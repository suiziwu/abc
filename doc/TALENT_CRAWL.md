# 人才培养 · 一键爬取（按钮功能改造）

- 日期：2026-10-28
- 变更：「导入 CSV」按钮改为「爬取数据」；点击后在后台运行爬虫，实时显示进度，完成后自动导入并刷新看板

## 一、交互

```
点击「爬取数据」
  → POST /project/talent/crawl/start
  → 后端启动 Python 爬虫进程（不阻塞页面）
  → 前端每 2 秒 GET /project/talent/crawl/status
  → 按钮区域变成进度条 + 当前进度文案（已保存 X 条 / 正在爬取第 i/n 个详情页）
  → 进程正常结束后：
       取爬虫打印的落盘 CSV（回退：扫描输出目录里本次生成的最新 CSV）
       复用 TalentCsvImportService 入库
       复制一份到「04-清洗后数据」，给技能热度曲线增加时间点
       status=DONE, percent=100
  → 前端提示「爬取并导入完成：新增 N 条岗位，看板已刷新」
     并自动把采集批次切回「全部时间」再 loadAll()，确保新数据可见
```

失败时 `status=ERROR`，前端弹出错误信息；任务状态在页面刷新后仍可恢复（进入页面会自动查一次状态）。

## 二、配置（application.yml）

```yaml
talent:
  crawl:
    python: python                    # 只是候选，见下方说明
    script: fiveEi/01-爬虫脚本/BOSS直聘爬虫.py
    working-dir: fiveEi/01-爬虫脚本
    output-dir: fiveEi/新爬取
    total-target: 200        # 进度条按“已保存/目标”估算
    timeout-minutes: 90      # 超时强制结束
  cleaned-data-dir: fiveEi/04-清洗后数据
```

- **路径一律写相对工程根目录**（`TalentPathResolver` 会按「含 fiveEi 目录的最近祖先」定位工程根）。
  以前这里写的是 `D:/system/lerning/abc/...`、`D:/IDEA2025/xiangmu/...` 这类别的机器上的绝对路径，
  目录一旦不存在按钮就报“找不到爬虫脚本”。现在配置失效时还会自动在工程目录里探测一遍。
- `python`：只是**第一个候选**。机器上常有多个 Python，真正装了 `DrissionPage` + `pandas` 的未必是 PATH 里那个。
  服务会按「配置值 → python → python3 → py -3 → `%LOCALAPPDATA%\Programs\Python\Python*`」依次探测，
  用第一个能 import 成功的，并在日志里打一行 WARN 说明替换了哪个。想指定就用绝对路径。
- `script`：要运行的爬虫文件；换成智联爬虫或其它脚本只改这一行。
- `output-dir`：爬虫输出 CSV 的目录（不存在会自动创建）；任务结束后取「开始时间之后修改的最新 CSV」。
- `output-dir` 会以环境变量 `BOSS_DATA_DIR` 传给爬虫，所以**不用再改爬虫里的 `DATA_DIR`**；
  命令行直接跑爬虫时，`DATA_DIR` 默认回落到脚本同级的 `../新爬取`。
- 爬虫进程继承 `PYTHONIOENCODING=utf-8`、`BOSS_NO_BROWSER=1`（后台运行时不自动弹出数据看板页面）。

## 三、接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/project/talent/crawl/start` | 启动爬取；已有任务在跑时返回当前进度 |
| GET | `/project/talent/crawl/status` | 返回 `id/status/percent/message/result/logs`；无任务时 `status=IDLE` |

状态：`RUNNING → IMPORTING → DONE`（或 `ERROR`）。

## 四、进度解析

后端逐行读取爬虫 stdout，匹配：

- `已保存 12/20 条` → 百分比（按 `total-target` 折算，最高 85%）
- `第 3/20 个职位详情页` → 文案「正在爬取职位详情 3/20」
- `已导出JSON` / `可视化页面已生成` → 88%
- 进程结束 → 92%（导入中）→ 100%

最近 30 行输出保存在任务 `logs` 字段里，出错时 `message` 会带上最后一行。

**落盘路径以爬虫自己打印的为准**：匹配 `💾 原始数据已保存到本地：<path>` 拿到 CSV 绝对路径，
找不到时再退回扫描 `output-dir`。这样即使爬虫脚本里的 `DATA_DIR` 被改回硬编码路径
（历史上被回滚过一次，导致任务跑完 4 分钟却报“没找到本次生成的 CSV”），数据也不会丢。

## 四·补、导入字段映射

爬虫输出的 CSV 表头是 `岗位关键词,城市,职位名称,职位描述`，导入时对应：

| CSV 列 | 入库字段 | 说明 |
|---|---|---|
| 职位名称 | `job_title` | 必需，缺失整行跳过 |
| 职位描述 | `description` | 用于技能抽取 |
| 岗位关键词 | `keyword` | 需显式匹配 `岗位关键词`，否则关键词维度为空 |
| 城市 | `city` | 爬虫按行业 URL 抓取时该列为空，会回落默认值 |
| — | `industry` | BOSS CSV 无行业列，统一落 `其他`；需要行业维度请用「逐行业抓取结果」CSV |

## 四·补、技能热度曲线为什么会出现「爬完了但曲线没更新」

曲线（`/project/talent/skill-trend` → `TalentFolderTrendService`）**不读数据库**，而是扫描
`talent.cleaned-data-dir`（`fiveEi/04-清洗后数据`）下的 CSV，每个文件按**修改时间**算一个时间点。
爬取结束时会 `copyToCleanedDir()` 把结果复制成 `BOSS爬取_yyyyMMdd_HHmmss.csv`，所以每次爬取都应该多一个点。

之前有三个坑，已全部修掉：

| 坑 | 表现 | 修法 |
|---|---|---|
| `minJobs` 默认 20 | 一次爬取只有几条到几十条，新批次被过滤 → 曲线看不到新点 | 爬取结果文件（`BOSS爬取_*` / `boss_zhaopin_multi*`）**一律保留**，不受阈值限制 |
| 只保最新一批 | 下一次爬取后，上一批变成“非最新”又被过滤 → 历史断档 | 同上，所有爬取结果都保留；另外兜底「最新一批必定保留」 |
| 快照标签用 `new Date()` | 时间下拉里的“最新时间”其实是**当前时刻**，每次刷新都变，和曲线上的文件时间对不上 | 改成取 `max(create_time)`（最近一次入库时间），与曲线最后一个点一致 |

另外：同一分钟落盘的多个 CSV 会得到相同标签，横轴重复；现在重复时补序号 `（2）（3）…`。

**验证方式**：点一次「爬取数据」，约 4 分钟后曲线末尾应出现新的 `yyyy-MM-dd HH:mm` 点，
且时间下拉的第一项（当前快照）显示同一时间。

## 五、联调方式（不跑真实爬虫）

`scripts/mock_crawl.py` 是模拟爬虫：按真实格式打印进度行，并写一份 CSV 到 `BOSS_DATA_DIR`（即 `output-dir`）。
用命令行参数覆盖配置即可，脚本路径写相对路径也能被解析到：

```powershell
java -jar ruoyi-admin/target/ruoyi-admin.jar `
  --talent.crawl.script=scripts/mock_crawl.py `
  --talent.crawl.working-dir=scripts `
  --talent.crawl.output-dir=scripts/_crawl_out `
  --talent.cleaned-data-dir=scripts/_crawl_out/cleaned
```

已验证（2026-10-28）：`RUNNING 1% → 3% → 5% → DONE 100%`，进度实时更新，自动导入 1 条岗位，
日志打出「配置的 Python（python）缺少爬虫依赖，自动改用 …Python312\python.exe」。
联调会往库里插一条 Mock 岗位，测完按 `source like '%mock%'` 删掉即可。

## 六、注意

1. 真实 BOSS 爬虫会**打开浏览器**，首次运行需要在弹出的浏览器里登录；没有登录状态时任务会卡住直到超时。
2. 一次完整爬取按脚本配置约 1 小时；`timeout-minutes` 控制上限。
3. 爬虫输出目录由后端通过 `BOSS_DATA_DIR` 注入，和 `output-dir` 天然一致，不用再手工同步脚本里的 `DATA_DIR`。
4. 同一时间只允许一个爬取任务；重复点击返回当前进度，不会起第二个进程。
5. 导入失败不会丢数据：任务会变成 `ERROR`，CSV 仍保留在输出目录，可手动导入。
6. 报错文案已经区分场景：找不到脚本 / 输出目录不可用 / 没有可用 Python 环境 / 进程退出码非 0，都会直接说明原因。

## 七、涉及文件

| 文件 | 改动 |
|---|---|
| `ruoyi-project/.../talent/service/TalentCrawlService.java` | 进程、进度、超时、自动导入；路径解析 + Python 自动探测 |
| `ruoyi-project/.../talent/service/TalentPathResolver.java` | 新增：工程根定位、配置路径失效时的候选探测 |
| `ruoyi-project/.../talent/service/TalentFolderTrendService.java` | 清洗目录同样走解析，配置失效自动回落 |
| `ruoyi-admin/.../TalentController.java` | `/crawl/start`、`/crawl/status` |
| `ruoyi-admin/src/main/resources/application.yml` | `talent.crawl.*`、`talent.cleaned-data-dir` 改为相对路径 |
| `ruoyi-ui/src/api/project/talent.js` | 两个接口 |
| `ruoyi-ui/src/views/project/talent/index.vue` | 按钮「爬取数据」，进度条 + 2 秒轮询 |
| `fiveEi/01-爬虫脚本/BOSS直聘爬虫.py` | `DATA_DIR` 支持 `BOSS_DATA_DIR`；`BOSS_NO_BROWSER=1` 时不弹看板 |
| `scripts/mock_crawl.py` | 联调用模拟爬虫，输出目录跟随 `BOSS_DATA_DIR` |
