import os
import json
import re
from docx import Document
from openai import OpenAI

# ---------- 配置 ----------
# Key 只从环境变量读取，不要写进代码：$env:DEEPSEEK_API_KEY = "sk-..."
DEEPSEEK_API_KEY = os.environ.get("DEEPSEEK_API_KEY", "").strip()
DEEPSEEK_BASE_URL = "https://api.deepseek.com"
MODEL_NAME = "deepseek-chat"

if not DEEPSEEK_API_KEY:
    print("❌ 错误：请先设置环境变量 DEEPSEEK_API_KEY")
    exit(1)

# ---------- 文档解析器 ----------
class DocumentParser:
    def __init__(self, docx_path):
        self.doc = Document(docx_path)
        self.texts = [p.text.strip() for p in self.doc.paragraphs if p.text.strip()]
        self.chapters = []

    def parse_chapters(self):
        # 宽松匹配“第X章”
        chapter_pattern = re.compile(r'.*第\s*\d+\s*章.*')
        processed = set()
        current_chapter = None
        current_content = []

        for text in self.texts:
            if any(x in text for x in ["media", "image", "http", "www", "版权"]):
                continue

            if chapter_pattern.match(text):
                match = re.search(r'第\s*(\d+)\s*章', text)
                if match:
                    num = int(match.group(1))
                    if num not in processed:
                        # 保存上一个章节
                        if current_chapter:
                            self.chapters.append({
                                'title': current_chapter,
                                'content': '\n'.join(current_content)
                            })
                        current_chapter = f"第{num}章"
                        current_content = []
                        processed.add(num)
                        continue  # 不将标题行加入内容
            # 非标题行或重复标题都归入当前章节内容
            if current_chapter:
                current_content.append(text)

        # 保存最后一章
        if current_chapter:
            self.chapters.append({
                'title': current_chapter,
                'content': '\n'.join(current_content)
            })

        return self.chapters

# ---------- 调用 DeepSeek API ----------
def call_deepseek(prompt):
    client = OpenAI(
        api_key=DEEPSEEK_API_KEY,
        base_url=DEEPSEEK_BASE_URL
    )
    response = client.chat.completions.create(
        model=MODEL_NAME,
        messages=[
            {"role": "system", "content": "你是Java技术专家，输出必须为严格合法的JSON数组。"},
            {"role": "user", "content": prompt}
        ],
        temperature=0.3,
        response_format={"type": "json_object"}
    )
    return response.choices[0].message.content

# ---------- 提取知识点 ----------
def extract_knowledge(chapter_title, chapter_content):
    prompt = f"""
请从以下章节内容中提取核心知识点，按JSON数组输出，每个元素包含：
- "知识点": 简洁术语（不要包含大段描述）
- "重要程度": 1~5颗星，如"⭐⭐⭐⭐⭐"
- "技能类型": "理论"或"实操"
- "前置知识": 没有则填"—"

章节：{chapter_title}
内容：{chapter_content[:4000]}

只输出JSON数组，不要其他内容。
"""
    try:
        resp = call_deepseek(prompt)
        items = json.loads(resp)
        for item in items:
            item.setdefault("前置知识", "—")
            item['章节'] = chapter_title
        return items
    except Exception as e:
        print(f"提取失败: {e}")
        return []

# ---------- 清洗去重 ----------
def clean_knowledge(knowledge_list):
    seen = set()
    cleaned = []
    for item in knowledge_list:
        # 统一章节格式（去掉多余空格）
        ch = re.search(r'第\d+章', item['章节'])
        if ch:
            item['章节'] = ch.group()
        # 知识点简化（去掉括号内容）
        concept = item['知识点'].split('（')[0].split('(')[0].strip()
        if len(concept) < 2:
            continue
        item['知识点'] = concept
        key = (item['章节'], concept)
        if key not in seen:
            seen.add(key)
            cleaned.append(item)
    return cleaned

# ---------- 提取章节号用于排序 ----------
def extract_chapter_num(title):
    match = re.search(r'第(\d+)章', title)
    return int(match.group(1)) if match else 999

# ---------- 主流程 ----------
def main():
    docx_path = r"D:\BaiduNetdiskDownload\新建文件夹\《Java从入门到精通》_全本.docx"
    if not os.path.exists(docx_path):
        print(f"❌ 文件不存在：{docx_path}")
        return

    print("📖 正在解析文档...")
    parser = DocumentParser(docx_path)
    chapters = parser.parse_chapters()
    print(f"✅ 共解析出 {len(chapters)} 章（排序前）")

    # ---------- 按章节号排序 ----------
    chapters.sort(key=lambda ch: extract_chapter_num(ch['title']))
    print("📋 已按章节顺序排序")

    all_knowledge = []
    for idx, ch in enumerate(chapters, 1):
        print(f"⏳ 处理 ({idx}/{len(chapters)})：{ch['title']}")
        items = extract_knowledge(ch['title'], ch['content'])
        all_knowledge.extend(items)

    # ---------- 清洗 ----------
    print("\n🧹 正在清洗去重...")
    cleaned = clean_knowledge(all_knowledge)
    print(f"📊 清洗后剩余 {len(cleaned)} 条知识点")

    # ---------- 输出路径：工程根目录的 05-知识点数据 ----------
    out_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "05-知识点数据")
    os.makedirs(out_dir, exist_ok=True)
    md_path = os.path.join(out_dir, "知识点-Java_排序版.md")
    json_path = os.path.join(out_dir, "知识点-Java_排序版.json")

    # ---------- 输出 Markdown 表格 ----------
    with open(md_path, "w", encoding="utf-8") as f:
        f.write("| 章节 | 知识点 | 重要程度 | 技能类型 | 前置知识 |\n")
        f.write("|------|--------|----------|----------|----------|\n")
        for row in cleaned:
            chapter = row['章节'].replace('|', '\\|')
            concept = row['知识点'].replace('|', '\\|')
            pre = row['前置知识'].replace('|', '\\|')
            f.write(f"| {chapter} | {concept} | {row['重要程度']} | {row['技能类型']} | {pre} |\n")

    # 同时保存 JSON
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(cleaned, f, ensure_ascii=False, indent=2)

    print("\n✅ 完成！输出文件：")
    print(f"   - {md_path}")
    print(f"   - {json_path}")

if __name__ == "__main__":
    main()