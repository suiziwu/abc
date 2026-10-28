import csv
import time
import os
import requests
import re
import json
from DrissionPage import ChromiumPage, ChromiumOptions
from datetime import datetime
import pandas as pd

# ================== 配置 ==================
SERVER_URL = "http://110.40.186.228:5000/upload"
# 存储位置：工程根目录下的 09-智联招聘输出 文件夹（相对路径，自动创建）
_BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA_DIR = os.path.join(_BASE_DIR, "09-智联招聘输出")
HISTORY_FILE = os.path.join(DATA_DIR, "cleaned_zhaopin_jobs_history.csv")
JSON_DATA_FILE = os.path.join(DATA_DIR, "job_skills_data_zhaopin.json")

# ========== 爬虫配置 ==========
TARGET_COUNT_PER_KEYWORD = 100  # 每个城市每个关键词目标600条

JOB_KEYWORDS = [
    "AI全栈开发",

]

# ========== 城市配置（智联招聘城市代码）==========
CITIES = [
    {"code": "530", "name": "北京"},
    {"code": "538", "name": "上海"},
    {"code": "763", "name": "广州"},
    {"code": "765", "name": "深圳"},
    {"code": "653", "name": "杭州"},
    {"code": "635", "name": "南京"},
    {"code": "637", "name": "西安"},
    {"code": "702", "name": "苏州"},
    {"code": "600", "name": "济南"},
    {"code": "669", "name": "郑州"},

]

# 是否启用多城市爬取
ENABLE_MULTI_CITY = True
MAX_CITIES = 10  # 先爬10个主要城市，设为0爬全部

# ========== 技能库 ==========
skills_dict = {
    '编程语言': [
        'Python', 'Java', 'C++', 'JavaScript', 'TypeScript', 'Go', 'PHP', 'Node.js',
        'Ruby', 'Rust', 'C#', '.NET', 'SQL', 'HTML5', 'CSS3', 'Scala', 'Kotlin', 'Swift'
    ],
    '框架/库': [
        'React', 'Vue', 'Angular', 'Next.js', 'Nuxt.js', 'Redux', 'Vuex', 'Pinia',
        'Element UI', 'Ant Design', 'Tailwind CSS', 'Bootstrap', 'jQuery',
        'Django', 'Flask', 'FastAPI', 'Spring Boot', 'Spring Cloud', 'MyBatis', 'MyBatis Plus',
        'Hibernate', 'Express', 'NestJS', 'Koa', 'Laravel', 'Gin', 'TensorFlow', 'PyTorch',
        'LangChain', 'Pydantic', 'LightRAG', 'SSE', 'WordPress', 'Shopify',
        'Cursor', 'Trae', 'Windsurf', 'Cloud Code'
    ],
    '数据库/中间件': [
        'MySQL', 'PostgreSQL', 'Oracle', 'SQL Server', 'SQLite',
        'Redis', 'MongoDB', 'Elasticsearch', 'HBase', 'Cassandra',
        'Kafka', 'RabbitMQ', 'RocketMQ', 'ActiveMQ', 'MQ'
    ],
    '工具/环境': [
        'Docker', 'Kubernetes', 'K8s', 'Linux', 'Nginx', 'Apache', 'Jenkins',
        'GitLab CI', 'GitHub Actions', 'CI/CD', 'DevOps', 'Shell', 'Bash',
        '阿里云', '腾讯云', 'AWS', 'Azure', '华为云', 'AGC平台', '鸿蒙元服务',
        'Git', 'SVN', 'RESTful API', 'GraphQL', 'WebSocket', 'Swagger', 'Postman', 'gRPC'
    ]
}


# ================== 技能检测函数 ==================
def has_technical_skills(job_desc, job_title):
    if not job_desc:
        return False, []
    text = (job_desc + " " + job_title).lower()

    exclude_keywords = [
        "机房巡检", "设备维护", "桌面支持", "共享单车", "充电宝",
        "机器人操作", "数据采集员", "销售代表", "客户经理",
        "行政", "人事", "助理", "无经验可教", "接受小白",
        "不限专业", "学历不限", "无需经验", "岗前培训",
        "客服", "保洁", "保安", "司机", "配送", "外卖",
        "普工", "操作工", "质检", "包装", "仓储"
    ]
    for kw in exclude_keywords:
        if kw in text:
            return False, []

    matched_skills = []
    programming_keywords = ["python", "java", "c++", "javascript", "typescript", "go", "rust", "c#", "php", "sql"]
    for kw in programming_keywords:
        if kw in text:
            matched_skills.append(kw)

    framework_keywords = ["spring", "django", "flask", "fastapi", "react", "vue", "angular",
                          "tensorflow", "pytorch", "langchain", "mybatis", "redis", "mysql",
                          "docker", "kubernetes", "k8s", "jenkins", "git"]
    for kw in framework_keywords:
        if kw in text:
            matched_skills.append(kw)

    ai_keywords = ["算法", "大模型", "llm", "agent", "rag", "机器学习", "深度学习", "计算机视觉"]
    for kw in ai_keywords:
        if kw in text:
            matched_skills.append(kw)

    tech_titles = ["开发", "工程师", "研发", "算法", "架构", "后端", "前端", "全栈"]
    for kw in tech_titles:
        if kw in job_title.lower():
            matched_skills.append(kw)
            break

    return len(matched_skills) > 0, list(set(matched_skills))


# ================== 导出JSON ==================
def export_data_to_json(data_df, json_file_path):
    if data_df.empty:
        with open(json_file_path, 'w', encoding='utf-8') as f:
            json.dump([], f, ensure_ascii=False, indent=2)
        return False

    json_data = []
    for _, row in data_df.iterrows():
        record = {}
        keyword = str(row.get('岗位关键词', ''))
        if keyword and keyword != 'nan':
            record["岗位关键词"] = keyword
        city = str(row.get('城市', ''))
        if city and city != 'nan':
            record["城市"] = city
        job_name = str(row.get('职位名称', ''))
        if job_name and job_name != 'nan':
            record["职位名称"] = job_name
        for category in ['编程语言', '框架/库', '数据库/中间件', '工具/环境']:
            val = str(row.get(category, ''))
            if val and val != '' and val != 'nan' and val != '无':
                record[category] = val
        if record:
            json_data.append(record)

    with open(json_file_path, 'w', encoding='utf-8') as f:
        json.dump(json_data, f, ensure_ascii=False, indent=2)
    print(f"✅ 已导出JSON: {json_file_path} (共{len(json_data)}条)")
    return True


# ================== 智联招聘爬虫函数 ==================
def zhaopin_login(page):
    """登录智联招聘"""
    print("打开智联招聘...")
    page.get('https://www.zhaopin.com')
    page.wait(3)
    print("已打开智联招聘首页")


def fetch_jobs_from_zhaopin(keyword, city_code, city_name):
    """从智联招聘爬取职位"""
    co = ChromiumOptions().headless(False)
    co.set_argument('--disable-blink-features=AutomationControlled')
    co.set_argument('--start-maximized')
    try:
        co.set_browser_path(r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe')
    except:
        co.set_browser_path(r'C:\Program Files\Microsoft\Edge\Application\msedge.exe')

    page = ChromiumPage(co)
    zhaopin_login(page)

    print(f"\n🔍 开始爬取关键词：【{keyword}】在【{city_name}】的职位（目标{TARGET_COUNT_PER_KEYWORD}条）")

    # 智联招聘搜索URL
    search_url = f"https://sou.zhaopin.com/?jl={city_code}&kw={keyword}&p=1"
    print(f"🌐 搜索URL: {search_url}")
    page.get(search_url)
    page.wait(5)

    all_jobs = []
    page_num = 1
    max_pages = 50

    while page_num <= max_pages and len(all_jobs) < TARGET_COUNT_PER_KEYWORD:
        print(f"\n📄 正在处理第 {page_num} 页...")

        # 等待页面加载
        page.wait(2)

        # 获取职位卡片 - 智联招聘的选择器
        job_cards = page.eles('xpath://div[contains(@class, "joblist-box__item")]', timeout=3)
        if not job_cards:
            job_cards = page.eles('css:.joblist-box__item', timeout=3)
        if not job_cards:
            job_cards = page.eles(
                'xpath://div[contains(@class, "search-job-result")]//div[contains(@class, "job-item")]', timeout=3)

        if not job_cards:
            print(f"⚠️ 第 {page_num} 页未找到职位卡片")
            break

        print(f"📊 第 {page_num} 页找到 {len(job_cards)} 个职位")

        # 提取职位信息
        for card in job_cards:
            if len(all_jobs) >= TARGET_COUNT_PER_KEYWORD:
                break

            try:
                # 提取职位名称
                title_el = card.ele('css:.job-title a, .position-title a, .job-name a', timeout=1)
                if not title_el:
                    title_el = card.ele('xpath://a[contains(@class, "job-title")]', timeout=1)

                if not title_el:
                    continue

                job_title = title_el.text.strip() if title_el else "未知职位"

                # 获取职位详情链接
                job_link = title_el.attr('href') if title_el else None
                if job_link:
                    if job_link.startswith('//'):
                        job_link = 'https:' + job_link
                    elif job_link.startswith('/'):
                        job_link = 'https://sou.zhaopin.com' + job_link

                # 进入详情页获取职位描述
                job_desc = ""
                if job_link:
                    try:
                        print(f"  🔗 进入详情页: {job_title}")
                        page.get(job_link)
                        page.wait(2)

                        # 提取职位描述
                        desc_el = page.ele(
                            'css:.job-description, .position-detail, .detail-content, .position-description', timeout=2)
                        if desc_el:
                            job_desc = desc_el.text.replace('\n', ' ').strip()

                        # 如果没找到，尝试其他选择器
                        if not job_desc or len(job_desc) < 20:
                            desc_el = page.ele('xpath://div[contains(@class, "description")]', timeout=2)
                            if desc_el:
                                job_desc = desc_el.text.replace('\n', ' ').strip()

                        # 尝试使用智联招聘的职位描述标签
                        if not job_desc or len(job_desc) < 20:
                            desc_el = page.ele(
                                'xpath://div[contains(@class, "position-detail")]//div[contains(@class, "content")]',
                                timeout=2)
                            if desc_el:
                                job_desc = desc_el.text.replace('\n', ' ').strip()

                        # 回到搜索页面
                        page.back()
                        page.wait(2)

                    except Exception as e:
                        print(f"  ⚠️ 获取详情失败: {e}")
                        # 如果详情页获取失败，尝试回到搜索页
                        try:
                            page.back()
                            page.wait(2)
                        except:
                            pass

                # 如果没有获取到描述，使用卡片上的信息
                if not job_desc or len(job_desc) < 20:
                    desc_el = card.ele('css:.job-description, .position-desc, .job-desc', timeout=1)
                    if desc_el:
                        job_desc = desc_el.text.replace('\n', ' ').strip()

                # 检测技术含量
                has_skill, matched_skills = has_technical_skills(job_desc, job_title)

                if not has_skill:
                    continue

                print(f"  ✅ 保留: {job_title} | 匹配技能: {matched_skills[:3] if matched_skills else '无'}...")

                # 构造记录
                record = [keyword, city_name, job_title, job_desc if job_desc else ""]
                all_jobs.append(record)
                print(f"  📝 已保存 {len(all_jobs)}/{TARGET_COUNT_PER_KEYWORD} 条")

            except Exception as e:
                print(f"  ❌ 处理职位失败: {e}")
                continue

        # 检查是否有下一页
        if len(all_jobs) >= TARGET_COUNT_PER_KEYWORD:
            break

        # 尝试点击下一页
        try:
            next_btn = page.ele('xpath://a[contains(text(), "下一页")]', timeout=2)
            if not next_btn:
                next_btn = page.ele('css:.next-page, .pagination-next', timeout=2)

            if next_btn:
                if 'disabled' in (next_btn.attr('class') or ''):
                    print("📄 已到达最后一页")
                    break
                next_btn.click()
                page_num += 1
                page.wait(3)
            else:
                # 尝试使用URL翻页
                page_num += 1
                page.get(f"https://sou.zhaopin.com/?jl={city_code}&kw={keyword}&p={page_num}")
                page.wait(3)

        except Exception as e:
            print(f"⚠️ 翻页失败: {e}")
            # 尝试URL翻页
            page_num += 1
            try:
                page.get(f"https://sou.zhaopin.com/?jl={city_code}&kw={keyword}&p={page_num}")
                page.wait(3)
            except:
                break

        time.sleep(1)

    page.quit()
    print(f"\n📊 【{keyword}】在【{city_name}】爬取完成: 成功 {len(all_jobs)} 条")
    return all_jobs


def get_all_jobs():
    """获取所有职位（支持单城市/多城市）"""
    all_data = []

    # 确定要爬取的城市列表
    if ENABLE_MULTI_CITY:
        if MAX_CITIES > 0 and MAX_CITIES < len(CITIES):
            cities_to_crawl = CITIES[:MAX_CITIES]
        else:
            cities_to_crawl = CITIES
        print(f"\n🌍 多城市爬取模式：共 {len(cities_to_crawl)} 个城市")
        print(f"📋 城市列表: {', '.join([c['name'] for c in cities_to_crawl])}")
    else:
        cities_to_crawl = [{"code": "530", "name": "北京"}]
        print(f"\n📍 单城市爬取模式：北京")

    for kw in JOB_KEYWORDS:
        for city in cities_to_crawl:
            print(f"\n{'=' * 50}")
            print(f"🚀 开始爬取: 关键词【{kw}】在【{city['name']}】")
            print(f"🎯 目标: {TARGET_COUNT_PER_KEYWORD} 条")
            print(f"{'=' * 50}")

            data = fetch_jobs_from_zhaopin(kw, city['code'], city['name'])
            if data:
                all_data.extend(data)
                print(f"✅ 【{kw}】【{city['name']}】完成，获得 {len(data)} 条")
            else:
                print(f"⚠️ 【{kw}】【{city['name']}】未获取到有效数据")

            time.sleep(3)

    return all_data


def save_raw_data_to_csv(data):
    """保存原始数据"""
    if not data:
        return None
    os.makedirs(DATA_DIR, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    csv_file = os.path.join(DATA_DIR, f"zhaopin_multi_{timestamp}.csv")
    with open(csv_file, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.writer(f)
        writer.writerow(["岗位关键词", "城市", "职位名称", "职位描述"])
        for record in data:
            writer.writerow(record)
    print(f"💾 原始数据已保存到本地：{csv_file} (共{len(data)}条)")
    return csv_file


def extract_skills(desc):
    """从描述中提取技能"""
    if not isinstance(desc, str) or not desc:
        return {category: [] for category in skills_dict.keys()}

    found_skills = {category: [] for category in skills_dict.keys()}
    for category, keywords in skills_dict.items():
        for keyword in keywords:
            pattern = r'\b' + re.escape(keyword) + r'\b'
            if re.search(pattern, desc, re.IGNORECASE):
                clean_keyword = keyword.strip()
                if clean_keyword not in found_skills[category]:
                    found_skills[category].append(clean_keyword)
    return found_skills


def clean_and_extract_skills_from_csv(csv_path):
    """清洗数据"""
    df = pd.read_csv(csv_path, encoding='utf-8-sig')
    required_cols = ['岗位关键词', '城市', '职位名称', '职位描述']

    if '城市' not in df.columns:
        df['城市'] = '未知'

    for col in required_cols:
        if col not in df.columns:
            print(f"⚠️ 文件缺少列: {col}")
            return pd.DataFrame()

    results = []
    no_core_skill_count = 0

    for _, row in df.iterrows():
        skills = extract_skills(row['职位描述'])

        has_required_skill = False
        for cat in ['框架/库', '编程语言', '工具/环境']:
            if skills.get(cat) and len(skills[cat]) > 0:
                has_required_skill = True
                break

        if not has_required_skill:
            no_core_skill_count += 1
            continue

        result_row = {
            '岗位关键词': row['岗位关键词'],
            '城市': row['城市'],
            '职位名称': row['职位名称'],
        }
        for category in skills_dict.keys():
            val = ', '.join(skills[category]) if skills[category] else ''
            result_row[category] = val if val else ''

        results.append(result_row)

    print(f"📊 清洗统计:")
    print(f"   ├─ 原始数据: {len(df)} 条")
    print(f"   ├─ 无核心技能过滤: {no_core_skill_count} 条")
    print(f"   └─ 最终保留: {len(results)} 条")

    return pd.DataFrame(results)


def send_cleaned_data_to_server(data_df):
    """发送数据到服务器"""
    if data_df.empty:
        print("⚠️ 清洗后无数据可发送")
        return
    data_json = data_df.to_dict('records')
    try:
        resp = requests.post(SERVER_URL, json=data_json, timeout=10)
        if resp.status_code == 200:
            print(f"✅ 成功发送清洗后的 {len(data_json)} 条数据到服务器")
        else:
            print(f"❌ 发送失败 (HTTP {resp.status_code})")
    except Exception as e:
        print(f"❌ 网络错误: {e}")


def safe_save_dataframe(df, filepath):
    """安全保存DataFrame"""
    try:
        file_exists = os.path.exists(filepath)
        if file_exists:
            existing_df = pd.read_csv(filepath, encoding='utf-8-sig')
            combined_df = pd.concat([existing_df, df], ignore_index=True)
            combined_df = combined_df.drop_duplicates(subset=['岗位关键词', '城市', '职位名称'], keep='last')
            combined_df.to_csv(filepath, index=False, encoding='utf-8-sig')
        else:
            df.to_csv(filepath, index=False, encoding='utf-8-sig')
        return True
    except Exception as e:
        print(f"❌ 保存失败: {e}")
        return False


def process_cleaning_and_upload(raw_csv_path):
    """处理清洗和上传"""
    print("\n===== 开始自动清洗数据 =====")

    today_df = clean_and_extract_skills_from_csv(raw_csv_path)
    if today_df.empty:
        print("❌ 清洗失败，无有效数据")
        export_data_to_json(pd.DataFrame(), JSON_DATA_FILE)
        return

    print(f"✨ 今日清洗出 {len(today_df)} 条技能记录")

    if not safe_save_dataframe(today_df, HISTORY_FILE):
        print("❌ 无法保存历史文件，本次清洗中断")
        return

    all_df = pd.read_csv(HISTORY_FILE, encoding='utf-8-sig')
    export_data_to_json(all_df, JSON_DATA_FILE)
    send_cleaned_data_to_server(all_df)

    print("\n📋 清洗结果预览（前5条）:")
    display_cols = ['岗位关键词', '城市', '职位名称', '编程语言', '框架/库', '工具/环境']
    available_cols = [c for c in display_cols if c in today_df.columns]
    print(today_df[available_cols].head())


def run_once():
    """单次运行"""
    print("=" * 50)
    print(f"🚀 开始执行爬取任务 —— {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"🎯 每个城市每个关键词目标: {TARGET_COUNT_PER_KEYWORD} 条")

    if ENABLE_MULTI_CITY:
        city_count = MAX_CITIES if MAX_CITIES > 0 else len(CITIES)
        print(f"🏙️ 多城市模式：将爬取 {city_count} 个城市")
        print(f"🔑 关键词数量: {len(JOB_KEYWORDS)} 个")
        print(f"📊 预计最大爬取量: {len(JOB_KEYWORDS) * city_count * TARGET_COUNT_PER_KEYWORD} 条")
    else:
        print(f"📍 单城市模式：北京")

    print("=" * 50)

    raw_data = get_all_jobs()
    if raw_data:
        raw_csv = save_raw_data_to_csv(raw_data)
        if raw_csv:
            process_cleaning_and_upload(raw_csv)
            print(f"\n✅ 全部完成！共获取 {len(raw_data)} 条有技能要求的岗位")
        else:
            print("❌ 保存原始数据失败")
    else:
        print("⚠️ 本次爬取未获得任何数据")


def run_scheduled():
    """定时运行"""
    INTERVAL_DAYS = 2
    SLEEP_SECONDS = INTERVAL_DAYS * 24 * 60 * 60
    print(f"⏰ 定时任务已启动：每隔 {INTERVAL_DAYS} 天自动爬取一次")
    print(f"📅 下次执行时间：{datetime.fromtimestamp(time.time() + SLEEP_SECONDS).strftime('%Y-%m-%d %H:%M:%S')}\n")

    while True:
        run_once()
        print(f"\n😴 任务完成，休眠 {INTERVAL_DAYS} 天...")
        print(f"🔔 下次执行时间：{datetime.fromtimestamp(time.time() + SLEEP_SECONDS).strftime('%Y-%m-%d %H:%M:%S')}")
        print("=" * 70 + "\n\n")
        time.sleep(SLEEP_SECONDS)


if __name__ == '__main__':
    print("=" * 60)
    print("🐱 智联招聘爬虫")
    print("=" * 60)
    print("请选择运行模式:")
    print("1. 单次运行（多城市爬取）")
    print("2. 定时任务（每2天运行一次）")
    choice = input("请输入选择 (1/2): ").strip()

    if choice == '2':
        run_scheduled()
    else:
        run_once()