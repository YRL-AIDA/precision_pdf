from __future__ import annotations

import argparse
import copy
import html
import json
from pathlib import Path
from typing import Any

import opendataloader_pdf

SCAN_LIKE_MAX_NATIVE_CHARS = 2000

## opendataloader-pdf[hybrid]" && opendataloader-pdf-hybrid --port 5002
def _normalize_odl_mode(raw: str | None) -> str:
    m = (raw or "heuristic").strip().lower().replace("_", "-")
    if m in ("merge", "both", "merge-tables", "mergetables"):
        return "merge-tables"
    if m in ("docling", "docling-fast", "hybrid"):
        return "docling-fast"
    return "heuristic"


def _run_odl_convert(pdf_path: Path, output_dir: Path, *, use_hybrid: bool) -> None:
    kwargs: dict[str, Any] = {
        "input_path": str(pdf_path),
        "output_dir": str(output_dir),
        "format": "json",
        "include_header_footer": True,
    }
    if use_hybrid:
        kwargs["hybrid"] = "docling-fast"
    opendataloader_pdf.convert(**kwargs)


def _bbox_iou(a: list[Any], b: list[Any]) -> float:
    try:
        ax1, ay1, ax2, ay2 = (float(x) for x in a[:4])
        bx1, by1, bx2, by2 = (float(x) for x in b[:4])
    except (TypeError, ValueError):
        return 0.0
    inter_x1 = max(ax1, bx1)
    inter_y1 = max(ay1, by1)
    inter_x2 = min(ax2, bx2)
    inter_y2 = min(ay2, by2)
    iw = max(0.0, inter_x2 - inter_x1)
    ih = max(0.0, inter_y2 - inter_y1)
    inter = iw * ih
    aa = max(0.0, ax2 - ax1) * max(0.0, ay2 - ay1)
    bb = max(0.0, bx2 - bx1) * max(0.0, by2 - by1)
    union = aa + bb - inter
    return inter / union if union > 0 else 0.0


def _table_page(node: dict[str, Any]) -> int:
    try:
        return int(node.get("page number") or 0)
    except (TypeError, ValueError):
        return 0


def _count_table_cells(tbl: dict[str, Any]) -> int:
    n = 0
    for row in tbl.get("rows") or []:
        if not isinstance(row, dict):
            continue
        for cell in row.get("cells") or []:
            if isinstance(cell, dict):
                n += 1
    return n


def _cell_text_from_kids(cell: dict[str, Any]) -> str:
    parts: list[str] = []
    for kid in cell.get("kids") or []:
        if isinstance(kid, dict) and kid.get("content") is not None:
            parts.append(str(kid.get("content", "")))
    return "".join(parts).strip()


def _count_empty_1x1_cells(tbl: dict[str, Any]) -> int:
    """Empty single-row/col cells — Docling often emits many after splitting merged headers."""
    n = 0
    for row in tbl.get("rows") or []:
        if not isinstance(row, dict):
            continue
        for cell in row.get("cells") or []:
            if not isinstance(cell, dict):
                continue
            rs = int(cell.get("row span") or 1)
            cs = int(cell.get("column span") or 1)
            if rs != 1 or cs != 1:
                continue
            if not _cell_text_from_kids(cell):
                n += 1
    return n


def _native_table_weak(tbl: dict[str, Any]) -> bool:
    rows = tbl.get("rows")
    if not isinstance(rows, list) or len(rows) == 0:
        return True
    return _count_table_cells(tbl) <= 1


def _estimate_document_text_chars(data: dict[str, Any], limit: int = 500_000) -> int:
    total = 0

    def walk(o: Any) -> None:
        nonlocal total
        if total >= limit:
            return
        if isinstance(o, str):
            total += len(o)
        elif isinstance(o, dict):
            c = o.get("content")
            if isinstance(c, str):
                total += len(c)
            for k, v in o.items():
                if k == "content":
                    continue
                walk(v)
        elif isinstance(o, list):
            for x in o:
                walk(x)

    walk(data.get("kids"))
    c0 = data.get("content")
    if isinstance(c0, str):
        total += len(c0)
    return total


def _collect_table_nodes(nodes: list[Any], acc: list[dict[str, Any]]) -> None:
    for node in nodes or []:
        if not isinstance(node, dict):
            continue
        t = str(node.get("type", "")).strip().lower()
        if t == "table":
            acc.append(node)
        _collect_table_nodes(node.get("kids") or [], acc)
        _collect_table_nodes(node.get("list items") or [], acc)


def _pick_hybrid_table_replacement(
    native_tbl: dict[str, Any],
    hybrid_tables: list[dict[str, Any]],
    *,
    scan_like: bool,
) -> dict[str, Any] | None:
    nb = native_tbl.get("bounding box")
    if not isinstance(nb, list) or len(nb) != 4:
        return None
    np_ = _table_page(native_tbl)
    native_cells = _count_table_cells(native_tbl)
    native_empty = _count_empty_1x1_cells(native_tbl)
    weak = _native_table_weak(native_tbl)
    best: dict[str, Any] | None = None
    best_iou = 0.0
    best_cells = -1
    for ht in hybrid_tables:
        hp = _table_page(ht)
        if np_ > 0 and hp > 0 and np_ != hp:
            continue
        hb = ht.get("bounding box")
        if not isinstance(hb, list) or len(hb) != 4:
            continue
        iou = _bbox_iou(nb, hb)
        hc = _count_table_cells(ht)
        if iou > best_iou or (abs(iou - best_iou) < 1e-9 and hc > best_cells):
            best = ht
            best_iou = iou
            best_cells = hc
    if best is None:
        return None
    iou_thr = 0.12 if scan_like else 0.22
    if best_iou < iou_thr:
        return None
    hc = best_cells
    hybrid_empty = _count_empty_1x1_cells(best)

    if weak:
        return best
    # Bordered + merged: Docling often emits more 1x1 empty cells — must run BEFORE scan_like (short
    # "table-only" PDFs used to hit scan_like first and wrongly preferred hybrid).
    if native_cells >= 4 and hc > native_cells * 1.06 and hybrid_empty >= native_empty:
        return None
    # Borderless: native explodes into many cells / empties; hybrid is more compact.
    if (
        native_cells >= 10
        and hc > 0
        and hc <= native_cells * 0.88
        and native_empty >= hybrid_empty + 4
    ):
        return best
    if scan_like and hc > native_cells and hybrid_empty <= native_empty + 2:
        return best
    if (
        not weak
        and native_cells >= 2
        and hc >= max(4, int(native_cells * 1.4))
        and hybrid_empty <= native_empty + 1
    ):
        return best
    return None


def _inject_orphan_hybrid_tables(merged: dict[str, Any], hybrid_tables: list[dict[str, Any]]) -> None:
    """When native has no table (or no overlap), insert Docling tables that were never matched."""
    kids = merged.get("kids")
    if not isinstance(kids, list):
        return

    for ht in hybrid_tables:
        hb = ht.get("bounding box")
        if not isinstance(hb, list) or len(hb) != 4:
            continue
        tbl_cy = (float(hb[1]) + float(hb[3])) / 2

        merged_tables: list[dict[str, Any]] = []
        _collect_table_nodes(kids, merged_tables)
        max_iou = 0.0
        for mt in merged_tables:
            mb = mt.get("bounding box")
            if isinstance(mb, list) and len(mb) == 4:
                max_iou = max(max_iou, _bbox_iou(hb, mb))
        if max_iou >= 0.28:
            continue

        insert_at = 0
        for i, node in enumerate(kids):
            if not isinstance(node, dict):
                continue
            bb = node.get("bounding box")
            if isinstance(bb, list) and len(bb) >= 4:
                cy = (float(bb[1]) + float(bb[3])) / 2
                if cy > tbl_cy:
                    insert_at = i + 1
        kids.insert(insert_at, copy.deepcopy(ht))


def _merge_walk_nodes(nodes: list[Any], hybrid_tables: list[dict[str, Any]], scan_like: bool) -> None:
    if not isinstance(nodes, list):
        return
    for i, node in enumerate(nodes):
        if not isinstance(node, dict):
            continue
        t = str(node.get("type", "")).strip().lower()
        if t == "table":
            repl = _pick_hybrid_table_replacement(node, hybrid_tables, scan_like=scan_like)
            if repl is not None:
                nodes[i] = copy.deepcopy(repl)
            else:
                _merge_walk_nodes(node.get("kids") or [], hybrid_tables, scan_like)
                _merge_walk_nodes(node.get("list items") or [], hybrid_tables, scan_like)
        else:
            _merge_walk_nodes(node.get("kids") or [], hybrid_tables, scan_like)
            _merge_walk_nodes(node.get("list items") or [], hybrid_tables, scan_like)


def _merge_native_json_with_hybrid_tables(native: dict[str, Any], hybrid: dict[str, Any]) -> dict[str, Any]:
    merged = copy.deepcopy(native)
    hybrid_tables: list[dict[str, Any]] = []
    _collect_table_nodes(hybrid.get("kids") or [], hybrid_tables)
    scan_like = _estimate_document_text_chars(native) < SCAN_LIKE_MAX_NATIVE_CHARS
    _merge_walk_nodes(merged.get("kids") or [], hybrid_tables, scan_like)
    _inject_orphan_hybrid_tables(merged, hybrid_tables)
    return merged


def parse_pdf_to_json(pdf_path: Path, output_dir: Path, odl_mode: str | None = None) -> Path:
    """Parse a PDF with OpenDataloader and return generated JSON path."""
    output_dir.mkdir(parents=True, exist_ok=True)
    mode = _normalize_odl_mode(odl_mode)
    stem = pdf_path.stem
    final_json = output_dir / f"{stem}.json"

    if mode == "merge-tables":
        _run_odl_convert(pdf_path, output_dir, use_hybrid=False)
        native_tmp = output_dir / f"{stem}.json"
        native_saved = output_dir / f"{stem}_native.json"
        if not native_tmp.exists():
            raise FileNotFoundError(f"Native ODL JSON not found after convert: {native_tmp}")
        native_tmp.rename(native_saved)

        _run_odl_convert(pdf_path, output_dir, use_hybrid=True)
        hybrid_tmp = output_dir / f"{stem}.json"
        hybrid_saved = output_dir / f"{stem}_docling.json"
        if not hybrid_tmp.exists():
            raise FileNotFoundError(f"Hybrid ODL JSON not found after convert: {hybrid_tmp}")
        hybrid_tmp.rename(hybrid_saved)

        native_root = json.loads(native_saved.read_text(encoding="utf-8"))
        hybrid_root = json.loads(hybrid_saved.read_text(encoding="utf-8"))
        merged = _merge_native_json_with_hybrid_tables(native_root, hybrid_root)
        final_json.write_text(json.dumps(merged, ensure_ascii=False), encoding="utf-8")
        native_saved.unlink(missing_ok=True)
        hybrid_saved.unlink(missing_ok=True)
    else:
        _run_odl_convert(pdf_path, output_dir, use_hybrid=(mode == "docling-fast"))

    if not final_json.exists():
        raise FileNotFoundError(f"Parser finished but JSON output was not found: {final_json}")
    return final_json


def _collect_segments(nodes: list[dict[str, Any]], into: list[dict[str, Any]]) -> None:
    for node in nodes:
        bbox = node.get("bounding box")
        if isinstance(bbox, list) and len(bbox) == 4:
            into.append(
                {
                    "id": node.get("id"),
                    "type": node.get("type", "unknown"),
                    "page": node.get("page number"),
                    "bbox": bbox,
                    "content": node.get("content", ""),
                }
            )

        kids = node.get("kids")
        if isinstance(kids, list):
            _collect_segments(kids, into)

        list_items = node.get("list items")
        if isinstance(list_items, list):
            _collect_segments(list_items, into)


def load_segments(json_path: Path, page: int | None) -> tuple[list[dict[str, Any]], str]:
    data = json.loads(json_path.read_text(encoding="utf-8"))
    file_name = str(data.get("file name", json_path.name))
    root_nodes = data.get("kids", [])
    if not isinstance(root_nodes, list):
        raise ValueError("Unsupported JSON format: expected top-level 'kids' list.")

    segments: list[dict[str, Any]] = []
    _collect_segments(root_nodes, segments)
    if page is not None:
        segments = [segment for segment in segments if segment.get("page") == page]
    return segments, file_name


def render_segments_html(
    segments: list[dict[str, Any]], output_html: Path, source_name: str
) -> None:
    if not segments:
        raise ValueError("No segments found to visualize.")

    max_x = max(float(segment["bbox"][2]) for segment in segments)
    max_y = max(float(segment["bbox"][3]) for segment in segments)
    width = max_x + 20.0
    height = max_y + 20.0

    colors = {
        "paragraph": "#4f8ef7",
        "list": "#34a853",
        "list item": "#9c27b0",
    }

    box_html: list[str] = []
    for idx, segment in enumerate(segments, start=1):
        x1, y1, x2, y2 = (float(value) for value in segment["bbox"])
        segment_width = max(x2 - x1, 1.0)
        segment_height = max(y2 - y1, 1.0)
        # ODL bounding boxes use a PDF-like coordinate system (origin at bottom-left).
        top = height - y2

        segment_type = str(segment.get("type", "unknown"))
        color = colors.get(segment_type, "#f57c00")
        content = str(segment.get("content", "")).strip().replace("\n", " ")
        safe_content = html.escape(content[:240] + ("..." if len(content) > 240 else ""))
        label_id = segment.get("id", idx)
        page_number = segment.get("page")

        box_html.append(
            f"""
            <div class="segment"
                 style="left:{x1:.2f}px;top:{top:.2f}px;width:{segment_width:.2f}px;height:{segment_height:.2f}px;border-color:{color};">
              <div class="label">{html.escape(str(label_id))}</div>
              <div class="tooltip">
                <strong>{html.escape(segment_type)}</strong><br/>
                page: {html.escape(str(page_number))}<br/>
                bbox: [{x1:.2f}, {y1:.2f}, {x2:.2f}, {y2:.2f}]<br/>
                text: {safe_content or "(empty)"}
              </div>
            </div>
            """
        )

    html_text = f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <title>ODL Segment Viewer - {html.escape(source_name)}</title>
  <style>
    body {{
      font-family: Arial, sans-serif;
      margin: 20px;
      color: #1f2937;
      background: #fafafa;
    }}
    .meta {{
      margin-bottom: 12px;
      font-size: 14px;
    }}
    .canvas {{
      position: relative;
      width: {width:.2f}px;
      height: {height:.2f}px;
      border: 1px solid #d1d5db;
      background: white;
      box-shadow: 0 2px 10px rgba(0, 0, 0, 0.06);
    }}
    .segment {{
      position: absolute;
      border: 1px solid;
      background: rgba(79, 142, 247, 0.06);
      box-sizing: border-box;
    }}
    .segment:hover {{
      z-index: 5;
      background: rgba(245, 124, 0, 0.10);
      border-width: 2px;
    }}
    .label {{
      position: absolute;
      top: -16px;
      left: 0;
      font-size: 10px;
      line-height: 12px;
      color: #374151;
      background: #ffffff;
      border: 1px solid #d1d5db;
      padding: 0 2px;
      border-radius: 3px;
    }}
    .tooltip {{
      display: none;
      position: absolute;
      left: 0;
      top: 100%;
      margin-top: 4px;
      min-width: 260px;
      max-width: 460px;
      font-size: 12px;
      line-height: 1.4;
      color: #111827;
      background: #fff;
      border: 1px solid #d1d5db;
      padding: 8px;
      border-radius: 6px;
      box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
    }}
    .segment:hover .tooltip {{
      display: block;
    }}
  </style>
</head>
<body>
  <div class="meta">
    <strong>Source:</strong> {html.escape(source_name)}<br/>
    <strong>Segments:</strong> {len(segments)}
  </div>
  <div class="canvas">
    {''.join(box_html)}
  </div>
</body>
</html>
"""
    output_html.write_text(html_text, encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Parse PDF with OpenDataloader and visualize extracted segments."
    )
    parser.add_argument(
        "--pdf",
        type=Path,
        help="Path to input PDF to parse. If omitted, use --json only.",
    )
    parser.add_argument(
        "--json",
        type=Path,
        help="Path to existing JSON output. If --pdf is set, this is optional.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("output"),
        help="Directory for parser outputs (used with --pdf).",
    )
    parser.add_argument(
        "--html",
        type=Path,
        help="Path for generated visualization HTML.",
    )
    parser.add_argument(
        "--page",
        type=int,
        default=1,
        help="Page number to visualize (default: 1). Use 0 to include all pages.",
    )
    parser.add_argument(
        "--odl-mode",
        type=str,
        default="heuristic",
        help="OpenDataloader run: heuristic (no hybrid), docling-fast, or merge-tables (native JSON + Docling tables when native is weak / scan-like).",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    json_path = args.json
    if args.pdf is not None:
        pdf_path = args.pdf.resolve()
        if not pdf_path.exists():
            raise FileNotFoundError(f"PDF does not exist: {pdf_path}")
        json_path = parse_pdf_to_json(pdf_path, args.output_dir.resolve(), args.odl_mode)

    if json_path is None:
        raise ValueError("Provide --pdf to parse, or --json to visualize an existing file.")

    json_path = json_path.resolve()
    if not json_path.exists():
        raise FileNotFoundError(f"JSON does not exist: {json_path}")

    page = None if args.page == 0 else args.page
    segments, source_name = load_segments(json_path, page=page)
    output_html = args.html.resolve() if args.html else json_path.with_name(f"{json_path.stem}_segments.html")
    render_segments_html(segments, output_html, source_name=source_name)

    print(f"JSON source: {json_path}")
    print(f"Segments visualized: {len(segments)}")
    print(f"HTML written to: {output_html}")


if __name__ == "__main__":
    main()
