# -*- coding: utf-8 -*-
"""文档解析组件测试：PDF/扫描件 OCR、DOCX、PPTX、XLSX、CSV、Markdown/TXT/HTML、
附件提取、错误码与插件扩展。"""

from __future__ import annotations

import json
import os
import tempfile
import unittest
from importlib.util import find_spec

from media_kit import DocumentConfig, ErrorCode, ParserError, parse_document, register_document_handler
from media_kit.document.handlers.base import BaseDocumentHandler, DocumentExtract
from media_kit.tests import fixtures
from media_kit.types import DocumentMetadata, DocumentPage

RAPIDOCR = find_spec("rapidocr_onnxruntime") is not None


class DocumentParserTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.tmp = tempfile.mkdtemp(prefix="mk_doc_test_")
        cls.pdf = fixtures.make_pdf(os.path.join(cls.tmp, "sample.pdf"))
        cls.pdf_toc = fixtures.make_pdf(os.path.join(cls.tmp, "sample_toc.pdf"), with_toc=True)
        cls.scanned = fixtures.make_scanned_pdf(os.path.join(cls.tmp, "scanned.pdf"))
        cls.docx = fixtures.make_docx(os.path.join(cls.tmp, "sample.docx"))
        cls.pptx = fixtures.make_pptx(os.path.join(cls.tmp, "sample.pptx"))
        cls.xlsx = fixtures.make_xlsx(os.path.join(cls.tmp, "sample.xlsx"))
        cls.csv = fixtures.make_csv(os.path.join(cls.tmp, "sample.csv"))
        cls.md = fixtures.make_md(os.path.join(cls.tmp, "sample.md"))
        cls.txt = fixtures.make_txt(os.path.join(cls.tmp, "sample.txt"))
        cls.html = fixtures.make_html(os.path.join(cls.tmp, "sample.html"))

    @classmethod
    def tearDownClass(cls) -> None:
        fixtures.cleanup(cls.tmp)

    # ------------------------------------------------------------------
    @unittest.skipUnless(fixtures.FONT, "需要中文字体生成 PDF 夹具")
    def test_pdf_text_and_structure(self) -> None:
        result = parse_document(self.pdf, config={"output_dir": os.path.join(self.tmp, "out_pdf")})
        self.assertTrue(result.ok, result.error)
        self.assertEqual(result.metadata.page_count, 2)
        self.assertEqual(len(result.pages), 2)
        self.assertIn("计算机网络", result.text)
        levels = {h.level for h in result.structure}
        self.assertIn(1, levels)
        self.assertIn(2, levels)
        titles = " ".join(h.title for h in result.structure)
        self.assertIn("第1章", titles)

    def test_pdf_bookmark_structure(self) -> None:
        result = parse_document(self.pdf_toc)
        self.assertTrue(result.ok, result.error)
        self.assertEqual(len(result.structure), 3)
        self.assertEqual(result.structure[0].title, "第1章 计算机网络")

    @unittest.skipUnless(RAPIDOCR and fixtures.FONT, "需要 rapidocr 与中文字体")
    def test_scanned_pdf_ocr(self) -> None:
        result = parse_document(
            self.scanned,
            config={"pdf": {"ocr_fallback": True, "ocr_dpi": 120}, "output_dir": os.path.join(self.tmp, "out_ocr")},
        )
        self.assertTrue(result.ok, result.error)
        self.assertTrue(result.pages[0].ocr)
        self.assertGreater(len(result.pages[0].text.strip()), 5)

    def test_scanned_pdf_without_ocr_warns(self) -> None:
        result = parse_document(self.scanned, config={"pdf": {"ocr_fallback": False}})
        self.assertTrue(result.ok, result.error)
        self.assertTrue(any(w.code == ErrorCode.DOCUMENT_NO_TEXT_LAYER for w in result.warnings))

    def test_docx_text_structure_and_attachments(self) -> None:
        result = parse_document(
            self.docx,
            config={
                "output_dir": os.path.join(self.tmp, "out_docx"),
                "attachments": {"enabled": True},
            },
        )
        self.assertTrue(result.ok, result.error)
        self.assertIn("协议是通信双方必须遵守的规则", result.text)
        self.assertIn("运输层 | 端到端传输", result.text)
        self.assertEqual([(h.level, h.title) for h in result.structure][:2], [(1, "计算机网络"), (2, "网络协议")])
        self.assertGreaterEqual(len(result.attachments), 1)
        self.assertTrue(os.path.exists(result.attachments[0].path))

    def test_pptx(self) -> None:
        result = parse_document(self.pptx)
        self.assertTrue(result.ok, result.error)
        self.assertEqual(result.metadata.page_count, 2)
        self.assertIn("网络协议", result.text)
        self.assertEqual(len(result.structure), 2)

    def test_xlsx(self) -> None:
        result = parse_document(self.xlsx)
        self.assertTrue(result.ok, result.error)
        self.assertEqual(result.metadata.page_count, 2)
        self.assertEqual([h.title for h in result.structure], ["基础", "进阶"])
        self.assertIn("时延", result.text)
        self.assertIn("100", result.text)

    def test_csv(self) -> None:
        result = parse_document(self.csv)
        self.assertTrue(result.ok, result.error)
        self.assertIn("时延", result.text)
        self.assertIn("100", result.text)

    def test_markdown(self) -> None:
        result = parse_document(self.md)
        self.assertTrue(result.ok, result.error)
        self.assertEqual([(h.level, h.title) for h in result.structure], [(1, "计算机网络"), (2, "1.1 网络定义")])

    def test_txt(self) -> None:
        result = parse_document(self.txt)
        self.assertTrue(result.ok, result.error)
        self.assertIn("第1章", result.text)
        self.assertGreaterEqual(len(result.structure), 2)

    def test_html(self) -> None:
        result = parse_document(self.html)
        self.assertTrue(result.ok, result.error)
        self.assertIn("网络协议是通信规则", result.text)
        self.assertNotIn("hidden", result.text)
        self.assertEqual([(h.level, h.title) for h in result.structure], [(1, "计算机网络"), (2, "1.1 网络定义")])

    def test_max_chars(self) -> None:
        result = parse_document(self.md, config={"text": {"max_chars": 5}})
        self.assertTrue(result.ok, result.error)
        self.assertLessEqual(len(result.text), 5)

    # ------------------------------------------------------------------
    def test_error_missing(self) -> None:
        result = parse_document(os.path.join(self.tmp, "none.pdf"))
        self.assertFalse(result.ok)
        self.assertEqual(result.error.code, ErrorCode.DOCUMENT_FILE_NOT_FOUND)

    def test_error_unsupported(self) -> None:
        path = os.path.join(self.tmp, "a.xyz")
        with open(path, "wb") as fp:
            fp.write(b"data")
        result = parse_document(path)
        self.assertFalse(result.ok)
        self.assertEqual(result.error.code, ErrorCode.UNSUPPORTED_DOCUMENT_FORMAT)

    def test_error_empty(self) -> None:
        path = os.path.join(self.tmp, "empty.pdf")
        open(path, "wb").close()
        result = parse_document(path)
        self.assertFalse(result.ok)
        self.assertEqual(result.error.code, ErrorCode.DOCUMENT_FILE_EMPTY)

    def test_error_corrupt_pdf(self) -> None:
        path = os.path.join(self.tmp, "bad.pdf")
        with open(path, "wb") as fp:
            fp.write(b"not a pdf")
        result = parse_document(path)
        self.assertFalse(result.ok)
        self.assertEqual(result.error.code, ErrorCode.DOCUMENT_TEXT_EXTRACT_FAILED)

    def test_error_corrupt_docx(self) -> None:
        path = os.path.join(self.tmp, "bad.docx")
        with open(path, "wb") as fp:
            fp.write(b"not a zip")
        result = parse_document(path)
        self.assertFalse(result.ok)
        self.assertEqual(result.error.code, ErrorCode.DOCUMENT_TEXT_EXTRACT_FAILED)

    def test_strict_raises(self) -> None:
        with self.assertRaises(ParserError) as ctx:
            parse_document(os.path.join(self.tmp, "none.pdf"), strict=True)
        self.assertEqual(ctx.exception.code, ErrorCode.DOCUMENT_FILE_NOT_FOUND)

    def test_invalid_config(self) -> None:
        with self.assertRaises(ParserError) as ctx:
            DocumentConfig.from_dict({"pdf": {"ocr_dpi": 10}})
        self.assertEqual(ctx.exception.code, ErrorCode.INVALID_CONFIG)

    def test_result_serializable(self) -> None:
        result = parse_document(self.md)
        payload = result.to_dict()
        json.dumps(payload, ensure_ascii=False)
        self.assertIn("text", payload)

    # ------------------------------------------------------------------
    def test_custom_plugin(self) -> None:
        """演示：新增 .mkd 格式只需注册处理器，核心流程不改。"""

        @register_document_handler(".mkd")
        class MkdHandler(BaseDocumentHandler):
            name = "mkd"

            def extract(self, source, config, attachment_dir):
                with open(source, encoding="utf-8") as fp:
                    text = fp.read()
                return DocumentExtract(
                    metadata=DocumentMetadata(
                        path=source, format=".mkd", size_bytes=os.path.getsize(source), page_count=1
                    ),
                    pages=[DocumentPage(index=1, text=text)],
                )

        path = os.path.join(self.tmp, "plugin.mkd")
        with open(path, "w", encoding="utf-8") as fp:
            fp.write("# 自定义格式\n插件注册成功。\n")

        result = parse_document(path)
        self.assertTrue(result.ok, result.error)
        self.assertIn("插件注册成功", result.text)
        self.assertEqual(result.metadata.format, ".mkd")

        from media_kit import supported_document_formats

        self.assertIn(".mkd", supported_document_formats())


if __name__ == "__main__":
    unittest.main()
