# 智程学途 · 人才培养模块联调记录

- 日期：2026-10-28
- 范围：登录 → 菜单 → 人才培养页（`/talent`）→ 9 个后端接口 → CSV 导入
- 结论：主链路可跑通，共发现 6 个问题并修复 5 个代码问题 + 1 处数据状态；页面最终 0 控制台错误

## 一、运行环境（本次实测）

| 组件 | 实际使用 | 说明 |
|---|---|---|
| JDK（运行） | Corretto 1.8.0_492 | `java -jar ruoyi-admin.jar` 正常 |
| JDK（编译） | JDK 11.0.30 | Corretto 8 跑 `mvn clean package` 时 JVM 崩溃（hs_err，ACCESS_VIOLATION），改用 JDK 11 编译，`source/target=1.8` 产物在 JDK 8 下运行正常 |
| Maven | 3.9.16 | 根目录 `mvn -DskipTests -pl ruoyi-admin -am package` |
| MySQL | 8.0，`localhost:3306/personnel_information` | 6180 岗位 / 71340 技能 / 120 课程 / 24 专业 |
| Redis | `localhost:6379`，无密码 | 登录验证码存在 `captcha_codes:{uuid}` |
| 后端 | `http://localhost:8081` | `ruoyi-admin/target/ruoyi-admin.jar` |
| 前端 | `http://localhost:5173` | Vite 6，`/dev-api` 代理到 8081 |

自动登录方式（无浏览器手工输入验证码）：`GET /captchaImage` 取 uuid → 从 Redis 读 `captcha_codes:{uuid}` → `POST /login` 取 token → 后续请求带 `Authorization: Bearer <token>`。

## 二、跑通的链路

菜单「人才培养」→ 路由 `/talent` → 页面并行请求：

| 接口 | 结果 |
|---|---|
| `/project/talent/overview` | 200，6180 岗位 / 快照 3256 / 批次 2924 / 技能 680 |
| `/project/talent/skill-rank` | 200，Python 1309、Java 1124… |
| `/project/talent/skill-trend` | 200，4 期：2026-06-12 / 09-09 / 09-10 / 当前快照 |
| `/project/talent/category-stat` | 200，AI大模型 39.2% 等 |
| `/project/talent/major-match` | 200，24 个专业，含 matched/gap 技能 |
| `/project/talent/warnings` | 200，4 条中等 + 2 条低 |
| `/project/talent/suggestions` | 200，4 条建议（技能趋势/课程缺口/专业优化） |
| `/project/talent/curriculum` | 200，35 门课程 / 754 知识点 |
| `/project/talent/data-source` | 200，批次、行业、口径、限制说明齐全 |

页面（headless Edge + CDP）：正文 7793 字符，`NaN=0`，`undefined=0`，控制台错误 0。

## 三、发现并修复的问题

| # | 问题 | 现象 | 修复 | 验证 |
|---|---|---|---|---|
| 1 | 负数分页参数导致 SQL 报错 | `skill-rank?limit=-1`、`skill-trend?topN=-3` 返回 HTTP 200 但 `code=500`，消息为 SQL 语法错误 | `TalentAnalysisService.skillRank` 把 limit 夹取到 1–200，`skillTrend` 把 topN 夹取到 1–30 | 传 -1/-3 均返回 200 正常数据 |
| 2 | 「其他」方向被当成“无对应专业” | 产生一条 high 级“岗位方向『其他』尚无对应专业”预警，且挤占 KPI 重点预警数 | `warnings()` 跳过兜底方向「其他」 | 预警列表不再出现该条，重点预警 4 条 |
| 3 | 专业优化建议选错对象 | 建议“『运动健康指导』培养方案需重点调整”，但同一句写着覆盖率 100%（5/5），自相矛盾 | 原逻辑按岗位量排序后取列表末位；改为在 `sampleEnough=true` 且 `totalSkills>0` 的专业中取 `matchRate` 最小者，且低于阈值才输出 | 现在输出“『工商企业管理（财务与人事方向）』…0.0%”，与该专业实际覆盖一致 |
| 4 | CSV 导入只认 UTF-8 | Excel 在中文 Windows 默认导出 GBK，导入直接报“CSV 缺少『职位名称』列” | 读取字节后检测编码：UTF-8 严格解码失败则回退 GBK，并跳过 UTF-8 BOM | GBK 与 UTF-8 BOM 两种文件均导入成功（1 条岗位） |
| 5 | 快照为空时页面全 0 且无解释 | 3256 条快照的 `batch_date` 被回填成历史批次后，`snapshotJobs=0`，技能 680→0，专业匹配全部 0%，页面看起来“坏了” | `warnings()` 增加数据状态自检：快照为 0 时输出 high 级“当前没有快照岗位，看板指标会全部为 0”，并给出恢复办法 | 正常状态不触发；代码已随 jar 部署 |
| 6 | KPI 与预警列表数量对不上 | KPI 显示“预警提示 5”，副标题写“专业覆盖 / 专业设置 / 样本”，列表实际 7 条且已无“专业设置” | 副标题改为“共 {{ warnings.length }} 条 · 重点（中/高）{{ warningCount }} 条” | 页面显示“共 6 条 · 重点（中/高）4 条” |

改动文件：

- `ruoyi-project/src/main/java/com/ruoyi/project/talent/service/TalentAnalysisService.java`
- `ruoyi-project/src/main/java/com/ruoyi/project/talent/service/TalentCsvImportService.java`
- `ruoyi-ui/src/views/project/talent/index.vue`

修复前的源文件副本在 `doc/_talent_fix_backup/`。

## 四、数据侧异常（本次实际遇到）

联调中途发现 `talent_job_posting` 的 3256 条“快照”记录被回填了 `batch_date`（2026-10-27 19:09 / 19:30 / 2026-10-28 09:14），导致：

- `overview.snapshotJobs = 0`、`skillCount = 0`
- `major-match` 全部 `jobCount=0`、`matchRate=0`
- 页面 KPI 仍显示 6180 岗位，但技能、匹配、预警全空

处理：以 `sql/backup/talent_backup_20261028.sql`（10:17 导出，快照 `batch_date=''`）为准，把这三批的 `batch_date` 恢复为空，并同步 `talent_job_skill.batch_date`；恢复后 3256 快照 + 2924 批次 = 6180，技能 680，页面指标正常。

> 注意：导入接口在“采集批次”留空时写入快照，填了日期则只进批次。快照为 0 时看板所有指标都会归零，新增的数据状态预警会明确提示。

## 五、发现但未改的风险

1. `/project/talent/import` 没有 `@PreAuthorize` 权限注解，任何已登录用户都能导入/覆盖岗位数据（会写库）。建议加 `@PreAuthorize("@ss.hasPermi('project:talent:import')")` 并给菜单配置按钮权限。
2. `LearnController` 中讯飞星火 AK/SK 硬编码，建议移到配置/环境变量并轮换。
3. `doc/RUNNING.md` 的路径仍写 `D:\AAAWork\Knowledge\Knowledge`，与当前 `D:\000\abc` 不一致；JDK 编译建议也需更新（JDK 8 编译在本机会崩）。
4. 旧的前端 dev server（5173）会缓存旧模块，改完 Vue 需要重启 Vite 才能生效。

## 六、复现验证

```powershell
# 1) 编译（JDK 11）
$env:JAVA_HOME='C:\Program Files\Java\jdk-11.0.30'
mvn -DskipTests -pl ruoyi-admin -am package

# 2) 运行（JDK 8）
$env:JAVA_HOME='C:\Users\hp\.jdks\corretto-1.8.0_492'
& "$env:JAVA_HOME\bin\java.exe" -jar ruoyi-admin\target\ruoyi-admin.jar

# 3) 前端
cd ruoyi-ui; npm run dev     # http://localhost:5173

# 4) 边界回归（需先登录拿 token）
#    GET /project/talent/skill-rank?limit=-1   -> code 200
#    GET /project/talent/skill-trend?topN=-3   -> code 200
```

## 七、结论

- 登录、菜单、9 个接口、页面渲染、CSV 导入均已跑通；页面无 JS 错误、无 NaN/undefined。
- 修复 5 处代码问题（负数参数、其他方向预警、建议选错专业、GBK 导入、快照为空的提示）+ 1 处前端文案。
- 当前数据：6180 岗位（快照 3256 + 批次 2924）、71340 条技能、24 个专业、35 门课程。
- 仍有权限与密钥两项安全风险，建议在提交前处理。
