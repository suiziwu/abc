# -*- coding: utf-8 -*-
"""按对照表里的全部行业 URL 逐个访问并测试爬取。

用法: 先登录 zhipin 浏览器，然后运行本脚本。每个 URL 地址最多抓 TEST_PER_URL 条岗位。
读取: 本目录下 行业编码对照表.csv（149 个编码）
输出: 逐行业抓取结果_<时间戳>.csv （含行业编码、行业名称、层级、职位名称、职位描述）
"""
import csv
import os
import time
from datetime import datetime
from DrissionPage import ChromiumPage, ChromiumOptions

BASE = os.path.dirname(os.path.abspath(__file__))
CSV_TABLE = os.path.join(BASE, "行业编码对照表.csv")
OUT_CSV = os.environ.get("OUT_CSV") or os.path.join(BASE, "逐行业抓取结果.csv")  # 固定文件名，便于断点续爬追加

TEST_PER_URL = int(os.environ.get("TEST_PER_URL", 200))  # 每个 URL/行业要抓取的岗位条数
LIMIT_INDUSTRIES = int(os.environ.get("LIMIT_INDUSTRIES", 0))  # 0=全部行业，>0 只抓前 N 个（用于验证）
NAV_WAIT = 5      # 列表页等待秒数
DETAIL_WAIT = 2   # 详情页等待秒数
NAV_TIMEOUT = 20  # 单个列表页加载超时（秒），避免个别 URL 长时间卡死
PROGRESS_LOG = os.path.join(BASE, "crawl_progress.log")  # 实时进度日志

# 清空并重置进度日志
_PROGRESS_HANDLE = None


def log_progress(line):
    """追加一行到进度日志并立即刷盘，便于实时查看"""
    global _PROGRESS_HANDLE
    if _PROGRESS_HANDLE is None:
        _PROGRESS_HANDLE = open(PROGRESS_LOG, "a", encoding="utf-8")
    _PROGRESS_HANDLE.write(line + "\n")
    _PROGRESS_HANDLE.flush()


def read_table():
    """读取对照表，返回 [{code,name,level,parent,url}] 列表（全部 149 个）"""
    rows = []
    with open(CSV_TABLE, "r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        for r in reader:
            url = (r.get("职位列表URL") or "").strip()
            if not url:
                continue
            rows.append({
                "code": (r.get("行业编码") or "").strip(),
                "name": (r.get("行业名称") or "").strip(),
                "level": (r.get("层级") or "").strip(),
                "parent": (r.get("上级行业") or "").strip(),
                "url": url,
            })
    return rows


def extract_detail(page, link):
    """打开单个岗位详情页并提取职位名称+描述"""
    page.get(link, timeout=NAV_TIMEOUT)
    page.wait(DETAIL_WAIT)
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


def crawl_urls():
    co = ChromiumOptions().headless(False)
    co.set_argument('--disable-blink-features=AutomationControlled')
    co.set_argument('--start-maximized')
    try:
        co.set_browser_path(r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe')
    except:
        co.set_browser_path(r'C:\Program Files\Microsoft\Edge\Application\msedge.exe')
    page = ChromiumPage(co)

    rows = read_table()
    if LIMIT_INDUSTRIES > 0:
        rows = rows[:LIMIT_INDUSTRIES]

    # 断点续爬：从已存在的结果 CSV 读取已完成（已成功抓取过）的行业编码
    done_codes = set()
    if os.path.exists(OUT_CSV):
        with open(OUT_CSV, "r", encoding="utf-8-sig", newline="") as f:
            for r in csv.DictReader(f):
                c = (r.get("行业编码") or "").strip()
                if c:
                    done_codes.add(c)
    resume_note = f"（断点续爬，已完成 {len(done_codes)} 个行业）" if done_codes else ""
    print(f"📋 对照表共 {len(rows)} 个行业 URL，每个测试抓取 {TEST_PER_URL} 条 {resume_note}", flush=True)
    print("=" * 60, flush=True)

    # 进度日志不清空，追加记录
    log_progress(f"[START] {datetime.now().isoformat()} 共{len(rows)}个URL 每个{TEST_PER_URL}条 {resume_note}".strip())

    # 结果 CSV 初始化表头：仅在文件不存在时创建，保留断点数据并追加
    if not os.path.exists(OUT_CSV):
        with open(OUT_CSV, "w", encoding="utf-8-sig", newline="") as f:
            w = csv.writer(f)
            w.writerow(["行业编码", "行业名称", "层级", "上级行业", "职位名称", "职位描述"])

    ok = 0
    fail = 0
    total_jobs = 0
    resumed = 0

    for i, row in enumerate(rows, 1):
        # 断点续爬：跳过已完成（已访问到成功抓取）的行业
        if row["code"] in done_codes:
            resumed += 1
            continue
        ind_name = f"{row['name']}({row['code']})"
        line = f"[{i}/{len(rows)}] {datetime.now().strftime('%H:%M:%S')} 访问【{ind_name}】"
        print(line, flush=True)
        log_progress(line)
        try:
            page.get(row["url"], timeout=NAV_TIMEOUT)
            page.wait(NAV_WAIT)
        except Exception as e:
            msg = f"   ❌ 打开列表页失败: {e}"
            print(msg, flush=True)
            log_progress(msg)
            fail += 1
            continue

        # 翻页收集 job_detail 链接，直到达到 TEST_PER_URL 或无更多页面
        detail_links = []
        seen = set()
        max_pages = 200  # 保险上限，防止死循环
        for pn in range(1, max_pages + 1):
            try:
                all_elems = page.eles('xpath://a[contains(@href, "/job_detail/")]', timeout=5)
                new_found = 0
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
                    if '/job_detail/' in href and href not in seen:
                        seen.add(href)
                        detail_links.append(href)
                        new_found += 1
            except Exception:
                new_found = 0
            if len(detail_links) >= TEST_PER_URL:
                break
            # 尝试点“下一页”
            next_btn = None
            for sel in [
                'xpath://a[contains(@class,"next")]',
                'xpath://a[contains(text(),"下一页")]',
                'xpath://li[contains(@class,"next")]/a',
                'xpath://i[contains(@class,"icon-next")]',
            ]:
                try:
                    el = page.ele(sel, timeout=1.5)
                    if el and el.states.is_displayed:
                        next_btn = el
                        break
                except:
                    continue
            if not next_btn:
                # 新职位可能用“加载更多”滚动加载
                page.scroll.to_bottom()
                page.wait(1.5)
                before = len(detail_links)
                try:
                    all_elems = page.eles('xpath://a[contains(@href, "/job_detail/")]', timeout=3)
                    for a in all_elems:
                        try:
                            href = a.attr('href') or a.get_attribute('href')
                        except:
                            href = a.get_attribute('href') or a.attr('href')
                        if href:
                            if href.startswith('//'):
                                href = 'https:' + href
                            elif href.startswith('/'):
                                href = 'https://www.zhipin.com' + href
                            if '/job_detail/' in href and href not in seen:
                                seen.add(href)
                                detail_links.append(href)
                except Exception:
                    pass
                after = len(detail_links)
                if after - before <= 0:
                    # 滚到底也无新增 → 没有更多
                    break
                continue
            else:
                next_cls = next_btn.attr('class') or ''
                if 'disabled' in next_cls:
                    break
                try:
                    next_btn.click()
                    page.wait(2)
                except Exception as e:
                    break
        print(f"   📄 翻页收集到 {len(detail_links)} 个职位链接（共翻 {pn} 页）", flush=True)
        log_progress(f"   📄 翻页收集到 {len(detail_links)} 个职位链接")
        if not detail_links:
            msg = "   ❌ 未找到任何职位链接（可能被风控/未登录）"
            print(msg, flush=True)
            log_progress(msg)
            fail += 1
            continue

        got = 0
        for link in detail_links:
            if got >= TEST_PER_URL:
                break
            try:
                job_name, job_desc = extract_detail(page, link)
                with open(OUT_CSV, "a", encoding="utf-8-sig", newline="") as f:
                    w = csv.writer(f)
                    w.writerow([
                        row["code"], row["name"], row["level"], row["parent"],
                        job_name, job_desc,
                    ])
                got += 1
                total_jobs += 1
                msg = f"   ✅ [{got}/{TEST_PER_URL}] {job_name}"
                print(msg, flush=True)
                log_progress(msg)
            except Exception as e:
                msg = f"   ❌ 处理失败: {e}"
                print(msg, flush=True)
                log_progress(msg)
            time.sleep(0.5)
        if got > 0:
            ok += 1
        else:
            fail += 1

    page.quit()

    msg = ("=" * 60 + "\n"
           f"📊 测试完成: 成功 {ok} 个 URL, 失败 {fail} 个 URL, 共抓取 {total_jobs} 条岗位\n"
           f"💾 结果已保存: {OUT_CSV}")
    print(msg, flush=True)
    log_progress(msg)
    if _PROGRESS_HANDLE:
        _PROGRESS_HANDLE.close()
    return OUT_CSV


if __name__ == "__main__":
    crawl_urls()