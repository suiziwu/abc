# -*- coding: utf-8 -*-
"""PDF 处理器：文本层优先，扫描件回退 OCR，支持书签结构与图片附件。"""

from __future__ import annotations

import os
from pathlib import Path

from ...errors import ErrorCode, ParserError, error_info
from ...registry import register_document_handler
from ...types import AttachmentInfo, DocumentMetadata, DocumentPage, Heading
from ..structure import headings_from_pages
from .base import BaseDocumentHandler, DocumentExtract

_OCR_ENGINE = None
_OCR_FAILED = False


def _ocr_engine():
    """懒加载 RapidOCR（可选依赖）。"""
    global _OCR_ENGINE, _OCR_FAILED
    if _OCR_ENGINE is not None or _OCR_FAILED:
        return _OCR_ENGINE
    try:
        from rapidocr_onnxruntime import RapidOCR

        config_path = os.environ.get("MEDIA_KIT_RAPIDOCR_CONFIG")
        _OCR_ENGINE = RapidOCR(config_path=config_path) if config_path else RapidOCR()
    except Exception:
        _OCR_FAILED = True
        _OCR_ENGINE = None
    return _OCR_ENGINE


def _ocr_page(page, dpi: int, score: float) -> str:
    engine = _ocr_engine()
    if engine is None:
        raise ParserError(ErrorCode.DOCUMENT_OCR_UNAVAILABLE, "未安装 rapidocr-onnxruntime", stage="ocr")
    import pymupdf

    pix = page.get_pixmap(dpi=dpi, colorspace=pymupdf.csGRAY)
    result, _ = engine(pix.tobytes("png"))
    if not result:
        return ""
    items = []
    for box, text, conf in result:
        if conf is not None and conf < score:
            continue
        ys = [p[1] for p in box]
        xs = [p[0] for p in box]
        items.append((min(ys), min(xs), text.strip()))
    items.sort(key=lambda t: (round(t[0] / 12), t[1]))
    return "\n".join(t[2] for t in items if t[2])


@register_document_handler(".pdf")
class PdfHandler(BaseDocumentHandler):
    name = "pdf"

    def extract(self, source: str, config, attachment_dir: str | None) -> DocumentExtract:
        try:
            import pymupdf
        except Exception as exc:  # pragma: no cover
            raise ParserError(
                ErrorCode.DEPENDENCY_MISSING, "缺少 PyMuPDF：pip install pymupdf", stage="dependency", cause=exc
            ) from exc

        try:
            doc = pymupdf.open(source)
        except Exception as exc:
            raise ParserError(
                ErrorCode.DOCUMENT_TEXT_EXTRACT_FAILED, "PDF 无法打开或已损坏", stage="extract", cause=exc
            ) from exc

        warnings = []
        pages: list[DocumentPage] = []
        ocr_count = 0

        try:
            for index in range(doc.page_count):
                page = doc[index]
                text = (page.get_text("text", sort=True) or "").strip()
                used_ocr = False
                if not text and config.pdf.ocr_fallback:
                    if config.pdf.ocr_max_pages and ocr_count >= config.pdf.ocr_max_pages:
                        pass
                    else:
                        try:
                            text = _ocr_page(page, config.pdf.ocr_dpi, config.pdf.ocr_text_score)
                            used_ocr = True
                            ocr_count += 1
                        except ParserError as exc:
                            warnings.append(exc.info)
                        except Exception as exc:
                            warnings.append(
                                error_info(ErrorCode.DOCUMENT_OCR_FAILED, f"第 {index + 1} 页 OCR 失败", stage="ocr", cause=exc)
                            )
                pages.append(DocumentPage(index=index + 1, text=text, ocr=used_ocr))

            if pages and not any(p.text.strip() for p in pages):
                warnings.append(
                    error_info(
                        ErrorCode.DOCUMENT_NO_TEXT_LAYER,
                        f"{len(pages)} 页均未提取到文本；如为扫描件请开启 pdf.ocr_fallback",
                        stage="extract",
                    )
                )

            metadata = DocumentMetadata(
                path=str(source),
                format=Path(source).suffix.lower(),
                size_bytes=os.path.getsize(source),
                page_count=doc.page_count,
                title=(doc.metadata or {}).get("title", "") or "",
                author=(doc.metadata or {}).get("author", "") or "",
                producer=(doc.metadata or {}).get("producer", "") or "",
                extra={"ocr_pages": ocr_count},
            )

            structure = self._structure(doc, pages, config)
            attachments = self._attachments(doc, attachment_dir, config) if attachment_dir else []
        finally:
            doc.close()

        return DocumentExtract(
            metadata=metadata,
            pages=pages,
            structure=structure,
            attachments=attachments,
            warnings=warnings,
        )

    # ------------------------------------------------------------------
    @staticmethod
    def _structure(doc, pages, config) -> list[Heading]:
        if not config.structure.enabled:
            return []
        try:
            raw = doc.get_toc(simple=True) or []
        except Exception:
            raw = []
        valid = [
            (int(level), str(title).strip(), int(page))
            for level, title, page in raw
            if 1 <= int(level) <= config.structure.max_level and title and len(str(title).strip()) >= 2
        ]
        # 书签有效条目足够多才采用（有些扫描件只有“1、2、3”这类无效书签）
        if len(valid) >= 3:
            return [Heading(level=level, title=title, page=page) for level, title, page in valid]
        return headings_from_pages(pages, config.structure)

    # ------------------------------------------------------------------
    @staticmethod
    def _attachments(doc, attachment_dir: str, config) -> list[AttachmentInfo]:
        result: list[AttachmentInfo] = []
        seen: set[int] = set()
        for page_index in range(doc.page_count):
            page = doc[page_index]
            for image in page.get_images(full=True):
                xref = image[0]
                if xref in seen:
                    continue
                seen.add(xref)
                try:
                    info = doc.extract_image(xref)
                except Exception:
                    continue
                data = info.get("image") or b""
                if len(data) < config.attachments.min_size_bytes:
                    continue
                ext = info.get("ext", "bin")
                name = f"pdf_p{page_index + 1}_{xref}.{ext}"
                path = os.path.join(attachment_dir, name)
                with open(path, "wb") as fp:
                    fp.write(data)
                result.append(
                    AttachmentInfo(
                        name=name,
                        path=path,
                        content_type=f"image/{ext}",
                        source=f"pdf:page{page_index + 1}",
                        size_bytes=len(data),
                    )
                )
        return result
