from __future__ import annotations

import argparse
import html
import json
from pathlib import Path
from typing import Any

import opendataloader_pdf


def parse_pdf_to_json(pdf_path: Path, output_dir: Path) -> Path:
    """Parse a PDF with OpenDataloader and return generated JSON path."""
    output_dir.mkdir(parents=True, exist_ok=True)

    opendataloader_pdf.convert(
        input_path=str(pdf_path),
        output_dir=str(output_dir),
        format="json",
        hybrid="docling-fast",
        include_header_footer=True
    )

    json_path = output_dir / f"{pdf_path.stem}.json"
    if not json_path.exists():
        raise FileNotFoundError(
            f"Parser finished but JSON output was not found: {json_path}"
        )
    return json_path


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
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    json_path = args.json
    if args.pdf is not None:
        pdf_path = args.pdf.resolve()
        if not pdf_path.exists():
            raise FileNotFoundError(f"PDF does not exist: {pdf_path}")
        json_path = parse_pdf_to_json(pdf_path, args.output_dir.resolve())

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
