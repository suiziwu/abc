package com.ruoyi.project.kb;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

/**
 * 课本/文档解析器：把上传的 Markdown / Word(.docx) / PDF 解析为带层级与页码的
 * 结构化片段 {@link DocSection} 列表，供 {@link SemanticChunker} 进一步语义切分。
 *
 * <p>三种格式统一产出 {@link DocSection}：Markdown 按 # 标题层级切分；Word 优先按
 * 标题样式切分、退化为全文结构识别；PDF 按页切分（保留页码），再交给语义切分细化。</p>
 *
 * @author ruoyi
 */
public class DocumentParser
{
    /** 解析后的一段（可能对应一页、一个标题章节） */
    public static class DocSection
    {
        /** 层级：0=无标题正文，1..6 对应标题深浅 */
        public int level;
        /** 标题（可能为空） */
        public String title;
        /** 正文 */
        public String content;
        /** 起始页（PDF 有效） */
        public Integer pageStart;
        /** 结束页（PDF 有效） */
        public Integer pageEnd;

        public DocSection(int level, String title, String content)
        {
            this.level = level;
            this.title = title == null ? "" : title;
            this.content = content == null ? "" : content;
        }
    }

    /** 解析结果 */
    public static class ParseResult
    {
        public String title;
        public List<DocSection> sections = new ArrayList<>();
    }

    private static final Pattern MD_HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern CN_CHAPTER = Pattern.compile("^第[一二三四五六七八九十百千0-9]+[章节篇].*");
    private static final Pattern NUMBERED = Pattern.compile(
            "^([0-9]+(\\.[0-9]+)*|[\\(（][0-9]+[\\)）]|[一二三四五六七八九十]+[、．.])\\s*.*");

    /**
     * 解析文档字节流。
     *
     * @param bytes   文件字节
     * @param fileName 文件名（用于判断格式）
     * @return 结构化片段
     */
    public ParseResult parse(byte[] bytes, String fileName) throws IOException
    {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".md") || lower.endsWith(".markdown"))
        {
            return parseMarkdown(new String(bytes, StandardCharsets.UTF_8));
        }
        if (lower.endsWith(".txt"))
        {
            return parsePlainText(new String(bytes, StandardCharsets.UTF_8));
        }
        if (lower.endsWith(".docx"))
        {
            return parseDocx(bytes);
        }
        if (lower.endsWith(".pdf"))
        {
            return parsePdf(bytes);
        }
        // 兜底：纯文本
        return parsePlainText(new String(bytes, StandardCharsets.UTF_8));
    }

    private ParseResult parseMarkdown(String text)
    {
        ParseResult result = new ParseResult();
        String[] lines = text.split("\n", -1);
        DocSection current = null;
        StringBuilder buf = new StringBuilder();
        for (String line : lines)
        {
            java.util.regex.Matcher m = MD_HEADING.matcher(line);
            if (m.find())
            {
                flush(current, buf, result);
                int level = m.group(1).length();
                current = new DocSection(level, m.group(2).trim(), "");
                buf = new StringBuilder();
            }
            else
            {
                if (current == null)
                {
                    current = new DocSection(0, "", "");
                    buf = new StringBuilder();
                }
                buf.append(line).append("\n");
            }
        }
        flush(current, buf, result);
        if (result.sections.isEmpty())
        {
            result.sections.add(new DocSection(0, "", text.trim()));
        }
        return result;
    }

    private ParseResult parsePlainText(String text)
    {
        ParseResult result = new ParseResult();
        result.sections = detectStructure(text);
        return result;
    }

    private ParseResult parseDocx(byte[] bytes) throws IOException
    {
        List<String> paragraphs = new ArrayList<>();
        List<Boolean> headingFlags = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes)))
        {
            for (IBodyElement element : doc.getBodyElements())
            {
                if (element instanceof XWPFParagraph)
                {
                    XWPFParagraph p = (XWPFParagraph) element;
                    String text = p.getText();
                    if (text == null || text.trim().isEmpty())
                    {
                        continue;
                    }
                    paragraphs.add(text);
                    headingFlags.add(isHeadingStyle(p));
                }
                else if (element instanceof XWPFTable)
                {
                    XWPFTable table = (XWPFTable) element;
                    for (XWPFTableRow row : table.getRows())
                    {
                        StringBuilder sb = new StringBuilder();
                        for (XWPFTableCell cell : row.getTableCells())
                        {
                            sb.append(cell.getText()).append(" ");
                        }
                        String t = sb.toString().trim();
                        if (!t.isEmpty())
                        {
                            paragraphs.add(t);
                            headingFlags.add(false);
                        }
                    }
                }
            }
        }
        ParseResult result = new ParseResult();
        if (Boolean.TRUE.equals(java.util.Collections.max(headingFlags)))
        {
            result.sections = splitByFlags(paragraphs, headingFlags);
        }
        else
        {
            result.sections = detectStructure(String.join("\n", paragraphs));
        }
        return result;
    }

    private ParseResult parsePdf(byte[] bytes) throws IOException
    {
        ParseResult result = new ParseResult();
        try (PDDocument doc = PDDocument.load(bytes))
        {
            if (doc.getDocumentInformation() != null)
            {
                result.title = doc.getDocumentInformation().getTitle();
            }
            int pages = doc.getNumberOfPages();
            for (int p = 1; p <= pages; p++)
            {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String pageText = stripper.getText(doc);
                if (pageText == null || pageText.trim().isEmpty())
                {
                    continue;
                }
                DocSection section = new DocSection(1, "第" + p + "页", pageText.trim());
                section.pageStart = p;
                section.pageEnd = p;
                result.sections.add(section);
            }
        }
        if (result.sections.isEmpty())
        {
            result.sections.add(new DocSection(0, "", ""));
        }
        return result;
    }

    /** 按 Markdown 之外的格式做标题结构识别 */
    private List<DocSection> detectStructure(String text)
    {
        List<DocSection> sections = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        DocSection current = null;
        StringBuilder buf = new StringBuilder();
        boolean anyHeading = false;
        for (String raw : lines)
        {
            String line = raw.trim();
            if (line.isEmpty())
            {
                if (current != null)
                {
                    buf.append("\n");
                }
                continue;
            }
            if (isHeadingLine(line))
            {
                anyHeading = true;
                flush(current, buf, sections);
                current = new DocSection(1, line, "");
                buf = new StringBuilder();
            }
            else
            {
                if (current == null)
                {
                    current = new DocSection(0, "", "");
                    buf = new StringBuilder();
                }
                buf.append(raw).append("\n");
            }
        }
        flush(current, buf, sections);
        if (!anyHeading && sections.size() <= 1)
        {
            // 完全没有标题结构，整段作为一个 section
            if (sections.isEmpty())
            {
                sections.add(new DocSection(0, "", text.trim()));
            }
        }
        return sections;
    }

    private List<DocSection> splitByFlags(List<String> paragraphs, List<Boolean> flags)
    {
        List<DocSection> sections = new ArrayList<>();
        DocSection current = null;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < paragraphs.size(); i++)
        {
            String text = paragraphs.get(i);
            if (Boolean.TRUE.equals(flags.get(i)))
            {
                flush(current, buf, sections);
                current = new DocSection(1, text, "");
                buf = new StringBuilder();
            }
            else
            {
                if (current == null)
                {
                    current = new DocSection(0, "", "");
                    buf = new StringBuilder();
                }
                buf.append(text).append("\n");
            }
        }
        flush(current, buf, sections);
        return sections;
    }

    private void flush(DocSection section, StringBuilder buf, ParseResult result)
    {
        if (section == null)
        {
            return;
        }
        section.content = buf.toString().trim();
        // 丢弃完全空白的空章节
        if (section.title.isEmpty() && section.content.isEmpty())
        {
            return;
        }
        result.sections.add(section);
    }

    private void flush(DocSection section, StringBuilder buf, List<DocSection> sections)
    {
        if (section == null)
        {
            return;
        }
        section.content = buf.toString().trim();
        if (section.title.isEmpty() && section.content.isEmpty())
        {
            return;
        }
        sections.add(section);
    }

    /** Word 段落是否标题样式 */
    private boolean isHeadingStyle(XWPFParagraph p)
    {
        String style = p.getStyle();
        if (style != null)
        {
            String s = style.toLowerCase();
            if (s.contains("heading") || s.contains("标题"))
            {
                return true;
            }
        }
        try
        {
            if (p.getCTP() != null && p.getCTP().getPPr() != null && p.getCTP().getPPr().getOutlineLvl() != null)
            {
                return true;
            }
        }
        catch (Exception ignored)
        {
            // 部分段落无 CTP，忽略
        }
        return false;
    }

    /** 纯文本/PDF 行是否标题（启发式） */
    private boolean isHeadingLine(String t)
    {
        if (t.length() > 50)
        {
            return false;
        }
        if (MD_HEADING.matcher(t).find())
        {
            return true;
        }
        if (CN_CHAPTER.matcher(t).find())
        {
            return true;
        }
        if (NUMBERED.matcher(t).find())
        {
            return true;
        }
        // 短且不以标点结尾，视为标题（避免过度误判，要求至少 4 字）
        if (t.length() >= 4 && t.length() <= 30
                && !t.endsWith("。") && !t.endsWith(".") && !t.endsWith("，") && !t.endsWith(",")
                && !t.endsWith("；") && !t.endsWith(";") && !t.endsWith("：") && !t.endsWith(":"))
        {
            return true;
        }
        return false;
    }
}
