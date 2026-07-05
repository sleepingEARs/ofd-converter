# OFD Converter — PoC Findings (2026-07-05)

PoC executed as Plan 1, Section A (Tasks 3–6). All PoC tests pass. Findings below gate Plan 2 (OFD→DOCX, OFD→Markdown, DOCX→OFD production converters) and confirm Plan 1's native converters (Tasks 16–20) are safe to build.

## Native conversions (spec §5 🟢 — Task 3)

All 5 native ofdrw 2.3.9 conversions work against the fixture OFD/PDF/PNG.

| Direction | Result | ofdrw API used |
|---|---|---|
| OFD → PDF | PASS | `PDFExporterPDFBox(ofd, out).export()` |
| OFD → PNG | PASS | `ImageExporter(ofd, dir, "PNG", ppm).export()` → `getImgFilePaths()` |
| OFD → TXT | PASS | `TextExporter(ofd, out).export()` |
| PDF → OFD | PASS | `PDFConverter(out).convert(pdf)` |
| PNG → OFD | PASS | `ImageConverter(out).convert(png)` |

**Conclusion:** Tasks 16–20 (production converters) are unblocked. No spec change. One follow-up: the OFD→PNG PoC assertion only checks the file list is non-empty — the Task 17 production test should also assert PNG magic bytes (`89 50 4E 47`).

## OFD → DOCX (Task 4)

- **Verified ofdrw-reader 2.3.9 API:** `OFDReader(Path)` → `getPageList()` → `PageInfo.getObj()` → `Page.getContent().getLayers()` → `CT_Layer.getPageBlocks()` → filter `TextObject` → `getTextCodes()` → `TextCode.getContent()`.
- **Gotcha:** `OFDReader.getPage(int)` is **1-based** (passing 0 throws). Use `getPageList()` to avoid index errors.
- **Extraction quality:** raw text only — no paragraph/heading/table structure. `TextObject` exposes `getSize()` (font size) and `getFont()` (ST_RefID), so structure inference is possible but not free.
- **DOCX build:** POI `XWPFDocument` produces a valid ZIP DOCX.
- **Recommendation for Plan 2:** build OFD→DOCX on ofdrw-reader + POI. Use font-size heuristics for paragraph/heading detection. Expect lossy conversion (fixed-layout → flow). Mark as "experimental / lossy" in UI per spec §10.

## OFD → Markdown (Task 5)

- **Path B (HTMLExporter → flexmark): UNUSABLE.** HTMLExporter output is SVG-based (contains `<svg>`; flexmark produced only ~60 chars of Markdown). No semantic `<h1>`/`<table>` structure to convert.
- **Path A (ofdrw-reader): VIABLE.** Extracts text via the Task-4 API. `CT_Text.getSize()` exposes font size → heading-level inference by font size is feasible. `getFont()` available for font reference.
- **Path C (TextExporter → wrap as .md):** fallback only — no structure.
- **Decision:** adopt **Path A** for Plan 2 OFD→Markdown. Use font-size thresholds for `#`/`##`/`###` headings; fall back to plain paragraphs when inference is uncertain. Path C as degradation when A yields nothing.

## DOCX → OFD (Task 6)

- **Text-only conversion: PASS.** POI extracts paragraphs; `OFDDoc.add(new Paragraph(text))` builds a valid OFD. Note: ofdrw-layout 2.3.9 has `add(Div)`, not `addPage` (the brief's `addPage` was wrong).
- **Full-fidelity effort: HIGH.** Real DOCX→OFD needs a layout engine (pagination, font metrics, table rendering, image placement) — equivalent to a mini Word renderer.
- **Recommendation:** defer full DOCX→OFD to a later release. If shipped in Plan 2, mark as "text-only / experimental" and warn users. Consider whether to include at all in v1.

## Spec adjustments required

None for Plan 1 scope. Plan 2 (separate plan, future) will:
- Build OFD→DOCX (Path: ofdrw-reader + POI, lossy).
- Build OFD→Markdown (Path A: reader + font-size inference, Path C fallback).
- Decide DOCX→OFD scope (text-only experimental vs. defer).

The spec §5 matrix confidence levels (🟡/🔴) are confirmed accurate. No upgrades or downgrades needed.
