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
       找到输出目录里本次生成的最新 CSV
       复用 TalentCsvImportService 入库
       status=DONE, percent=100
  → 前端提示「爬取并导入完成」，自动 loadAll() 刷新看板、图表
```

失败时 `status=ERROR`，前端弹出错误信息；任务状态在页面刷新后仍可恢复（进入页面会自动查一次状态）。

## 二、配置（application.yml）

```yaml
talent:
  crawl:
    python: python
    script: D:/000/abc/fiveEi/01-爬虫脚本/BOSS直聘爬虫.py
    working-dir: D:/000/abc/fiveEi/01-爬虫脚本
    output-dir: D:/IDEA2025/xiangmu/fiveEi/新爬取
    total-target: 200        # 进度条按“已保存/目标”估算
    timeout-minutes: 90      # 超时强制结束
```

- `script`：要运行的爬虫文件；换成智联爬虫或其它脚本只改这一行。
- `output-dir`：爬虫输出 CSV 的目录；任务结束后取「开始时间之后修改的最新 CSV」。
- 爬虫进程继承 `PYTHONIOENCODING=utf-8`，后端按 UTF-8 读输出。

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

## 五、联调方式（不跑真实爬虫）

`scripts/mock_crawl.py` 是模拟爬虫：按真实格式打印进度行，并写一份 CSV 到指定目录。联调时用命令行参数覆盖配置：

```powershell
java -jar ruoyi-admin/target/ruoyi-admin.jar `
  --talent.crawl.script=D:/000/abc/scripts/mock_crawl.py `
  --talent.crawl.working-dir=D:/000/abc/scripts `
  --talent.crawl.output-dir=D:/000/abc/scripts/_crawl_out
```

已验证：进度从 2% 实时更新到 100%，自动导入 1 条岗位，页面无报错。正常启动（不带参数）则使用 `application.yml` 里的真实 BOSS 爬虫。

## 六、注意

1. 真实 BOSS 爬虫会**打开浏览器**，首次运行需要在弹出的浏览器里登录；没有登录状态时任务会卡住直到超时。
2. 一次完整爬取按脚本配置约 1 小时；`timeout-minutes` 控制上限。
3. 爬虫输出目录必须与脚本里的 `DATA_DIR` 一致（当前为 `D:\IDEA2025\xiangmu\fiveEi\新爬取`）。
4. 同一时间只允许一个爬取任务；重复点击返回当前进度，不会起第二个进程。
5. 导入失败不会丢数据：任务会变成 `ERROR`，CSV 仍保留在输出目录，可手动导入。

## 七、涉及文件

| 文件 | 改动 |
|---|---|
| `ruoyi-project/.../talent/service/TalentCrawlService.java` | 新增：进程、进度、超时、自动导入 |
| `ruoyi-admin/.../TalentController.java` | 新增 `/crawl/start`、`/crawl/status` |
| `ruoyi-admin/src/main/resources/application.yml` | 新增 `talent.crawl.*` 配置 |
| `ruoyi-ui/src/api/project/talent.js` | 新增两个接口 |
| `ruoyi-ui/src/views/project/talent/index.vue` | 按钮改为「爬取数据」，进度条 + 2 秒轮询 |
| `scripts/mock_crawl.py` | 联调用模拟爬虫 |
