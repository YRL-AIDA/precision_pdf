from __future__ import annotations

import argparse
import ast
import json
import os
import re
import sys
import tempfile
import warnings
from html.parser import HTMLParser
from pathlib import Path
from typing import Any

# Noisy transformers/torch deprecations from upstream DeepSeek-OCR code (not actionable here).
warnings.filterwarnings("ignore", message=r".*seen_tokens.*deprecated.*")
warnings.filterwarnings("ignore", message=r".*get_max_cache\(\).*deprecated.*")
warnings.filterwarnings("ignore", message=r".*position_ids.*will be removed.*")
warnings.filterwarnings("ignore", message=r".*`do_sample` is set to `False`.*temperature.*")

try:
    import fitz
except ImportError:
    print("PyMuPDF (pymupdf) is required: pip install pymupdf", file=sys.stderr)
    raise

# Same shape as upstream re_match() in modeling_deepseekocr.py (no space between </ref|> and <|det|>).
REF_DET_PATTERN = re.compile(
    r"<\|ref\|>(.*?)<\|/ref\|><\|det\|>(.*?)<\|/det\|>",
    re.DOTALL,
)


def _log(msg: str) -> None:
    print(f"[deepseek_ocr_parser] {msg}", file=sys.stderr, flush=True)


def _env_int(name: str, default: int) -> int:
    raw = os.environ.get(name, "").strip()
    if not raw:
        return default
    try:
        return int(raw)
    except ValueError:
        return default


def _render_dpi_int(render_dpi: float) -> int:
    """PyMuPDF pixmap DPI must be int (see fz_pixmap_xres_set)."""
    return max(1, int(round(float(render_dpi))))


def _gnn_label_from_ref_type(ref_type: str) -> str:
    """
    DeepSeek grounding puts the *layout class* inside <|ref|>…<|/ref|>, not the OCR text.
    OCR text follows the <|det|>…<|/det|> tag until the next grounding block.
    """
    t = (ref_type or "").strip().lower().replace(" ", "_")
    if t in ("table", "table_caption", "table_footnote", "table_title"):
        return "table"
    if t in (
        "title",
        "figure_title",
        "section",
        "section_title",
        "heading",
        "header",
        "caption",
        "figure_caption",
        "doc_title",
    ):
        return "header"
    if t in ("text", "paragraph", "list", "list_item", "footnote", "reference"):
        return "text"
    if t in ("figure", "image", "picture", "chart", "formula", "equation"):
        return "other"
    return "other"


def _norm_label_from_content(content: str, ref_type: str) -> str:
    """Refine label using OCR body when ref type is generic 'text'."""
    base = _gnn_label_from_ref_type(ref_type)
    if base != "text":
        return base
    t = (content or "").strip()
    if not t:
        return "text"
    first = t.split("\n", 1)[0].strip()
    if first.startswith("#"):
        return "header"
    pipe_lines = [ln for ln in t.splitlines() if "|" in ln]
    if len(pipe_lines) >= 2 and all(ln.count("|") >= 2 for ln in pipe_lines[:3]):
        return "table"
    return "text"


def _parse_det_box(det: str) -> tuple[float, float, float, float] | None:
    """Parse <|det|>[[x1,y1,x2,y2]]<|/det|> or list literals from model output."""
    raw = (det or "").strip()
    if not raw:
        return None
    if raw.startswith("[[") and raw.endswith("]]"):
        raw = raw[2:-2]
    elif raw.startswith("[") and raw.endswith("]"):
        raw = raw[1:-1]
    parts = [p.strip() for p in raw.replace(" ", "").split(",")]
    if len(parts) != 4:
        return None
    try:
        return float(parts[0]), float(parts[1]), float(parts[2]), float(parts[3])
    except ValueError:
        return None


def _norm_box_to_pdf_points(
    x1n: float,
    y1n: float,
    x2n: float,
    y2n: float,
    page_w_pt: float,
    page_h_pt: float,
    img_w: float,
    img_h: float,
) -> tuple[float, float, float, float] | None:
    denom = 1000.0
    x1i = x1n / denom * img_w
    y1i = y1n / denom * img_h
    x2i = x2n / denom * img_w
    y2i = y2n / denom * img_h
    sx = page_w_pt / img_w if img_w else 1.0
    sy = page_h_pt / img_h if img_h else 1.0
    x_tl = min(x1i, x2i) * sx
    y_tl = min(y1i, y2i) * sy
    x_br = max(x1i, x2i) * sx
    y_br = max(y1i, y2i) * sy
    if x_br <= x_tl or y_br <= y_tl:
        return None
    return x_tl, y_tl, x_br, y_br


def _det_to_pdf_points(
    det: str, page_w_pt: float, page_h_pt: float, img_w: float, img_h: float
) -> tuple[float, float, float, float] | None:
    parsed = _parse_det_box(det)
    if parsed is None:
        return None
    return _norm_box_to_pdf_points(*parsed, page_w_pt, page_h_pt, img_w, img_h)


def _parse_det_boxes_normalized(det: str) -> list[tuple[float, float, float, float]]:
    """Det field may be [[x1,y1,x2,y2]] or a list of such boxes (upstream eval)."""
    raw = (det or "").strip()
    if not raw:
        return []
    try:
        data = ast.literal_eval(raw)
    except (SyntaxError, ValueError):
        one = _parse_det_box(raw)
        return [one] if one else []

    if isinstance(data, (int, float)):
        return []
    if isinstance(data, list) and len(data) == 4 and all(isinstance(x, (int, float)) for x in data):
        return [(float(data[0]), float(data[1]), float(data[2]), float(data[3]))]
    boxes: list[tuple[float, float, float, float]] = []
    if isinstance(data, list):
        for item in data:
            if isinstance(item, list) and len(item) >= 4:
                try:
                    boxes.append(
                        (float(item[0]), float(item[1]), float(item[2]), float(item[3]))
                    )
                except (TypeError, ValueError):
                    continue
    return boxes


_HTML_TABLE_RE = re.compile(r"<table\b[\s\S]*?</table>", re.IGNORECASE)


def _parse_html_table_rows(raw: str) -> list[list[str]]:
    """Extract grid from DeepSeek markdown/HTML table (often one <table>…</table> blob)."""
    m = _HTML_TABLE_RE.search(raw or "")
    fragment = m.group(0) if m else (raw or "")
    rows_out: list[list[str]] = []
    current_row: list[str] = []
    cell_parts: list[str] = []

    class _TableParser(HTMLParser):
        def handle_starttag(self, tag: str, attrs: list) -> None:
            t = tag.lower()
            if t == "tr":
                nonlocal current_row
                current_row = []
            elif t in ("td", "th"):
                nonlocal cell_parts
                cell_parts = []

        def handle_endtag(self, tag: str) -> None:
            t = tag.lower()
            if t in ("td", "th"):
                current_row.append(" ".join(cell_parts).strip())
                cell_parts.clear()
            elif t == "tr" and current_row:
                rows_out.append(current_row)

        def handle_data(self, data: str) -> None:
            if data:
                cell_parts.append(data)

    parser = _TableParser()
    try:
        parser.feed(fragment)
        parser.close()
    except Exception:
        return []
    return [r for r in rows_out if any(c.strip() for c in r)]


def _html_table_to_markdown(raw: str) -> str:
    rows = _parse_html_table_rows(raw)
    if not rows:
        return raw
    width = max(len(r) for r in rows)
    lines: list[str] = []
    for i, row in enumerate(rows):
        padded = row + [""] * (width - len(row))
        lines.append("| " + " | ".join(c.replace("|", "\\|") for c in padded) + " |")
        if i == 0:
            lines.append("| " + " | ".join("---" for _ in padded) + " |")
    return "\n".join(lines)


def _normalize_table_region_content(content: str) -> str:
    c = (content or "").strip()
    if not c:
        return c
    lower = c.lower()
    if "<table" in lower:
        md = _html_table_to_markdown(c)
        if md and "|" in md:
            return md
    return c


def _markdown_row_cells(line: str) -> list[str] | None:
    s = line.strip()
    if not s.startswith("|") or s.count("|") < 2:
        return None
    parts = [p.strip() for p in s.strip("|").split("|")]
    if not parts:
        return None
    if all(re.fullmatch(r"[\s\-:]+", p) or not p for p in parts):
        return None
    return parts


def _rows_for_region_text(text: str, seg: dict[str, float]) -> list[dict[str, Any]]:
    """One GNN row per markdown line; pipe cells become separate words for Java grid layout."""
    x_tl = float(seg["x_top_left"])
    y_tl = float(seg["y_top_left"])
    x_br = float(seg["x_bottom_right"])
    y_br = float(seg["y_bottom_right"])
    width = max(x_br - x_tl, 1.0)
    height = max(y_br - y_tl, 1.0)
    entries: list[dict[str, Any]] = []
    data_lines = [
        ln.strip()
        for ln in text.splitlines()
        if ln.strip() and not re.match(r"^\|[\s\-:|]+\|$", ln.strip())
    ]
    row_count = max(len(data_lines), 1)
    row_h = height / row_count
    for row_idx, line in enumerate(data_lines):
        cells = _markdown_row_cells(line)
        y0 = y_tl + row_idx * row_h
        y1 = y_tl + (row_idx + 1) * row_h
        row_seg = {
            "x_top_left": x_tl,
            "y_top_left": y0,
            "x_bottom_right": x_br,
            "y_bottom_right": y1,
        }
        words: list[dict[str, Any]] = []
        if cells:
            col_w = width / max(len(cells), 1)
            for col_idx, cell_text in enumerate(cells):
                cx0 = x_tl + col_idx * col_w
                cx1 = x_tl + (col_idx + 1) * col_w
                word_seg = {
                    "x_top_left": cx0,
                    "y_top_left": y0,
                    "x_bottom_right": cx1,
                    "y_bottom_right": y1,
                }
                words.append(
                    {
                        "text": cell_text,
                        "segment": word_seg,
                        "font": {"name": "deepseek-ocr-hf", "size": 10.0},
                    }
                )
        else:
            words.append(
                {
                    "text": line,
                    "segment": dict(row_seg),
                    "font": {"name": "deepseek-ocr-hf", "size": 10.0},
                }
            )
        entries.append({"text": line, "segment": row_seg, "words": words})
    return entries


def _union_pdf_boxes(
    boxes: list[tuple[float, float, float, float]],
) -> tuple[float, float, float, float] | None:
    if not boxes:
        return None
    x_tl = min(b[0] for b in boxes)
    y_tl = min(b[1] for b in boxes)
    x_br = max(b[2] for b in boxes)
    y_br = max(b[3] for b in boxes)
    if x_br <= x_tl or y_br <= y_tl:
        return None
    return x_tl, y_tl, x_br, y_br


def _region_from_grounding_block(
    ref_type: str,
    content: str,
    corners: tuple[float, float, float, float],
) -> dict[str, Any]:
    x_tl, y_tl, x_br, y_br = corners
    body = (content or "").strip()
    if _gnn_label_from_ref_type(ref_type) == "table" or (
        ref_type.lower() == "table" and body
    ):
        body = _normalize_table_region_content(body)
    label = _norm_label_from_content(body, ref_type)
    seg = {
        "x_top_left": x_tl,
        "y_top_left": y_tl,
        "x_bottom_right": x_br,
        "y_bottom_right": y_br,
    }
    display = body if body else ref_type
    rows_payload: list[dict[str, Any]] = []
    if label == "table" and display:
        rows_payload = _rows_for_region_text(display, seg)
    if not rows_payload:
        rows_payload = [
            {
                "text": display,
                "segment": dict(seg),
                "words": [
                    {
                        "text": display,
                        "segment": dict(seg),
                        "font": {"name": "deepseek-ocr-hf", "size": max(8.0, (y_br - y_tl) * 0.8)},
                    }
                ],
            }
        ]
    region: dict[str, Any] = {
        "label": label,
        "segment": seg,
        "rows": rows_payload,
    }
    if label == "table" and "<table" in (content or "").lower():
        region["html_table"] = content.strip()
    return region


def _parse_grounding_markdown(
    md: str,
    page_w_pt: float,
    page_h_pt: float,
    img_w: float,
    img_h: float,
) -> list[dict[str, Any]]:
    text = md or ""
    matches = list(REF_DET_PATTERN.finditer(text))
    regions: list[dict[str, Any]] = []

    for i, m in enumerate(matches):
        ref_type = m.group(1).strip()
        if ref_type.lower() == "image":
            continue
        det = m.group(2).strip()

        content_end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        content = REF_DET_PATTERN.sub("", text[m.end() : content_end]).strip()

        norm_boxes = _parse_det_boxes_normalized(det)
        pdf_boxes: list[tuple[float, float, float, float]] = []
        for nb in norm_boxes:
            pt = _norm_box_to_pdf_points(nb[0], nb[1], nb[2], nb[3], page_w_pt, page_h_pt, img_w, img_h)
            if pt is not None:
                pdf_boxes.append(pt)

        if not pdf_boxes:
            single = _det_to_pdf_points(det, page_w_pt, page_h_pt, img_w, img_h)
            if single is not None:
                pdf_boxes.append(single)

        corners = _union_pdf_boxes(pdf_boxes)
        if corners is None:
            continue

        regions.append(_region_from_grounding_block(ref_type, content, corners))

    return regions


def _regions_from_pymupdf_blocks(page: fitz.Page) -> list[dict[str, Any]]:
    regions: list[dict[str, Any]] = []
    d = page.get_text("dict")
    for block in d.get("blocks", []):
        if block.get("type") != 0:
            continue
        bbox = block.get("bbox")
        if not bbox or len(bbox) != 4:
            continue
        x0, y0, x1, y1 = (float(bbox[0]), float(bbox[1]), float(bbox[2]), float(bbox[3]))
        if x1 <= x0 or y1 <= y0:
            continue
        lines: list[str] = []
        for line in block.get("lines", []):
            spans = line.get("spans", [])
            if not spans:
                continue
            lines.append("".join(s.get("text", "") for s in spans))
        text = "\n".join(lines).strip()
        if not text:
            continue
        label = _norm_label_from_text(text)
        seg = {
            "x_top_left": x0,
            "y_top_left": y0,
            "x_bottom_right": x1,
            "y_bottom_right": y1,
        }
        regions.append(
            {
                "label": label,
                "segment": dict(seg),
                "rows": [
                    {
                        "text": text,
                        "segment": dict(seg),
                        "words": [
                            {
                                "text": text,
                                "segment": dict(seg),
                                "font": {"name": "pymupdf-stub", "size": 10.0},
                            }
                        ],
                    }
                ],
            }
        )
    return regions


def _empty_fallback_region(page_w_pt: float, page_h_pt: float) -> list[dict[str, Any]]:
    return [
        {
            "label": "paragraph",
            "segment": {
                "x_top_left": 0.0,
                "y_top_left": 0.0,
                "x_bottom_right": page_w_pt,
                "y_bottom_right": page_h_pt,
            },
            "rows": [
                {
                    "text": "(no grounding boxes parsed)",
                    "segment": {
                        "x_top_left": 0.0,
                        "y_top_left": 0.0,
                        "x_bottom_right": page_w_pt,
                        "y_bottom_right": page_h_pt,
                    },
                    "words": [
                        {
                            "text": "(no grounding boxes parsed)",
                            "segment": {
                                "x_top_left": 0.0,
                                "y_top_left": 0.0,
                                "x_bottom_right": page_w_pt,
                                "y_bottom_right": page_h_pt,
                            },
                            "font": {"name": "deepseek-ocr-hf", "size": 10.0},
                        }
                    ],
                }
            ],
        }
    ]


def _resolve_hf_device(model: Any) -> tuple[str, Any]:
    """Pick device/dtype for model.infer (Universal-DeepSeek-OCR-2 accepts device=, dtype=)."""
    import torch

    forced = os.environ.get("DEEPSEEK_OCR_DEVICE", "").strip().lower()
    if forced in ("cpu", "cuda", "mps"):
        device = forced
    elif torch.cuda.is_available():
        device = "cuda"
    elif getattr(torch.backends, "mps", None) and torch.backends.mps.is_available():
        device = "mps"
    else:
        device = "cpu"

    if device == "cpu":
        # infer() uses torch.autocast(device, dtype=…); float32 disables autocast on CPU.
        dtype = torch.bfloat16 if getattr(torch, "bfloat16", None) else torch.float16
    elif device == "mps":
        dtype = torch.float16
    else:
        dtype = torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float16
    return device, dtype


def _effective_crop_mode(crop_mode: bool, device: str) -> bool:
    env = os.environ.get("DEEPSEEK_OCR_CROP_MODE", "").strip().lower()
    if env in ("0", "false", "no", "off"):
        return False
    if env in ("1", "true", "yes", "on"):
        return True
    return crop_mode


def _n_query_for_image_size(image_size: int, patch_size: int = 16, downsample_ratio: int = 4) -> int:
    import math

    n = math.ceil((image_size // patch_size) / downsample_ratio)
    return n * n


def _resolve_infer_sizes(
    base_size: int,
    image_size: int,
    crop_mode: bool,
    model_name: str,
) -> tuple[int, int, bool]:
    """
    Universal-DeepSeek-OCR-2 vision encoder only supports n_query 144 (768) or 256 (1024).
    Default image_size=640 → n_query=100 → ValueError.
    """
    universal = "universal" in model_name.lower() or "deepseek-ocr-2" in model_name.lower()

    if not crop_mode:
        if _n_query_for_image_size(image_size) not in (144, 256):
            fixed = 768 if image_size < 896 else 1024
            _log(
                f"image_size={image_size} gives unsupported n_query={_n_query_for_image_size(image_size)}; "
                f"using image_size={fixed} (need 768→144 or 1024→256 tokens)"
            )
            image_size = fixed
        if base_size not in (1024, 1280):
            base_size = 1024
        return base_size, image_size, False

    if universal or base_size == 1024:
        if base_size != 1024 or image_size != 768:
            _log("using Gundam layout base_size=1024 image_size=768 crop_mode=True")
        return 1024, 768, True

    return base_size, image_size, True


def _run_hf_infer_page(
    image_path: Path,
    page_w_pt: float,
    page_h_pt: float,
    tmp_dir: Path,
    model: Any,
    tokenizer: Any,
    *,
    base_size: int,
    image_size: int,
    crop_mode: bool,
    render_w_px: int,
    render_h_px: int,
) -> list[dict[str, Any]]:
    import inspect

    prompt = os.environ.get(
        "DEEPSEEK_OCR_PROMPT",
        "<image>\n<|grounding|>Convert the document to markdown.",
    )
    out_page = tmp_dir / f"dsocr_{image_path.stem}"
    out_page.mkdir(parents=True, exist_ok=True)

    device, dtype = _resolve_hf_device(model)
    use_crop = _effective_crop_mode(crop_mode, device)
    model_name = os.environ.get("DEEPSEEK_OCR_MODEL", "Dogacel/Universal-DeepSeek-OCR-2")
    base_size, image_size, use_crop = _resolve_infer_sizes(
        base_size, image_size, use_crop, model_name
    )

    infer_kwargs: dict[str, Any] = {
        "tokenizer": tokenizer,
        "prompt": prompt,
        "image_file": str(image_path),
        "output_path": str(out_page),
        "base_size": base_size,
        "image_size": image_size,
        "crop_mode": use_crop,
        # save_results=True writes result.mmd but infer() returns None and strips <|ref|> tags.
        "save_results": False,
        "test_compress": False,
        "eval_mode": True,
    }
    sig = inspect.signature(model.infer)
    if "device" in sig.parameters:
        infer_kwargs["device"] = device
    if "dtype" in sig.parameters:
        infer_kwargs["dtype"] = dtype

    res = model.infer(**infer_kwargs)
    md = res if isinstance(res, str) else ""
    if not md.strip():
        mmd = out_page / "result.mmd"
        if mmd.is_file():
            md = mmd.read_text(encoding="utf-8", errors="replace")
            _log(f"read infer output from {mmd} (infer returned empty)")

    if os.environ.get("DEEPSEEK_OCR_DEBUG", "").strip() in ("1", "true", "yes"):
        preview = (md or "")[:500].replace("\n", "\\n")
        _log(f"model output preview ({len(md or '')} chars): {preview!r}")

    # Map 0–1000 coords using actual page render size (not model internal base_size).
    img_w = float(max(render_w_px, 1))
    img_h = float(max(render_h_px, 1))
    regions = _parse_grounding_markdown(md, page_w_pt, page_h_pt, img_w, img_h)
    n_md = len(md or "")
    if not regions:
        if n_md == 0:
            _log(
                "HF infer returned empty text (check logs for 'Size mismatch' — "
                "vision tokens did not align; use crop_mode with base_size=1024 image_size=768)"
            )
        else:
            _log(
                f"HF returned {n_md} chars but no <|ref|>…<|det|> boxes — "
                "try DEEPSEEK_OCR_PROMPT='<image>\\n<|grounding|>OCR this image.' "
                f"preview: {(md or '')[:200]!r}"
            )
    return regions


def _patch_upstream_cuda_for_cpu() -> None:
    """Official DeepSeek-OCR infer() hardcodes .cuda(); redirect to CPU when needed."""
    import torch

    if torch.cuda.is_available():
        return
    if os.environ.get("DEEPSEEK_OCR_ALLOW_CUDA_PATCH", "1").strip() in ("0", "false", "no"):
        return

    _real_cuda = torch.Tensor.cuda

    def _tensor_cuda(self, device=None):
        if device is None or (isinstance(device, int)) or str(device).startswith("cuda"):
            return self.to("cpu")
        return self.to(device)

    torch.Tensor.cuda = _tensor_cuda  # type: ignore[method-assign]

    _orig_autocast = torch.autocast

    def _autocast_compat(device_type, *args, **kwargs):
        if device_type == "cuda" and not torch.cuda.is_available():
            kwargs = dict(kwargs)
            kwargs.setdefault("dtype", torch.float32)
            device_type = "cpu"
        return _orig_autocast(device_type, *args, **kwargs)

    torch.autocast = _autocast_compat  # type: ignore[assignment]
    _ = _real_cuda


def _load_deepseek_hf() -> tuple[Any, Any]:
    """HuggingFace DeepSeek-OCR; CUDA / MPS / CPU (set DEEPSEEK_OCR_DEVICE=cpu)."""
    try:
        from transformers.models.llama import modeling_llama

        if not hasattr(modeling_llama, "LlamaFlashAttention2"):
            class LlamaFlashAttention2(modeling_llama.LlamaAttention):
                pass

            modeling_llama.LlamaFlashAttention2 = LlamaFlashAttention2
    except ImportError:
        pass

    import torch
    from transformers import AutoModel, AutoTokenizer

    _patch_upstream_cuda_for_cpu()

    model_name = os.environ.get("DEEPSEEK_OCR_MODEL", "Dogacel/Universal-DeepSeek-OCR-2")
    tokenizer = AutoTokenizer.from_pretrained(model_name, trust_remote_code=True)

    kwargs: dict[str, Any] = {
        "trust_remote_code": True,
        "use_safetensors": True,
        "_attn_implementation": "eager",
    }

    device, dtype = _resolve_hf_device(None)
    _log(f"loading {model_name} on device={device} dtype={dtype}")

    model = AutoModel.from_pretrained(model_name, **kwargs).eval()
    if device == "cuda":
        model = model.cuda().to(dtype)
    elif device == "mps":
        model = model.to("mps").to(dtype)
    else:
        model = model.float()

    return model, tokenizer


def build_segmentation_json(
    pdf_path: Path,
    output_json: Path,
    *,
    mode: str,
    render_dpi: float,
    base_size: int,
    image_size: int,
    crop_mode: bool,
) -> None:
    doc = fitz.open(pdf_path)
    pages_out: list[dict[str, Any]] = []
    dpi_i = _render_dpi_int(render_dpi)

    requested_mode = (mode or "auto").strip().lower()
    # Legacy CLI/UI may still pass "vllm" — this script is HF-only now.
    if requested_mode == "vllm":
        _log('mode "vllm" is ignored; using HuggingFace (transformers) path instead.')
        requested_mode = "transformers"

    eff_mode = requested_mode
    if requested_mode == "auto":
        if os.environ.get("DEEPSEEK_OCR_FORCE_STUB") == "1":
            eff_mode = "stub"
        else:
            try:
                import torch  # noqa: F401
                from transformers import AutoModel  # noqa: F401

                eff_mode = "transformers"
            except ImportError as ex:
                raise RuntimeError(
                    "DeepSeek OCR requires torch+transformers (or set --mode stub / "
                    "DEEPSEEK_OCR_FORCE_STUB=1 for PyMuPDF-only)."
                ) from ex

    if eff_mode == "transformers":
        model: Any = None
        tokenizer: Any = None
        try:
            _log("loading DeepSeek-OCR (HuggingFace)…")
            model, tokenizer = _load_deepseek_hf()
            _log("model ready")
        except Exception as ex:
            raise RuntimeError(f"Failed to load DeepSeek-OCR model: {ex}") from ex

        if model is not None and tokenizer is not None:
            total_pages = doc.page_count
            max_pages = _env_int("DEEPSEEK_OCR_MAX_PAGES", 0)
            n_hf = total_pages if max_pages <= 0 else min(total_pages, max_pages)
            if n_hf < total_pages:
                _log(f"HF on first {n_hf}/{total_pages} pages (DEEPSEEK_OCR_MAX_PAGES={max_pages})")

            with tempfile.TemporaryDirectory(prefix="dsocr_img_") as img_root:
                img_root_p = Path(img_root)
                for i in range(n_hf):
                    page = doc.load_page(i)
                    rect = page.rect
                    page_w_pt = float(rect.width)
                    page_h_pt = float(rect.height)
                    _log(f"HF infer page {i + 1}/{n_hf} (dpi={dpi_i})…")
                    try:
                        pix = page.get_pixmap(dpi=dpi_i, alpha=False)
                        img_path = img_root_p / f"page_{i + 1}.png"
                        pix.save(str(img_path))
                        regions = _run_hf_infer_page(
                            img_path,
                            page_w_pt,
                            page_h_pt,
                            img_root_p,
                            model,
                            tokenizer,
                            base_size=base_size,
                            image_size=image_size,
                            crop_mode=crop_mode,
                            render_w_px=pix.width,
                            render_h_px=pix.height,
                        )
                    except Exception as ex:
                        raise RuntimeError(f"DeepSeek OCR failed on page {i + 1}: {ex}") from ex

                    if not regions:
                        raise RuntimeError(
                            f"DeepSeek OCR returned no grounding regions on page {i + 1}. "
                            "Set DEEPSEEK_OCR_DEBUG=1 and check model output."
                        )
                    _log(f"page {i + 1}: {len(regions)} OCR region(s)")
                    pages_out.append({"regions": regions})

                if n_hf < total_pages:
                    raise RuntimeError(
                        f"DEEPSEEK_OCR_MAX_PAGES={max_pages} limits OCR to {n_hf}/{total_pages} pages; "
                        "set DEEPSEEK_OCR_MAX_PAGES=0 to process all pages."
                    )

    if eff_mode == "stub":
        for i in range(doc.page_count):
            page = doc.load_page(i)
            pages_out.append({"regions": _regions_from_pymupdf_blocks(page)})

    doc.close()
    root = {"pages": pages_out}
    output_json.parent.mkdir(parents=True, exist_ok=True)
    output_json.write_text(json.dumps(root, ensure_ascii=False, indent=2), encoding="utf-8")
    _log(f"wrote {output_json}")


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="DeepSeek-OCR (HuggingFace only) → GNN-style JSON for precision_pdf"
    )
    p.add_argument("--pdf", type=Path, required=True)
    p.add_argument("--output-dir", type=Path, required=True)
    p.add_argument(
        "--mode",
        choices=("auto", "stub", "transformers"),
        default="auto",
        help="stub = PyMuPDF; transformers = HF AutoModel.infer; auto = HF if torch+transformers else stub",
    )
    p.add_argument("--render-dpi", type=float, default=144.0)
    p.add_argument("--base-size", type=int, default=1024)
    p.add_argument(
        "--image-size",
        type=int,
        default=768,
        help="Local tile size when crop_mode=True; if crop off, use 768 or 1024 only (Universal-OCR-2)",
    )
    p.add_argument(
        "--no-crop-mode",
        dest="crop_mode",
        action="store_false",
        help="Single view: image_size must be 768 or 1024 (not 640)",
    )
    p.set_defaults(crop_mode=True)
    return p.parse_args()


def main() -> None:
    args = parse_args()
    pdf_path = args.pdf.resolve()
    if not pdf_path.exists():
        print(f"PDF not found: {pdf_path}", file=sys.stderr)
        sys.exit(1)
    out_dir = args.output_dir.resolve()
    out_dir.mkdir(parents=True, exist_ok=True)
    out_json = out_dir / "segmentation.json"

    build_segmentation_json(
        pdf_path,
        out_json,
        mode=args.mode,
        render_dpi=args.render_dpi,
        base_size=args.base_size,
        image_size=args.image_size,
        crop_mode=args.crop_mode,
    )
    print(out_json)


if __name__ == "__main__":
    main()
