import csv
import time
import os
import sys
import requests
import re
import glob
import webbrowser
import json
from DrissionPage import ChromiumPage, ChromiumOptions
from datetime import datetime
import shutil
import tempfile
import pandas as pd

# ================== 配置 ==================
SERVER_URL = "http://110.40.186.228:5000/upload"
# 存储位置：优先用后端传入的 BOSS_DATA_DIR（「爬取数据」按钮会把输出目录传进来），
# 命令行直接运行时回落到工程目录下的 fiveEi/新爬取
_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
_PROJECT_DIR = os.path.dirname(_SCRIPT_DIR)
DATA_DIR = os.environ.get("BOSS_DATA_DIR") or os.path.join(_PROJECT_DIR, "新爬取")
# 后端后台运行时不弹看板页面（BOSS_NO_BROWSER=1）
OPEN_BROWSER = os.environ.get("BOSS_NO_BROWSER") != "1"
HISTORY_FILE = os.path.join(DATA_DIR, "cleaned_boss_jobs_history.csv")
JSON_DATA_FILE = os.path.join(DATA_DIR, "job_skills_data.json")
HTML_VIEWER_FILE = os.path.join(DATA_DIR, "dashboard_viewer.html")

# ========== 爬虫配置 ==========
# 按公司行业逐类爬取：每个"公司行业"筛选项抓取的岗位条数（5~10之间）
PER_INDUSTRY = 8

# 职位列表排序方式（热度球）。地址栏可见，例如：热度/综合 sort=0、最新 sort=1001
JOB_SORT_PARAM = "sort=0"

# 如果从首页自动发现"热门职位分类"失败，用下面的关键词作为兜底分类频道
HOT_CHANNEL_QUERIES = [
    "AI", "Java", "Python", "前端开发", "后端开发", "全栈工程师",
    "算法工程师", "数据分析", "产品经理", "运营", "测试", "运维",
    "Go", "C++", "Node.js", "数据库", "云计算", "大数据",
]

JOB_KEYWORDS = [
    "AI全栈开发",
]

# 单个关键词/城市的目标爬取条数
TARGET_COUNT_PER_KEYWORD = 20
# 热门频道累计爬取目标条数
TOTAL_TARGET = 200

# ========== 多城市配置（新增）==========
# 城市代码对照表（BOSS直聘）
CITIES = [
    {"code": "101010100", "name": "北京"},
    {"code": "101020100", "name": "上海"},
    {"code": "101280100", "name": "广州"},
    {"code": "101280600", "name": "深圳"},
    {"code": "101210100", "name": "杭州"},
    {"code": "101190100", "name": "南京"},
    {"code": "101040100", "name": "重庆"},
    {"code": "101110100", "name": "西安"},
    {"code": "101190400", "name": "苏州"},
    {"code": "101120100", "name": "济南"},
    {"code": "101180100", "name": "郑州"},
    {"code": "101250100", "name": "长沙"},
    {"code": "101270100", "name": "成都"},
]

# 是否启用多城市爬取（True=多城市，False=单城市）
ENABLE_MULTI_CITY = True

# 如果只想爬部分城市，设置最大城市数量（0表示全部）
MAX_CITIES = 3  # 只爬前3个主要城市，控制总在1小时内

# ========== 用户指定爬取URL ==========
# 按公司行业过滤的列表页 URL（label, url），每行业抓 PER_INDUSTRY 条
INDUSTRY_URLS = [
    ("互联网行业(济南)", "https://www.zhipin.com/web/geek/jobs?city=101120200&industry=100020"),
    ("AI全栈开发(北京)", "https://www.zhipin.com/web/geek/jobs?city=101010100&industry=100001&query=AI%E5%85%A8%E6%A0%88%E5%BC%80%E5%8F%91"),
]

# 单个岗位详情页直爬 URL（label, url）
DETAIL_URLS = [
    ("AI岗位详情", "https://www.zhipin.com/job_detail/c5c011b21da7c6ce0nx53Ny7GVdT.html"),
]


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


# ================== 生成可视化页面 ==================
def generate_dynamic_dashboard(csv_file_path, output_html_path):
    """从清洗后的CSV文件读取数据，直接嵌入到HTML中"""

    if not os.path.exists(csv_file_path):
        print(f"⚠️ CSV文件不存在: {csv_file_path}")
        embedded_data = []
    else:
        try:
            df = pd.read_csv(csv_file_path, encoding='utf-8-sig')
            print(f"📖 从CSV读取到 {len(df)} 条数据")

            embedded_data = []
            for _, row in df.iterrows():
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

                has_skill = False
                for category in ['编程语言', '框架/库', '数据库/中间件', '工具/环境']:
                    val = str(row.get(category, ''))
                    if val and val != '' and val != 'nan' and val != '无':
                        record[category] = val
                        has_skill = True

                if has_skill and record:
                    embedded_data.append(record)

            print(f"📊 有技能数据的岗位: {len(embedded_data)} 条")

        except Exception as e:
            print(f"❌ 读取CSV失败: {e}")
            embedded_data = []

    data_json_str = json.dumps(embedded_data, ensure_ascii=False)

    html_template = f'''<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
    <title>岗位技能图谱可视化大屏 | 技能分析引擎</title>
    <script src="https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js"></script>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0-beta3/css/all.min.css">
    <style>
        * {{ margin: 0; padding: 0; box-sizing: border-box; }}
        body {{ background: radial-gradient(circle at 20% 30%, #0a0f1e, #03050b); font-family: 'Segoe UI', system-ui, sans-serif; color: #eef5ff; padding: 20px; min-height: 100vh; }}
        .bg-grid {{ position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-image: linear-gradient(rgba(0,255,255,0.05) 1px, transparent 1px), linear-gradient(90deg, rgba(0,255,255,0.05) 1px, transparent 1px); background-size: 40px 40px; pointer-events: none; z-index: 0; }}
        .dashboard {{ position: relative; z-index: 2; max-width: 1600px; margin: 0 auto; display: flex; flex-direction: column; gap: 20px; }}
        .card {{ background: rgba(12,20,35,0.65); backdrop-filter: blur(16px); border-radius: 32px; border: 1px solid rgba(0,255,255,0.25); box-shadow: 0 15px 35px rgba(0,0,0,0.3), inset 0 1px 0 rgba(255,255,255,0.05); transition: all 0.2s ease; overflow: hidden; display: flex; flex-direction: column; }}
        .card:hover {{ border-color: #0ff8; box-shadow: 0 0 18px rgba(0,200,255,0.2); }}
        .card-header {{ padding: 14px 20px; border-bottom: 1px solid rgba(0,255,255,0.2); font-weight: 600; background: rgba(0,20,40,0.4); }}
        .card-header i {{ margin-right: 8px; color: #2dd4bf; }}
        .card-content {{ flex: 1; padding: 16px; overflow-y: visible; }}
        .title-bar {{ backdrop-filter: blur(12px); background: rgba(10,20,40,0.55); border: 1px solid rgba(0,255,255,0.3); border-radius: 28px; padding: 12px 28px; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; box-shadow: 0 8px 20px rgba(0,0,0,0.3); }}
        .title-bar h1 {{ font-size: 1.6rem; font-weight: 600; background: linear-gradient(135deg, #A0F0FF, #4A9EFF); -webkit-background-clip: text; background-clip: text; color: transparent; letter-spacing: 1px; }}
        .badge {{ background: rgba(0,255,255,0.2); padding: 6px 14px; border-radius: 40px; font-size: 0.75rem; border-left: 2px solid #0ff; }}
        .refresh-btn {{ background: rgba(0,255,200,0.2); border: 1px solid #2dd4bf; border-radius: 40px; padding: 6px 16px; cursor: pointer; font-size: 0.75rem; transition: 0.2s; }}
        .refresh-btn:hover {{ background: rgba(0,255,200,0.4); }}
        .search-section {{ display: flex; flex-wrap: wrap; align-items: flex-end; gap: 16px; background: rgba(8,18,32,0.7); backdrop-filter: blur(12px); border-radius: 40px; padding: 16px 24px; border: 1px solid rgba(0,255,255,0.3); }}
        .search-group {{ flex: 1; min-width: 160px; }}
        .search-group label {{ font-size: 0.7rem; display: block; margin-bottom: 6px; letter-spacing: 1px; color: #9fd3ff; }}
        .search-input, .custom-select {{ width: 100%; background: rgba(0,10,25,0.8); border: 1px solid #2c5a7a; border-radius: 28px; padding: 10px 16px; color: white; font-size: 0.85rem; outline: none; font-family: inherit; }}
        .custom-select {{ appearance: none; -webkit-appearance: none; cursor: pointer; background-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='%2380ccff' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><polyline points='6 9 12 15 18 9'></polyline></svg>"); background-repeat: no-repeat; background-position: right 16px center; background-size: 14px; }}
        .custom-select option {{ background: #0a1428; color: #eef5ff; }}
        .search-btn, .reset-btn {{ background: linear-gradient(95deg, #0b5e7e, #00b4d8); border: none; border-radius: 40px; padding: 10px 28px; color: white; font-weight: bold; cursor: pointer; font-size: 0.85rem; margin-top: 20px; }}
        .reset-btn {{ background: rgba(50,70,100,0.8); border: 1px solid #3a7caa; }}
        .grid-five {{ display: grid; grid-template-columns: 1fr 2.2fr 1fr; grid-template-rows: auto 1fr auto auto; gap: 18px; }}
        .center-card {{ grid-column: 2 / 3; grid-row: 1 / 3; }}
        .top-card {{ grid-column: 1 / 4; grid-row: 1 / 2; }}
        .left-card {{ grid-column: 1 / 2; grid-row: 2 / 3; }}
        .right-card {{ grid-column: 3 / 4; grid-row: 2 / 3; }}
        .bottom-card {{ grid-column: 1 / 4; grid-row: 3 / 4; }}
        .skill-card {{ grid-column: 1 / 4; grid-row: 4 / 5; }}
        .kpi-row {{ display: flex; justify-content: space-around; flex-wrap: wrap; gap: 18px; }}
        .kpi-item {{ background: rgba(0,0,0,0.5); border-radius: 28px; padding: 12px 22px; text-align: center; min-width: 140px; border: 1px solid rgba(0,255,200,0.3); }}
        .kpi-number {{ font-size: 2.4rem; font-weight: 800; background: linear-gradient(135deg, #fff, #7ac7ff); -webkit-background-clip: text; background-clip: text; color: transparent; }}
        .kpi-label {{ font-size: 0.75rem; text-transform: uppercase; letter-spacing: 2px; color: #9ac8ff; }}
        .chart-container {{ width: 100%; height: 300px; }}
        .center-chart {{ height: 380px; }}
        .graph-container {{ height: 420px; width: 100%; background: rgba(0,0,0,0.2); border-radius: 24px; margin-top: 8px; }}
        .job-list-wrapper {{ position: relative; overflow: hidden; border-radius: 20px; margin-top: 12px; }}
        .job-list-scroll {{ display: flex; flex-direction: column; gap: 8px; animation: scrollUpJob 35s linear infinite; }}
        .job-list-scroll:hover {{ animation-play-state: paused; }}
        @keyframes scrollUpJob {{ 0% {{ transform: translateY(0); }} 100% {{ transform: translateY(-50%); }} }}
        .job-item {{ background: rgba(0,20,40,0.5); border-radius: 20px; padding: 12px 16px; border-left: 3px solid #2dd4bf; display: flex; flex-wrap: wrap; align-items: center; flex-shrink: 0; }}
        .job-info {{ flex: 1; }}
        .job-title {{ font-weight: 600; font-size: 0.9rem; }}
        .job-meta {{ font-size: 0.7rem; color: #aad0ff; margin-top: 6px; }}
        .add-form {{ display: flex; flex-wrap: wrap; gap: 12px; align-items: flex-end; }}
        .form-group {{ flex: 1; min-width: 120px; }}
        .form-group label {{ font-size: 0.7rem; display: block; margin-bottom: 4px; color: #9fd3ff; }}
        .form-control {{ background: rgba(0,10,25,0.8); border: 1px solid #2c5a7a; border-radius: 20px; padding: 8px 12px; width: 100%; color: white; font-size: 0.85rem; }}
        .btn-add {{ background: linear-gradient(95deg, #0b5e7e, #00b4d8); border: none; border-radius: 40px; padding: 8px 20px; color: white; font-weight: bold; cursor: pointer; margin-top: 18px; font-size: 0.8rem; }}
        .skill-scroll-wrapper {{ position: relative; overflow: hidden; border-radius: 24px; }}
        .skill-list-scroll {{ display: flex; flex-direction: column; gap: 12px; animation: scrollUpSkill 40s linear infinite; padding: 4px; }}
        .skill-list-scroll:hover {{ animation-play-state: paused; }}
        @keyframes scrollUpSkill {{ 0% {{ transform: translateY(0); }} 100% {{ transform: translateY(-50%); }} }}
        .skill-job-card {{ background: rgba(0,30,50,0.6); border-radius: 24px; padding: 12px 18px; border: 1px solid rgba(0,255,200,0.2); flex-shrink: 0; }}
        .skill-job-card:hover {{ background: rgba(0,70,100,0.8); border-color: #0ff; transform: scale(1.01); }}
        .skill-job-name {{ font-size: 1rem; font-weight: 700; color: #b2f0ff; margin-bottom: 8px; display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }}
        .skill-badge-group {{ display: flex; flex-wrap: wrap; gap: 8px; margin-top: 6px; }}
        .skill-badge {{ background: #0a3b4e; padding: 4px 12px; border-radius: 40px; font-size: 0.75rem; color: #b9f3ff; border-left: 2px solid #2dd4bf; }}
        .sub-header {{ font-size: 0.7rem; color: #8bbefe; margin-top: 4px; }}
        .stats-bar {{ background: rgba(0,20,40,0.5); border-radius: 20px; padding: 8px 16px; margin-bottom: 12px; font-size: 0.75rem; color: #7effe0; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }}
        .pause-control {{ background: rgba(0,255,200,0.15); border: 1px solid #2dd4bf; border-radius: 20px; padding: 4px 12px; font-size: 0.7rem; cursor: pointer; }}
        .empty-state {{ text-align: center; padding: 40px; color: #6c8fb0; }}
        .empty-state i {{ font-size: 3rem; margin-bottom: 16px; opacity: 0.5; }}
        @media (max-width: 1000px) {{ .kpi-number {{ font-size: 1.7rem; }} .search-section {{ flex-direction: column; align-items: stretch; }} .grid-five {{ display: flex; flex-direction: column; }} .chart-container {{ height: 280px; }} }}
    </style>
</head>
<body>
<div class="bg-grid"></div>
<div class="dashboard">
    <div class="title-bar">
        <h1><i class="fas fa-microchip"></i> NEURO·TALENT 智联观察站 | 技能图谱引擎</h1>
        <div style="display: flex; gap: 12px; align-items: center;">
            <div class="badge" id="dataStatus"><i class="fas fa-check-circle"></i> 数据已嵌入 | {len(embedded_data)}条</div>
            <button class="refresh-btn" onclick="location.reload();"><i class="fas fa-sync-alt"></i> 刷新页面</button>
        </div>
    </div>

    <div class="search-section">
        <div class="search-group"><label><i class="fas fa-search"></i> 职位 / 关键词</label><input type="text" id="searchKeywordInput" class="search-input" placeholder="输入职位名称、技术栈..."></div>
        <div class="search-group"><label><i class="fas fa-tag"></i> 岗位类别</label><select id="searchCategorySelect" class="custom-select"><option value="">全部类别</option></select></div>
        <div class="search-group"><label><i class="fas fa-code"></i> 编程语言</label><select id="searchLanguageSelect" class="custom-select"><option value="">全部语言</option></select></div>
        <div><button id="searchBtn" class="search-btn"><i class="fas fa-filter"></i> 智能筛选</button><button id="resetBtn" class="reset-btn"><i class="fas fa-undo-alt"></i> 重置</button></div>
    </div>

    <div class="grid-five">
        <div class="card top-card"><div class="card-header"><i class="fas fa-chart-simple"></i> 核心指标引擎</div><div class="card-content" id="kpiContainer"></div></div>
        <div class="card left-card"><div class="card-header"><i class="fas fa-briefcase"></i> 岗位关键词技能分布</div><div class="card-content"><div id="chartJobCategory" class="chart-container"></div></div></div>
        <div class="card center-card"><div class="card-header"><i class="fas fa-chart-line"></i> 技能类别分布 | 各岗位技能需求对比</div><div class="card-content"><div id="chartSkillCategory" class="chart-container center-chart"></div></div></div>
        <div class="card right-card"><div class="card-header"><i class="fab fa-python"></i> 编程语言热度榜</div><div class="card-content"><div id="chartLanguage" class="chart-container"></div></div></div>
        <div class="card bottom-card">
            <div class="card-header"><i class="fas fa-plus-circle"></i> 自由添加职位 · 动态更新</div>
            <div class="card-content">
                <div class="add-form">
                    <div class="form-group"><label>岗位关键词</label><input type="text" id="newKeyword" class="form-control" placeholder="例: AI" value="AI"></div>
                    <div class="form-group"><label>职位名称</label><input type="text" id="newTitle" class="form-control" placeholder="AI算法工程师" value="大模型架构师"></div>
                    <div class="form-group"><label>编程语言</label><input type="text" id="newLang" class="form-control" placeholder="Python" value="Python"></div>
                    <div class="form-group"><label>框架/库</label><input type="text" id="newFramework" class="form-control" placeholder="PyTorch" value="LangChain"></div>
                    <button class="btn-add" id="addJobBtn"><i class="fas fa-satellite-dish"></i> 注入数据</button>
                </div>
                <div class="stats-bar" id="jobStatsBar"><span><i class="fas fa-list-ul"></i> 当前筛选职位列表</span><span class="pause-control" id="jobPauseToggle"><i class="fas fa-pause"></i> 悬停暂停</span></div>
                <div id="jobScrollWrapper" class="job-list-wrapper" style="height: 280px; overflow: hidden;"><div id="jobListContainer" class="job-list-scroll"></div></div>
            </div>
        </div>
        <div class="card skill-card">
            <div class="card-header"><i class="fas fa-project-diagram"></i> 技能知识图谱 · 岗位关联技能全景</div>
            <div class="card-content">
                <div style="display: flex; flex-direction: column; gap: 20px;">
                    <div><div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; flex-wrap: wrap; gap: 8px;"><span><i class="fas fa-share-alt"></i> 岗位⇄技能关系图谱</span><span style="font-size: 0.7rem; background: #0a2a3a; padding: 4px 12px; border-radius: 20px;">🔵岗位 | 🟢技能</span></div><div id="skillGraph" class="graph-container"></div></div>
                    <div><div class="stats-bar" id="skillStatsBar"><span><i class="fas fa-clipboard-list"></i> 岗位所需技能清单（自动滚动）</span><span class="pause-control" id="skillPauseToggle"><i class="fas fa-pause"></i> 悬停暂停</span></div><div id="skillScrollWrapper" class="skill-scroll-wrapper" style="height: 480px; overflow: hidden;"><div id="skillDetailList" class="skill-list-scroll"></div></div></div>
                </div>
            </div>
        </div>
    </div>
</div>

<script>
    const EMBEDDED_DATA = {data_json_str};
    let fullData = EMBEDDED_DATA;
    let currentDisplayData = [...fullData];
    let jobPlaying = true;
    let skillPlaying = true;
    let charts = {{}};

    function extractSkillsFromJob(job) {{
        let skills = new Set();
        ["编程语言", "框架/库", "数据库/中间件", "工具/环境"].forEach(field => {{
            let val = job[field];
            if (val && val !== '无' && val.trim()) {{
                val.split(/[,，、;；|]+/).forEach(v => {{ let skill = v.trim(); if (skill && skill !== '无' && skill.length > 0) skills.add(skill); }});
            }}
        }});
        if (skills.size === 0) skills.add("无明确技能");
        return Array.from(skills);
    }}

    function buildGraphFromJobs(jobsData) {{
        let nodesMap = new Map(), links = [];
        if (!jobsData.length) return {{ nodes: [], links: [] }};
        jobsData.forEach((job, idx) => {{
            let jobId = `job_${{idx}}`;
            let jobName = job["职位名称"] || `${{job["岗位关键词"]}}_岗位`;
            if (jobName.length > 22) jobName = jobName.slice(0,20)+'..';
            nodesMap.set(jobId, {{ name: jobName, category: 0, symbolSize: 28, itemStyle: {{ color: '#3b82f6' }} }});
            extractSkillsFromJob(job).forEach(skill => {{
                let skillId = `skill_${{skill.replace(/\\s/g, '')}}`;
                if (!nodesMap.has(skillId)) nodesMap.set(skillId, {{ name: skill, category: 1, symbolSize: 20, itemStyle: {{ color: '#2dd4bf' }} }});
                links.push({{ source: jobId, target: skillId, lineStyle: {{ color: '#aaccff', curveness: 0.2, width: 1.5 }} }});
            }});
        }});
        return {{ nodes: Array.from(nodesMap.values()), links }};
    }}

    function renderSkillGraph() {{
        let chartDom = document.getElementById('skillGraph');
        if (!chartDom) return;
        if (charts.skillGraph) charts.skillGraph.dispose();
        charts.skillGraph = echarts.init(chartDom);
        let {{ nodes, links }} = buildGraphFromJobs(currentDisplayData);
        if (nodes.length === 0) {{
            charts.skillGraph.setOption({{ title: {{ show: true, text: "暂无数据", left: 'center', top: 'center', textStyle: {{ color: '#aaa' }} }}, series: [] }});
            return;
        }}
        charts.skillGraph.setOption({{
            tooltip: {{ trigger: 'item', formatter: '{{b}}' }},
            series: [{{ type: 'graph', layout: 'force', force: {{ repulsion: 500, edgeLength: 130, gravity: 0.1, friction: 0.2 }}, roam: true, draggable: true, data: nodes, links: links, categories: [{{ name: '岗位' }}, {{ name: '技能' }}], label: {{ show: true, position: 'right', fontSize: 10, color: '#eef5ff', offset: [5,0] }}, emphasis: {{ focus: 'adjacency' }}, lineStyle: {{ color: '#5aa9dd', curveness: 0.2, opacity: 0.7, width: 1.5 }}, edgeSymbol: ['none', 'arrow'], edgeSymbolSize: [0, 6], roamZoom: true, itemStyle: {{ borderColor: '#fff', borderWidth: 1 }} }}]
        }});
        charts.skillGraph.resize();
    }}

    function renderJobList() {{
        let container = document.getElementById('jobListContainer');
        if (!currentDisplayData.length) {{ container.innerHTML = '<div class="empty-state"><i class="fas fa-briefcase"></i><p>暂无职位数据</p></div>'; return; }}
        let html = '';
        currentDisplayData.forEach(job => {{
            let langHtml = job["编程语言"] ? `语言:${{escapeHtml(job["编程语言"])}}` : '';
            let cityHtml = job["城市"] ? `城市:${{escapeHtml(job["城市"])}}` : '';
            html += `<div class="job-item"><div class="job-info"><div class="job-title">${{escapeHtml(job["职位名称"] || "未命名")}} <span style="font-size:0.7rem;">${{escapeHtml(job["岗位关键词"] || '')}}</span></div><div class="job-meta">${{langHtml}} ${{cityHtml}}</div></div></div>`;
        }});
        container.innerHTML = html + html;
        let scrollElem = document.querySelector('.job-list-scroll');
        if (scrollElem) {{ scrollElem.style.animation = 'none'; scrollElem.offsetHeight; scrollElem.style.animation = 'scrollUpJob 35s linear infinite'; }}
        document.getElementById('jobStatsBar').innerHTML = `<span><i class="fas fa-list-ul"></i> 当前筛选职位列表 (共 ${{currentDisplayData.length}} 条)</span><span class="pause-control" id="jobPauseToggle"><i class="fas fa-pause"></i> 悬停暂停</span>`;
        document.getElementById('jobPauseToggle')?.addEventListener('click', () => {{
            let se = document.querySelector('.job-list-scroll');
            if (se) {{ jobPlaying ? se.style.animationPlayState = 'paused' : se.style.animationPlayState = 'running'; jobPlaying = !jobPlaying; }}
        }});
    }}

    function renderSkillDetailList() {{
        let container = document.getElementById('skillDetailList');
        if (!currentDisplayData.length) {{ container.innerHTML = '<div class="empty-state"><i class="fas fa-code"></i><p>暂无技能数据</p></div>'; return; }}
        let html = '';
        currentDisplayData.forEach(job => {{
            let title = job["职位名称"] || job["岗位关键词"] || "未命名岗位";
            let skills = extractSkillsFromJob(job);
            let skillsHtml = skills.length ? skills.map(s => `<span class="skill-badge">${{escapeHtml(s)}}</span>`).join('') : '<span class="skill-badge">无明确技能</span>';
            let cityInfo = job["城市"] ? `<span style="font-size:0.65rem; background:#0b4460; padding:2px 8px; border-radius:16px; margin-left:8px;">🏙️ ${{escapeHtml(job["城市"])}}</span>` : '';
            html += `<div class="skill-job-card"><div class="skill-job-name"><i class="fas fa-briefcase"></i> ${{escapeHtml(title)}}<span style="font-size:0.7rem; background:#0b4460; padding:2px 8px; border-radius:16px;">${{escapeHtml(job["岗位关键词"] || "其他")}}</span>${{cityInfo}}</div><div class="sub-header"><i class="fas fa-code"></i> 核心技术栈：</div><div class="skill-badge-group">${{skillsHtml}}</div></div>`;
        }});
        container.innerHTML = html + html;
        let scrollElem = document.querySelector('.skill-list-scroll');
        if (scrollElem) {{ scrollElem.style.animation = 'none'; scrollElem.offsetHeight; scrollElem.style.animation = 'scrollUpSkill 40s linear infinite'; }}
        document.getElementById('skillStatsBar').innerHTML = `<span><i class="fas fa-clipboard-list"></i> 岗位所需技能清单 (共 ${{currentDisplayData.length}} 条) | 自动滚动</span><span class="pause-control" id="skillPauseToggle"><i class="fas fa-pause"></i> 悬停暂停</span>`;
        document.getElementById('skillPauseToggle')?.addEventListener('click', () => {{
            let se = document.querySelector('.skill-list-scroll');
            if (se) {{ skillPlaying ? se.style.animationPlayState = 'paused' : se.style.animationPlayState = 'running'; skillPlaying = !skillPlaying; }}
        }});
    }}

    function refreshChartsAndKPI() {{
        if (currentDisplayData.length === 0) {{
            document.getElementById('kpiContainer').innerHTML = '<div class="empty-state"><i class="fas fa-chart-line"></i><p>暂无数据</p></div>';
            if (charts.jobCategory) charts.jobCategory.clear();
            if (charts.skillCategory) charts.skillCategory.clear();
            if (charts.language) charts.language.clear();
            return;
        }}

        let catCount = new Map();
        currentDisplayData.forEach(job => {{ let cat = job["岗位关键词"] || "其他"; if (cat.trim()) catCount.set(cat, (catCount.get(cat) || 0) + 1); }});
        let sortedCat = Array.from(catCount.entries()).sort((a,b)=>b[1]-a[1]).slice(0, 12);
        if (!charts.jobCategory) charts.jobCategory = echarts.init(document.getElementById('chartJobCategory'));
        charts.jobCategory.setOption({{
            tooltip: {{ trigger: 'axis', axisPointer: {{ type: 'shadow' }} }}, grid: {{ containLabel: true, left: '15%', right: '5%', top: 20, bottom: '22%' }},
            xAxis: {{ type: 'category', data: sortedCat.map(c=>c[0]), axisLabel: {{ rotate: 45, interval: 0, fontSize: 11, color: '#e0f0ff', margin: 15, formatter: function(v) {{ if (v.length > 4) {{ let m = Math.ceil(v.length / 2); return v.substring(0, m) + '\\n' + v.substring(m); }} return v; }} }} }},
            yAxis: {{ type: 'value', name: '岗位数量' }}, series: [{{ type: 'bar', data: sortedCat.map(c=>c[1]), itemStyle: {{ borderRadius: [6,6,0,0], color: '#20dbc0' }} }}]
        }});

        let skillCategoryData = {{ '编程语言': 0, '框架/库': 0, '数据库/中间件': 0, '工具/环境': 0 }};
        currentDisplayData.forEach(job => {{
            if (job["编程语言"] && job["编程语言"] !== '无') skillCategoryData['编程语言']++;
            if (job["框架/库"] && job["框架/库"] !== '无') skillCategoryData['框架/库']++;
            if (job["数据库/中间件"] && job["数据库/中间件"] !== '无') skillCategoryData['数据库/中间件']++;
            if (job["工具/环境"] && job["工具/环境"] !== '无') skillCategoryData['工具/环境']++;
        }});
        if (!charts.skillCategory) charts.skillCategory = echarts.init(document.getElementById('chartSkillCategory'));
        charts.skillCategory.setOption({{
            tooltip: {{ trigger: 'axis' }}, xAxis: {{ type: 'category', data: Object.keys(skillCategoryData), axisLabel: {{ rotate: 20, fontSize: 11, color: '#cceeff' }} }},
            yAxis: {{ type: 'value', name: '包含该技能的岗位数' }}, series: [{{ type: 'bar', data: Object.values(skillCategoryData), itemStyle: {{ color: '#5f9eff', borderRadius: [8,8,0,0] }} }}]
        }});

        let langMap = new Map();
        currentDisplayData.forEach(job => {{
            let lang = job["编程语言"];
            if (lang && lang !== '无') {{ lang.split(/[,，、]+/).forEach(l => {{ let lc = l.trim(); if(lc && lc !== '无') langMap.set(lc, (langMap.get(lc)||0)+1); }}); }}
        }});
        let topLang = Array.from(langMap.entries()).sort((a,b)=>b[1]-a[1]).slice(0,6);
        if (!charts.language) charts.language = echarts.init(document.getElementById('chartLanguage'));
        charts.language.setOption({{
            tooltip: {{ trigger: 'axis' }}, xAxis: {{ type: 'category', data: topLang.map(l=>l[0]), axisLabel: {{ rotate: 25, fontSize: 11, color: '#b6d0ff' }} }},
            yAxis: {{ type: 'value', name: '出现次数' }}, series: [{{ type: 'bar', data: topLang.map(l=>l[1]), itemStyle: {{ color: '#f5a623', borderRadius: [8,8,0,0] }} }}]
        }});

        let totalJobs = currentDisplayData.length;
        document.getElementById('kpiContainer').innerHTML = `<div class="kpi-row"><div class="kpi-item"><div class="kpi-number">${{totalJobs}}</div><div class="kpi-label">当前岗位数</div></div><div class="kpi-item"><div class="kpi-number">${{catCount.size}}</div><div class="kpi-label">技术类别</div></div><div class="kpi-item"><div class="kpi-number">${{topLang.length}}</div><div class="kpi-label">活跃语言</div></div></div>`;
    }}

    function refreshAll() {{ refreshChartsAndKPI(); renderJobList(); renderSkillGraph(); renderSkillDetailList(); }}
    function filterJobs() {{
        let keyword = document.getElementById('searchKeywordInput').value.trim().toLowerCase();
        let category = document.getElementById('searchCategorySelect').value;
        let language = document.getElementById('searchLanguageSelect').value;
        let filtered = [...fullData];
        if (keyword) filtered = filtered.filter(job => (job["职位名称"]||'').toLowerCase().includes(keyword) || (job["岗位关键词"]||'').toLowerCase().includes(keyword));
        if (category) filtered = filtered.filter(job => (job["岗位关键词"]||'') === category);
        if (language) filtered = filtered.filter(job => (job["编程语言"]||'').toLowerCase().includes(language.toLowerCase()));
        currentDisplayData = filtered;
        refreshAll();
    }}
    function resetFilters() {{
        document.getElementById('searchKeywordInput').value = '';
        document.getElementById('searchCategorySelect').value = '';
        document.getElementById('searchLanguageSelect').value = '';
        currentDisplayData = [...fullData];
        refreshAll();
    }}
    function populateFilterOptions() {{
        let catSet = new Set(), langSet = new Set();
        fullData.forEach(job => {{ let c = job["岗位关键词"]; if(c && c.trim()) catSet.add(c.trim()); let l = job["编程语言"]; if(l && l !== '无' && l) {{ l.split(/[,，、]+/).forEach(lc => {{ if(lc && lc.trim()) langSet.add(lc.trim()); }}); }} }});
        let catSel = document.getElementById('searchCategorySelect'); catSel.innerHTML = '<option value="">全部类别</option>'; Array.from(catSet).sort().forEach(c => catSel.innerHTML += `<option value="${{escapeHtml(c)}}">${{c}}</option>`);
        let langSel = document.getElementById('searchLanguageSelect'); langSel.innerHTML = '<option value="">全部语言</option>'; Array.from(langSet).sort().forEach(l => langSel.innerHTML += `<option value="${{escapeHtml(l)}}">${{l}}</option>`);
    }}
    function addCustomJob() {{
        let keyword = document.getElementById('newKeyword').value.trim() || "AI";
        let title = document.getElementById('newTitle').value.trim() || "新职位";
        let lang = document.getElementById('newLang').value.trim() || "";
        let framework = document.getElementById('newFramework').value.trim() || "";
        fullData.push({{ "岗位关键词": keyword, "职位名称": title, "编程语言": lang, "框架/库": framework }});
        populateFilterOptions();
        resetFilters();
    }}
    function escapeHtml(str) {{ if(!str) return ''; return str.replace(/[&<>]/g, m => ({{'&':'&amp;','<':'&lt;','>':'&gt;'}}[m])); }}
    document.getElementById('searchBtn').addEventListener('click', filterJobs);
    document.getElementById('resetBtn').addEventListener('click', resetFilters);
    document.getElementById('addJobBtn').addEventListener('click', addCustomJob);
    populateFilterOptions();
    refreshAll();
</script>
</body>
</html>'''

    try:
        with open(output_html_path, 'w', encoding='utf-8') as f:
            f.write(html_template)
        print(f"✅ 可视化页面已生成: {output_html_path}")
        return True
    except Exception as e:
        print(f"❌ 生成可视化页面失败: {e}")
        return False


# ================== 爬虫函数（支持城市参数）==================
def boos_login(page):
    print("打开BOSS直聘...")
    page.get('https://www.zhipin.com')
    page.wait(3)
    print("已登录，继续")


def fetch_jobs_for_keyword_in_city(keyword, city_code, city_name):
    """在指定城市爬取职位"""
    co = ChromiumOptions().headless(False)
    co.set_argument('--disable-blink-features=AutomationControlled')
    co.set_argument('--start-maximized')
    try:
        co.set_browser_path(r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe')
    except:
        co.set_browser_path(r'C:\Program Files\Microsoft\Edge\Application\msedge.exe')
    page = ChromiumPage(co)
    boos_login(page)

    print(f"\n🔍 开始爬取关键词：【{keyword}】在【{city_name}】的职位（目标{TARGET_COUNT_PER_KEYWORD}条）")

    # 构建带城市参数和排序方式的URL
    url = f"https://www.zhipin.com/web/geek/job?city={city_code}&query={keyword}&{JOB_SORT_PARAM}"
    print(f"🌐 URL: {url}")
    page.get(url)
    page.wait(5)

    # ========== 持续滚动直到获得足够多的链接 ==========
    print(f"\n===== 正在加载【{city_name}】职位列表 =====")
    last_count = 0
    no_new_count = 0
    scroll_count = 0
    max_scrolls = 300

    while scroll_count < max_scrolls:
        page.scroll.to_bottom()
        page.wait(2)

        current_cards = page.eles('xpath://li[contains(@class,"job-card")]', timeout=3)
        current_count = len(current_cards) if current_cards else 0

        print(f"📊 滚动第 {scroll_count + 1} 次，当前已加载 {current_count} 个职位卡片...")

        if current_count == last_count:
            no_new_count += 1
            if no_new_count >= 3:
                print(f"ℹ️ 连续3次没有新内容，停止滚动。")
                break
        else:
            no_new_count = 0
            last_count = current_count

        scroll_count += 1

        if current_count >= TARGET_COUNT_PER_KEYWORD * 3:
            print(f"✅ 已加载 {current_count} 个卡片，超过目标数量的3倍，停止滚动。")
            break

    print(f"📊 最终加载了 {last_count} 个职位卡片")
    page.wait(2)

    print("\n===== 开始提取职位详情链接 =====")
    links = []
    seen_links = set()

    selectors = [
        'xpath://a[contains(@href, "/job_detail/")]',
        'css:.job-card a',
        'css:.job-title a'
    ]

    all_job_links = []
    for selector in selectors:
        try:
            elements = page.eles(selector, timeout=2)
            if elements:
                all_job_links = elements
                print(f"🔗 使用选择器 '{selector}' 找到 {len(elements)} 个链接元素")
                break
        except:
            continue

    if not all_job_links:
        all_job_links = page.eles('xpath://a[contains(@href, "/job_detail/")]', timeout=3)

    print(f"🔗 全局共找到 {len(all_job_links)} 个职位链接元素")

    for a in all_job_links:
        href = a.attr('href') if hasattr(a, 'attr') else a.get_attribute('href')
        if not href:
            continue
        if href.startswith('//'):
            full_link = 'https:' + href
        elif href.startswith('/'):
            full_link = 'https://www.zhipin.com' + href
        else:
            full_link = href
        if '/job_detail/' in full_link and full_link not in seen_links:
            seen_links.add(full_link)
            links.append(full_link)

    print(f"✅ 共提取到 {len(links)} 个唯一职位链接")

    if len(links) < TARGET_COUNT_PER_KEYWORD:
        print(f"⚠️ 警告：只找到 {len(links)} 个链接，少于目标 {TARGET_COUNT_PER_KEYWORD} 条")

    if not links:
        print("⚠️ 链接提取失败")
        page.quit()
        return []

    result = []
    skipped_count = 0
    visited_count = 0

    for i, link in enumerate(links):
        if len(result) >= TARGET_COUNT_PER_KEYWORD:
            print(f"✅ 已达到目标数量 {TARGET_COUNT_PER_KEYWORD} 条，停止爬取【{keyword}】在【{city_name}】")
            break

        if visited_count >= MAX_DETAIL_VISITS:
            print(f"⏹️ 已访问 {MAX_DETAIL_VISITS} 个详情页超过上限，停止【{city_name}】")
            break

        visited_count += 1

        if not link:
            continue
        try:
            print(f"\n🔍 【{keyword}】【{city_name}】进入第 {i + 1}/{len(links)} 个职位详情页...")
            page.get(link)
            page.wait(2)

            # 职位名称
            job_name = "未知职位"
            for selector in ['.job-title h1', 'h1.job-title', '.job-banner .name', '.job-header .job-title']:
                ele = page.ele('css:' + selector, timeout=1.5)
                if ele and ele.text.strip():
                    job_name = ele.text.strip()
                    break

            if not job_name:
                job_name = "未知职位"

            # 职位描述
            job_desc = ""
            desc_selectors = [
                '.job-sec-text',
                '.job-description',
                '.description',
                '.detail-content',
                '.job-detail-section'
            ]

            for selector in desc_selectors:
                desc_ele = page.ele('css:' + selector, timeout=1.5)
                if desc_ele:
                    job_desc = desc_ele.text.replace('\n', ' ').strip()
                    if len(job_desc) > 50:
                        break

            if len(job_desc) < 50:
                desc_parts = []
                for kw in ['岗位职责', '工作职责', '职位描述', '任职要求', '岗位要求', '职位要求']:
                    try:
                        section = page.ele(f'xpath://div[contains(text(), "{kw}")]/following-sibling::div[1]',
                                           timeout=1)
                        if section:
                            text = section.text.replace('\n', ' ').strip()
                            if text and len(text) > 10:
                                desc_parts.append(f"{kw}：{text}")
                    except:
                        continue
                job_desc = "；".join(desc_parts) if desc_parts else job_desc

            # 检测是否有技术含量
            has_skill, matched_skills = has_technical_skills(job_desc, job_name)

            if not has_skill:
                skipped_count += 1
                print(f"⏭️ 跳过（无技能要求）: {job_name} | 已跳过{skipped_count}个")
                continue

            print(f"✅ 保留: {job_name} | 匹配技能: {matched_skills[:3]}...")

            # 记录城市信息
            record = [keyword, city_name, job_name, job_desc if job_desc else ""]
            result.append(record)
            print(f"📝 已保存 {len(result)}/{TARGET_COUNT_PER_KEYWORD} 条")

        except Exception as e:
            print(f"❌ 处理失败: {e}")

        time.sleep(0.5)

    page.quit()
    print(f"\n📊 【{keyword}】在【{city_name}】爬取完成: 成功 {len(result)} 条, 跳过 {skipped_count} 条无技能岗位")
    return result


def fetch_jobs_from_hot_channels(total_target=TOTAL_TARGET):
    """从BOSS首页的"热门职位分类"频道抓取岗位，累计抓满 total_target 条即止（不限时）"""
    co = ChromiumOptions().headless(False)
    co.set_argument('--disable-blink-features=AutomationControlled')
    co.set_argument('--start-maximized')
    try:
        co.set_browser_path(r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe')
    except:
        co.set_browser_path(r'C:\Program Files\Microsoft\Edge\Application\msedge.exe')
    page = ChromiumPage(co)
    boos_login(page)

    result = []
    skipped_count = 0

    # ---- 1. 打开首页，自动发现"热门职位分类"频道链接 ----
    print(f"\n🌍 打开BOSS首页，探测热门职位分类频道...")
    page.get("https://www.zhipin.com/web/geek/")
    page.wait(5)

    channel_urls = []
    seen = set()
    hot_selectors = [
        'css:.channel-list a',
        'css:.hot-job-list a',
        'css:.hot-position-list a',
        'xpath://div[contains(@class,"hot")]//a[contains(@href,"geek/job")]',
        'css:a[href*="web/geek/job?query="]',
    ]
    for sel in hot_selectors:
        try:
            elems = page.eles(sel, timeout=2)
            if elems:
                for a in elems:
                    try:
                        href = a.attr('href') or a.get_attribute('href')
                    except:
                        href = a.get_attribute('href') or a.attr('href')
                    if href and href.startswith('/'):
                        href = 'https://www.zhipin.com' + href
                    if href and 'geek/job' in href and href not in seen:
                        seen.add(href)
                        channel_urls.append(href)
                if channel_urls:
                    print(f"🔗 选择器 '{sel}' 发现 {len(channel_urls)} 个热门频道链接")
                    break
        except:
            continue

    # 兜底：自动发现失败时，用预置分类关键词构造频道URL（带上城市参数，避免返回全国散乱结果）
    if not channel_urls:
        print("⚠️ 未从首页自动发现热门频道，使用预置分类关键词兜底（加城市参数）")
        channel_urls = [f"https://www.zhipin.com/web/geek/job?query={q}&city=101010100" for q in HOT_CHANNEL_QUERIES]

    print(f"🏷️ 共 {len(channel_urls)} 个热门职位分类频道，目标累计 {total_target} 条")

    # ---- 2. 逐个频道抓取，累计达标即止 ----
    for idx, channel_url in enumerate(channel_urls):
        if len(result) >= total_target:
            break

        print(f"\n{'=' * 50}")
        print(f"🚀 进入热门频道 {idx + 1}/{len(channel_urls)}: {channel_url}")
        print(f"{'=' * 50}")

        url = channel_url + (f'&{JOB_SORT_PARAM}' if '?' in channel_url else f'?{JOB_SORT_PARAM}')
        page.get(url)
        page.wait(5)

        # 加载列表
        print(f"===== 正在加载职位列表（热度排序）=====")
        last_count = 0
        no_new_count = 0
        for scroll_idx in range(60):
            page.scroll.to_bottom()
            page.wait(2)
            cards = page.eles('xpath://li[contains(@class,"job-card")]', timeout=3)
            current_count = len(cards) if cards else 0
            if current_count == last_count:
                no_new_count += 1
                if no_new_count >= 3:
                    print(f"ℹ️ 列表无可加载内容，停止滚动")
                    break
            else:
                no_new_count = 0
                last_count = current_count
            if current_count >= 60:
                break

        # 提取详情链接
        detail_links = []
        all_elems = page.eles('xpath://a[contains(@href, "/job_detail/")]', timeout=3)
        for a in all_elems:
            try:
                href = a.attr('href') or a.get_attribute('href')
            except:
                href = a.get_attribute('href') or a.attr('href')
            if not href:
                continue
            if href.startswith('//'):
                href = 'https:' + href
            elif href.startswith('/'):
                href = 'https://www.zhipin.com' + href
            if '/job_detail/' in href and href not in detail_links:
                detail_links.append(href)

        print(f"🔗 本频道提取到 {len(detail_links)} 个职位链接")

        # 访问详情页
        for j, link in enumerate(detail_links):
            if len(result) >= total_target:
                print(f"✅ 已累计 {total_target} 条，停止爬取")
                break
            if not link:
                continue
            try:
                print(f"\n🔍 进入第 {j + 1}/{len(detail_links)} 个详情页（累计已保存 {len(result)} 条）...")
                page.get(link)
                page.wait(2)

                job_name = "未知职位"
                for s in ['css:.job-title h1', 'css:h1.job-title', 'css:.job-banner .name']:
                    el = page.ele(s, timeout=1.5)
                    if el and el.text.strip():
                        job_name = el.text.strip()
                        break

                job_desc = ""
                for s in ['css:.job-sec-text', 'css:.job-description', 'css:.description', 'css:.detail-content']:
                    d = page.ele(s, timeout=1.5)
                    if d:
                        job_desc = d.text.replace('\n', ' ').strip()
                        if len(job_desc) > 50:
                            break

                has_skill, matched = has_technical_skills(job_desc, job_name)
                if not has_skill:
                    skipped_count += 1
                    print(f"⏭️ 跳过（无技能要求）: {job_name} | 已跳过{skipped_count}个")
                    continue

                print(f"✅ 保留: {job_name} | 匹配技能: {matched[:3]}...")
                result.append(["热门职位", "首页", job_name, job_desc if job_desc else ""])
                print(f"📝 已保存 {len(result)}/{total_target} 条")
            except Exception as e:
                print(f"❌ 处理失败: {e}")
            time.sleep(0.5)

    page.quit()
    print(f"\n📊 热门职位频道爬取完成: 成功 {len(result)} 条, 跳过 {skipped_count} 条无技能岗位")
    return result


def _extract_job_detail(page, link):
    """打开单个岗位详情页并提取职位名称+描述"""
    page.get(link)
    page.wait(2)
    job_name = "未知职位"
    for s in ['css:.job-title h1', 'css:h1.job-title', 'css:.job-banner .name']:
        el = page.ele(s, timeout=1.5)
        if el and el.text.strip():
            job_name = el.text.strip()
            break
    job_desc = ""
    for s in ['css:.job-sec-text', 'css:.job-description', 'css:.description', 'css:.detail-content']:
        d = page.ele(s, timeout=1.5)
        if d:
            job_desc = d.text.replace('\n', ' ').strip()
            if len(job_desc) > 50:
                break
    return job_name, job_desc


def crawl_industry_urls(per_industry=8):
    """按用户提供的带 industry= 参数的列表页 URL 逐个爬取。
    每个列表页提取 job_detail 链接，最多抓取 per_industry 条。
    没有 find_industry_btn 点击逻辑，直接靠 URL 过滤公司行业，更稳定。
    结束后同时爬取 DETAIL_URLS 里的单个详情页。"""
    co = ChromiumOptions().headless(False)
    co.set_argument('--disable-blink-features=AutomationControlled')
    co.set_argument('--start-maximized')
    try:
        co.set_browser_path(r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe')
    except:
        co.set_browser_path(r'C:\Program Files\Microsoft\Edge\Application\msedge.exe')
    page = ChromiumPage(co)
    boos_login(page)

    result = []
    skipped = 0

    total_industries = len(INDUSTRY_URLS)
    for idx, (label, url) in enumerate(INDUSTRY_URLS, 1):
        print(f"\n行业进度 {idx}/{total_industries}")
        print(f"\n🔍 正在打开行业列表页【{label}】: {url}")
        try:
            page.get(url)
            page.wait(5)
        except Exception as e:
            print(f"❌ 打开列表页失败: {e}")
            continue

        # 提取列表页中所有 job_detail 链接
        detail_links = []
        try:
            all_elems = page.eles('xpath://a[contains(@href, "/job_detail/")]', timeout=5)
            for a in all_elems:
                try:
                    href = a.attr('href') or a.get_attribute('href')
                except:
                    href = a.get_attribute('href') or a.attr('href')
                if not href:
                    continue
                if href.startswith('//'):
                    href = 'https:' + href
                elif href.startswith('/'):
                    href = 'https://www.zhipin.com' + href
                if '/job_detail/' in href and href not in detail_links:
                    detail_links.append(href)
        except Exception as e:
            detail_links = []
        print(f"🏷️ 【{label}】找到 {len(detail_links)} 个职位，抓取其中最多 {per_industry} 条")

        got = 0
        for link in detail_links:
            if got >= per_industry:
                break
            try:
                job_name, job_desc = _extract_job_detail(page, link)
                has_skill, matched = has_technical_skills(job_desc, job_name)
                if not has_skill:
                    skipped += 1
                    continue
                result.append([label, "", job_name, job_desc])
                got += 1
                print(f"  ✅ [{got}/{per_industry}] {job_name} | {matched[:3]}")
            except Exception as e:
                print(f"  ❌ 处理失败: {e}")
            time.sleep(0.5)

    # 同一浏览器会话中直接爬取详情页 URL，避免二次登录
    for label, url in DETAIL_URLS:
        print(f"\n🔍 正在打开详情页【{label}】: {url}")
        try:
            job_name, job_desc = _extract_job_detail(page, url)
        except Exception as e:
            print(f"❌ 打开详情页失败: {e}")
            continue
        has_skill, matched = has_technical_skills(job_desc, job_name)
        if not has_skill:
            skipped += 1
            print(f"⚠️ 详情页【{label}】无技术技能，已跳过: {job_name}")
            continue
        result.append([label, "", job_name, job_desc])
        print(f"  ✅ {job_name} | {matched[:3]}")

    page.quit()
    print(f"\n📊 按行业URL爬取完成: 共 {len(result)} 条, 跳过 {skipped} 条无技能岗位")
    return result


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
        # 单城市模式，默认北京
        cities_to_crawl = [{"code": "101010100", "name": "北京"}]
        print(f"\n📍 单城市爬取模式：北京")

    for kw in JOB_KEYWORDS:
        for city in cities_to_crawl:
            print(f"\n{'=' * 50}")
            print(f"🚀 开始爬取: 关键词【{kw}】在【{city['name']}】")
            print(f"🎯 目标: {TARGET_COUNT_PER_KEYWORD} 条")
            print(f"{'=' * 50}")

            data = fetch_jobs_for_keyword_in_city(kw, city['code'], city['name'])
            if data:
                all_data.extend(data)
                print(f"✅ 【{kw}】【{city['name']}】完成，获得 {len(data)} 条")
            else:
                print(f"⚠️ 【{kw}】【{city['name']}】未获取到有效数据")

            time.sleep(3)  # 城市间延迟

    return all_data


def save_raw_data_to_csv(data):
    """保存原始数据，包含城市信息"""
    if not data:
        return None
    os.makedirs(DATA_DIR, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    csv_file = os.path.join(DATA_DIR, f"boss_zhaopin_multi_{timestamp}.csv")
    with open(csv_file, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.writer(f)
        writer.writerow(["岗位关键词", "城市", "职位名称", "职位描述"])
        for record in data:
            writer.writerow(record)
    print(f"💾 原始数据已保存到本地：{csv_file} (共{len(data)}条)")
    return csv_file


def extract_skills(desc):
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
    """清洗数据，包含城市信息"""
    df = pd.read_csv(csv_path, encoding='utf-8-sig')
    required_cols = ['岗位关键词', '城市', '职位名称', '职位描述']

    # 兼容旧数据格式（没有城市字段的情况）
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

        # 只保留有核心技能的岗位
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

    if results:
        # 按城市统计
        city_stats = pd.DataFrame(results)['城市'].value_counts()
        print(f"   ├─ 城市分布:")
        for city, count in city_stats.items():
            print(f"   │   └─ {city}: {count} 条")

    return pd.DataFrame(results)


def send_cleaned_data_to_server(data_df):
    if data_df.empty:
        print("⚠️ 清洗后无数据可发送")
        return
    # NaN 不是合法 JSON，requests 会抛 "Out of range float values are not JSON compliant"，
    # 这里统一转成 null 再发
    data_json = data_df.astype(object).where(pd.notna(data_df), None).to_dict('records')
    try:
        resp = requests.post(SERVER_URL, json=data_json, timeout=10)
        if resp.status_code == 200:
            print(f"✅ 成功发送清洗后的 {len(data_json)} 条数据到服务器")
        else:
            print(f"❌ 发送失败 (HTTP {resp.status_code})")
    except Exception as e:
        print(f"❌ 网络错误: {e}")


def safe_save_dataframe(df, filepath):
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


def open_dashboard(html_path):
    if not OPEN_BROWSER:
        print(f"📊 数据看板已生成（后台运行，不自动打开）：{os.path.abspath(html_path)}")
        return
    if os.path.exists(html_path):
        webbrowser.open(f'file://{os.path.abspath(html_path)}')
        print("📊 已自动打开技能需求仪表板")


def process_cleaning_and_upload(raw_csv_path):
    print("\n===== 开始自动清洗数据 =====")

    today_df = clean_and_extract_skills_from_csv(raw_csv_path)
    if today_df.empty:
        print("❌ 清洗失败，无有效数据")
        export_data_to_json(pd.DataFrame(), JSON_DATA_FILE)
        generate_dynamic_dashboard(HISTORY_FILE, HTML_VIEWER_FILE)
        open_dashboard(HTML_VIEWER_FILE)
        return

    print(f"✨ 今日清洗出 {len(today_df)} 条技能记录")

    if not safe_save_dataframe(today_df, HISTORY_FILE):
        print("❌ 无法保存历史文件，本次清洗中断")
        return

    all_df = pd.read_csv(HISTORY_FILE, encoding='utf-8-sig')
    export_data_to_json(all_df, JSON_DATA_FILE)
    generate_dynamic_dashboard(HISTORY_FILE, HTML_VIEWER_FILE)
    send_cleaned_data_to_server(all_df)
    open_dashboard(HTML_VIEWER_FILE)

    print("\n📋 清洗结果预览（前5条）:")
    display_cols = ['岗位关键词', '城市', '职位名称', '编程语言', '框架/库', '工具/环境']
    available_cols = [c for c in display_cols if c in today_df.columns]
    print(today_df[available_cols].head())


def run_once():
    print("=" * 50)
    print(f"🚀 开始执行爬取任务 —— {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"🎯 按公司行业逐类爬取，每个行业抓取 {PER_INDUSTRY} 条岗位")
    print("=" * 50)

    raw_data = crawl_industry_urls(per_industry=PER_INDUSTRY)

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
    if not os.path.exists(HTML_VIEWER_FILE):
        generate_dynamic_dashboard(HISTORY_FILE, HTML_VIEWER_FILE)

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
    if '--once' in sys.argv:
        run_once()
    else:
        print("请选择运行模式:")
        print("1. 单次运行（按公司行业逐类爬取，每行业8条）")
        print("2. 定时任务（每2天运行一次）")
        choice = input("请输入选择 (1/2): ").strip()
        run_scheduled() if choice == '2' else run_once()