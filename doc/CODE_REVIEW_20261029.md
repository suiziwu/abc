# 项目代码审查报告

- 审查对象：智程学途知识图谱学习平台（RuoYi 3.9 + Vue3 + RAG）
- 审查日期：2026-10-29
- 审查方式：全量静态阅读 + 数据库实况核对（只读，未修改任何代码）
- 代码规模：后端业务类 137 个、Controller 45 个、Vue 页面 110 个

## 结论速览

| 等级 | 数量 | 典型问题 |
| --- | --- | --- |
| P0 致命 | 6 | 重建知识库会误删已导入课本；导入先删后写无事务；事务内调 600s 大模型 |
| P1 高危 | 8 | 任意文件读取；存储型 XSS；Druid 匿名访问；AI 对话水平越权 |
| P2 中危 | 13 | 软删数据被查出；章节唯一键与软删冲突；检索 rerank 配置失效 |
| P3 低危 | 8 | 伪分页、定时器泄漏、文档过期、仓库臃肿 |

**最需要立刻处理的三件事**：P0-1（数据会被删）、P1-1（任意文件读取）、P0-3（连接池会被打满）。

---

## P0 致命

### P0-1 重建知识库会误删「已导入的课本」

`ruoyi-project/src/main/java/com/ruoyi/project/kb/KbIngestService.java:148`、`:221-245`

```java
// 148：把该学科/章节下"所有"切片来源的 docId 都取出来
List<String> previousDocIds = kbChunkMapper.selectKbChunkDocIds(subjectId, chapterId);
...
// 221：但新集合只包含本次重建能生成的 4 类文档
Set<String> newDocIds = chunksByDoc.keySet();   // 只有 chapter/point/transcript/quiz
for (String docId : previousDocIds) {
    if (!newDocIds.contains(docId)) { staleDocIds.add(docId); }
}
store.deleteByDocIds(staleDocIds);              // 向量 + kb_chunk + kb_document 三处物理删除
```

`chunksByDoc` 只由 `buildChapterDocs / buildPointDocs / buildTranscriptDocs / buildQuizDocs` 填充，永远不会产生 `document:*` 类型的 docKey；而 `selectKbChunkDocIds`（`KbChunkMapper.xml:155`）只按 `subject_id/chapter_id` 过滤，会把 `KbDocumentImportService` 写入的 `document:<subjectId>:<文件名>` 一起查出来。

**影响**：管理员点一次「重建」，该学科下所有导入的课本切片被永久删除（当前库里 `document:94:*` 有 1 个文档 / 6 个切片，会直接消失）。无二次确认、无回收站，`staleRemoved` 只返回一个数字。

**修法**：stale 判定加来源白名单，只清理本次重建负责的 4 类；`document:*` 由导入/删除接口自行管理生命周期。

### P0-2 课本导入「先删旧版、后向量化」，中途失败即永久丢书

`ruoyi-project/src/main/java/com/ruoyi/project/kb/KbDocumentImportService.java:113-123` → `:166` → `:200`

```java
// 113-123：先把旧版本三处全删
kbDocumentMapper.deleteKbDocumentByKeys(...);
kbChunkMapper.deleteKbChunkByDocIds(...);
storeFactory.active().deleteByDocIds(...);
catch (Exception e) { log.warn("清理旧版课本切片失败（可忽略，继续导入）: {}", ...); }  // 吞掉

// 166：才开始向量化，可能重试后抛异常
List<float[]> vectors = embeddingClient.embedBatch(texts);
// 200：才写新切片
int upserted = storeFactory.active().upsert(chunks);
```

`importDocument` 全程没有 `@Transactional`。`embedBatch` 因限流/超时/条数不一致失败时，用户看到「导入失败」，但**旧教材已经不存在了**。同样问题在 `KbIngestService.rebuildByLessons:214-219`（先 `deleteByDocIds` 再 `upsert`，`upsert` 抛异常即数据空洞）。

**修法**：顺序改为「解析 → 切片 → 向量化 → 写新版本 → 删旧版本」，或写入 `doc_key + version`，成功后再清理旧 key。

### P0-3 事务内调用大模型（600s 超时），20 并发即可打满连接池

`ruoyi-project/src/main/java/com/ruoyi/project/service/impl/PointAiGenerateServiceImpl.java:69`、`:93`

```java
@Override
@Transactional(rollbackFor = Exception.class)
public Map<String, Object> generateByAi(...) {
    ...
    String answer = callAi(buildPrompt(...));   // 远程 LLM，timeout-seconds: 600
```

连接池 `maxActive: 20`、`maxWait: 60000`（`application-druid.yml:24,26`），Tomcat 线程 800（`application.yml:85`）。20 个并发 AI 生成就能占满连接池，其余所有请求在 60s 后批量抛 `GetConnectionTimeoutException`，表现为「全站假死」。

同类问题：`KbBaseService.java:100-123` 在 `@Transactional` 里循环调 `kbVectorStoreFactory.active().clear(...)`（Milvus HTTP，读超时 30s），多学科删除时事务可持有 `N × 33s` 的连接与行锁。

**修法**：远程调用移到事务外——先调 LLM 拿 JSON，再在独立事务方法里只做写库（参考项目已有的 `KbChunkTxService` 拆分方式）。

### P0-4 向量库在 local / milvus 之间自动切换，但历史数据不迁移

`ruoyi-project/src/main/java/com/ruoyi/project/kb/KbVectorStoreFactory.java:51-69`

```java
if (!active.provider().equals(configured)) {
    if (configuredStore != null && cachedAvailable(configuredStore)) {
        log.info("知识库实现 {} 已恢复，重新切换", configured);
        active = configuredStore;    // 直接切，不做任何数据迁移
    }
}
```

两种实现落库方式不同：`LocalKbVectorStore` 只写 `kb_chunk.embedding`（Java 侧算余弦），`MilvusKbVectorStore` 向量只进 Milvus。切回 milvus 后，local 期间写入的切片在 Milvus 中不存在 → 检索命中为空 → `CourseStudyServiceImpl.retrieve` 静默走 `strategy=none`，用户只看到「无相关片段」。

**当前就是活的隐患**：`config/application.yml:46-50` 配的是 `provider: milvus` + `fallback-to-local: true`，而本机 19530 端口**当前没有监听**（Milvus 未启动），即应用正跑在 local 上。一旦启动 Milvus，全部历史语料立刻对检索不可见。

**修法**：切换时校验两边条数，不一致则标记 `needRebuild` 并在 `/project/kb/status` 暴露；检索为空时回退 local。

### P0-5 切片列表「伪分页」+ 全量加载 embedding，单次翻页可 OOM

`ruoyi-admin/src/main/java/com/ruoyi/web/controller/project/KnowledgeBaseController.java:247-253`

```java
List<KbChunk> all = kbDocumentImportService.listChunks(subjectId, chapterId, types);  // 全表
int total = all.size();
data.put("rows", from >= total ? new ArrayList<>() : all.subList(from, to));           // Java 里切片
```

`KbChunkMapper.xml:74-75` 的列清单带 `embedding`（1024 维浮点 JSON，每行约 10-20KB），且 SQL 只有 `limit` 没有 `offset`。当前库 2923 个切片 ≈ 30-60MB 堆内存/次，数据量涨到 1 万切片即 150MB+，`pageSize=50` 完全无效。

**修法**：XML 去掉 `embedding` 列；SQL 层 `limit #{offset}, #{pageSize}` + `count(1)` 分页。

### P0-6 初始化脚本 `knowledge.sql` 缺 14 张核心业务表，全新部署必然失败

实测 `knowledge.sql`（2026-10-10 导出）只有 36 张表，以下表**一张都没有**：

`kb_base`、`kb_document`、`kb_chunk`、`course_chapter`、`course_lesson`、`chapter_point`、`course_transcript`、`course_video_import`、`position`、`job_competency_graph`、`job_competency_weight`、`job_industry_chain`、`talent_job_posting`、`talent_major`

另外 `ai_chat_history` 缺 `position_id`、`knowledge_point` 缺 `chapter_id`，而 Mapper 已经在查这些列（`AiChatHistoryMapper.xml:10`、`KnowledgePointMapper.xml:28`）——这两列只存在于 `sql/migration_after_merge_20261028.sql`。

**影响**：新人按根目录 SQL 初始化后，课程学习、知识库、人才培养三大模块全部 `Table doesn't exist`；AI 对话历史报 `Unknown column 'position_id'`。

**修法**：`sql/` 收敛为单一 `schema.sql` + `data.sql`，用 Flyway/Liquibase 管版本；`knowledge.sql` 要么重新全量导出，要么在头部标注「仅历史备份，不可用于初始化」。

---

## P1 高危（安全）

### P1-1 MediaKitController 零权限 + 路径直传 → 任意文件读取

`ruoyi-admin/.../project/MediaKitController.java` 全类 5 个接口，**`@PreAuthorize` 数量为 0**：

```java
@PostMapping("/document")
public AjaxResult parseDocument(@RequestBody Map<String, Object> body) {
    return forward("/api/document", body);   // body.path 原样转发给本地 Python 服务
}

private AjaxResult forward(String endpoint, Map<String, Object> body) {
    if (body == null || body.get("path") == null || ...) { return error("缺少 path 参数"); }
    Map<String, Object> result = call(endpoint, body);   // 不做任何路径校验
```

`tools/media_kit/server.py:95` 直接 `payload.get("path")` 读文件，已注册 `.txt/.md/.html/.docx/.pdf/.pptx/.xlsx` 处理器并原样返回内容。任何登录用户（含学生）提交 `{"path":"C:/..."}` 即可读取服务器任意文本/文档。

**修法**：加 `@PreAuthorize`，并把 path 限制在 `ruoyi.profile` 目录内（`normalize().startsWith(uploadRoot)`）。

### P1-2 `/profile/**` 匿名 + 上传无后缀白名单 → 同源存储型 XSS

- `SecurityConfig.java:116`：`.antMatchers(HttpMethod.GET, ..., "/profile/**").permitAll()`
- `MediaKitController.java:277-301`：上传只做 `UUID + 原文件名`，无扩展名/MIME 校验
- `MimeTypeUtils.java:29-36`：通用白名单里含 `html`、`htm`

任何登录用户上传 `evil.html`，即可通过 `http://host/profile/media_kit/video/xxxxxxxx_evil.html` 匿名访问，浏览器按 `text/html` 渲染，可窃取管理员 `Admin-Token`（`utils/auth.js` 存的是非 httpOnly cookie）。

**修法**：上传按白名单校验；从 `MimeTypeUtils` 移除 `html/htm`；课程资源改走鉴权下载接口。

### P1-3 Druid 监控台匿名可访问 + 默认口令

- `SecurityConfig.java:117`：`/druid/**` 在 `permitAll` 列表里
- `application-druid.yml:44-51`：`statViewServlet.enabled: true`、`allow:`（空=不限来源）、`login-password: ${DRUID_PASSWORD:123456}`

未登录即可打开 `/druid/index.html`，口令缺省回落 `123456`，可看到全部 SQL、Session、数据源 URL 与 `root` 账号名。

**修法**：从 permitAll 移除；生产 `enabled: false`；口令强制走环境变量。

### P1-4 AI 对话历史水平越权（读/改/删/导出他人对话）

`ruoyi-admin/.../project/AiChatHistoryController.java`

| 方法 | 行号 | 是否覆盖 userId |
| --- | --- | --- |
| `list` | :44 | ✅ `setUserId(getUserId())` |
| `export` | :55 | ❌ 直接查全表 |
| `getInfo` | :66 | ❌ 无归属判断 |
| `edit` | :90 | ❌ userId 由请求体绑定 |
| `remove` | :101 | ❌ 无 user_id 过滤 |

`AiChatHistoryMapper.xml:83-111` 的 `update` / `delete` 都是 `where id = #{id}`，无 `user_id` 条件；而 `update` 的 SET 里含 `user_id = #{userId}`，攻击者还能把别人的记录改到自己名下。

**这不是设计如此**：同目录的 `UserKnowledgeAssessmentController`、`UserVideoWatchController`、`UserLearnRecordController` 全部做了 `!getUserId().equals(row.getUserId())` 校验，Mapper 里也都有 `and user_id = #{userId}`。只有 AI 对话历史漏了，属明显遗漏。

**修法**：照抄同类 Controller 的写法即可。

### P1-5 五个 Controller 完全无权限注解

实测权限注解覆盖率：

| Controller | 接口数 | 权限注解 |
| --- | --- | --- |
| `TalentController` | 15 | **0** |
| `JobCompetencyGraphController` | 7 | **0** |
| `LangChainWorkflowController` | 6 | **0** |
| `MediaKitController` | 5 | **0** |
| `AsrController` | 1 | **0** |
| `CourseVideoController` | 7 | 3 |
| `KnowledgeBaseController` | 7 | 6（`/list` 漏） |

其中破坏性/高成本操作：`TalentController.crawl/start`（拉起本机 Python 进程）、`TalentController.import`（写库）、`JobCompetencyGraphController.remove`（批量删除）、`JobCompetencyGraphController.review`（绕过审核直接置 approved）、`AsrController.transcribe`（每次消耗百炼额度）、`LangChainWorkflowController.invoke`（每次一次完整 LLM 工作流，无 `@RateLimiter`）。

**修法**：逐个补 `@PreAuthorize`，AI/ASR 类接口叠加 `@RateLimiter`。

### P1-6 JWT 签名密钥硬编码为字典串

`ruoyi-admin/src/main/resources/application.yml:151`

```yaml
token:
  secret: abcdefghijklmnopqrstuvwxyz
```

随仓库分发，任何人可离线签发结构合法的 JWT（实际危害受 Redis 中 uuid 校验限制，但密钥无法轮换，且一旦 uuid 泄露即可伪造）。

**修法**：`secret: ${TOKEN_SECRET:}`，长度 ≥ 64 随机字节。

### P1-7 真实 API Key 以「默认值」形式明文落盘

`config/application.yml`（已被 .gitignore 排除，但文件本身在工程目录里）

```yaml
api-key: "${DASHSCOPE_API_KEY:sk-ws-...真实key...}"
api-key: "${DEEPSEEK_API_KEY:sk-c8df45...真实key...}"
```

`${ENV:default}` 的写法意味着环境变量缺失时静默使用硬编码真实密钥，随工程拷贝/备份/打包即泄露。

**修法**：删除默认值（`${DASHSCOPE_API_KEY:}`），并到百炼/DeepSeek 控制台吊销已暴露的 Key。

### P1-8 全局异常把 SQL / 绝对路径回显给前端

`ruoyi-framework/.../GlobalExceptionHandler.java:96-113` 的 `RuntimeException` 分支直接 `AjaxResult.error(e.getMessage())`；业务代码里也大量 `return error("...：" + e.getMessage())`（如 `KnowledgeBaseController.java:257`、`CourseVideoController.java:93`）。SQL 异常会带回完整 SQL 与表名字段名，文件异常带回服务器绝对路径。

**修法**：对外统一「系统繁忙」，详细信息只进日志。

---

## P2 中危

### P2-1 软删除字段存在但列表查询不过滤（5 个 Mapper）

`KnowledgePointMapper.xml:31-46`、`SubjectMapper.xml:26-34`、`PositionMapper.xml:24-32`、`AiChatHistoryMapper.xml:28-41`、`UserKnowledgeAssessmentMapper.xml:32-46`

resultMap 里都映射了 `delFlag`，但 `selectXxxList` 的 `<where>` 里没有一条 `del_flag = '0'`。已软删的学科/知识点会出现在下拉框、导出、看板统计里（`KnowledgePointController.java:109`、`SubjectController.java:86` 用的是 `new Xxx()` 无参查询）。

同时 `deleteKnowledgePointByIds`（`KnowledgePointMapper.xml:120`）是**物理删除**，与 `CourseChapterMapper.xml:60` 的软删语义并存，一个项目两套删除策略。

### P2-2 `course_chapter` 唯一键不含 `del_flag`，软删后同章号无法再建 → 视频导入永久失败

`sql/migration_add_course_chapter.sql:43`：`UNIQUE INDEX uk_subject_chapter(subject_id, chapter_no)`

`CourseVideoImportService.java:383` 的 `selectCourseChapterList` 带 `del_flag='0'` 过滤 → 查不到已软删的章；`:408` 随即用相同 `chapter_no` 插入 → 撞唯一键 → `persistWithRetry` 重试 3 次仍失败 → 任务永久置为失败，用户重传也无法恢复，只能手工改库。

### P2-3 `course_transcript` 唯一键含可空列，约束实际失效

`sql/migration_lesson_transcript_key.sql:6`：`UNIQUE KEY uk_ct_lesson_seq (lesson_id, seq)`，而 `lesson_id` 是 `bigint NULL`。MySQL 唯一索引中 NULL 互不相等，`CourseVideoImportService.persist:1059` 在 `lessonId == null` 时插入的逐字稿全部可重复 → 同一章重复导入产生重复句子，时间轴出现重复。

### P2-4 删除学科漏删 `kb_document`，产生孤儿数据

`KbBaseService.java:124-131` 只删 `kb_base` 与 `kb_chunk`，不删 `kb_document`，而 `kb_document` 上也没有任何外键。删学科后文档记录永久残留，`stats()` 的文档数与实际不符。

### P2-5 `KbVectorStoreFactory` 用 `HashMap` 且在锁外读

`KbVectorStoreFactory.java:31`：`private final Map<String, Boolean> availabilityCache = new HashMap<>();`
`:77` `containsKey`、`:89` `get` 都在 `synchronized` 块外；`refresh():97-98` 的 `clear()` 完全无锁。并发下可能读到错乱桶链，表现为向量库可用性判断随机翻转。

### P2-6 `SubjectLockRegistry` 无超时，且锁内包住 embedding 远程调用

`SubjectLockRegistry.java:45`：`ref.lock.lock()` 无参阻塞，无 `tryLock(timeout)`。`CourseVideoImportService.java:646-670` 的锁内调用 `rebuildByLessons`（含 embedding HTTP），与类注释「锁只包住写库，不包 ASR/LLM 远程调用」直接矛盾。同学科并发上传时后续任务无限期阻塞，导入线程池只有 2 个线程 + 队列 8，满后 `CallerRunsPolicy` 会在 Tomcat 线程上同步跑数分钟。

### P2-7 `MilvusKbVectorStore.ensureCollection` 用 `synchronized` 包住 HTTP

`MilvusKbVectorStore.java:292-341`：`private synchronized void ensureCollection()` 内部有 `create` / `drop` / `describe` 三次 HTTP，且位于 `insert`/`search` 热路径。Milvus 不可达时（3s 连接超时）所有知识库请求串行阻塞。

### P2-8 `EmbeddingClient` / `RerankClient` 的 `RestTemplate` 懒初始化非线程安全

`EmbeddingClient.java:39`（非 volatile）+ `:247-259`（`if (restTemplate == null) { ... new RestTemplate() }`），`RerankClient.java:48`、`:172-179` 同。并发首调会创建多个实例，且无连接池（每次新建 TCP）。

### P2-9 rerank 配置 `top-n: 20` 形同虚设

`KbIngestService.java:367`：`List<KbChunk> fused = fuse(vectorHits, keywordHits, limit);` —— `fuse` 内部 `if (result.size() >= limit) break;`（`:758`）已经截断到 `limit`（默认 6），再传给 `RerankClient.rerank` 时 `candidateSize = Math.min(6, 20) = 6`。精排只能对最终 6 条重排，无法把 RRF 排名 7-20 的候选提上来。

**修法**：`fuse` 保留 `max(topK, rerank.topN)` 条，rerank 后再 `head(limit)`。

### P2-10 `keyword-min-score` 配置完全未生效

`KbProperties.java:320` 定义了 `keywordMinScore`，全项目**无任何一处调用** `getKeywordMinScore()`。ngram 全文命中的前 50 条无阈值全部塞进 RRF，两字查询的噪声会挤掉真正相关的片段。

### P2-11 两路召回对 `status` 口径不一致

`KbChunkMapper.xml:182` 的关键词召回带 `status = 1` 过滤，但 `MilvusKbVectorStore.java:162-166` 的 filter 只有 subject/chapter/sourceType，`LocalKbVectorStore` 的 `selectKbChunkIds` 也没有 status 条件 → 被停用的切片仍能通过向量召回进入上下文。

### P2-12 `topK` 无上限校验

`KnowledgeBaseController.java:142` → `KbIngestService.java:334`：`limit = topK == null || topK <= 0 ? default : topK`，直接透传给 Milvus 的 `limit`。`/project/kb/search?topK=1000000` 即可让 local 模式 PriorityQueue 撑到百万级，随后 `buildRagContext` 全部拼进 prompt。

### P2-13 连接池 20 与 Tomcat 800 线程严重不匹配

`application-druid.yml:24` `maxActive: 20` / `:26` `maxWait: 60000` vs `application.yml:85` `max: 800`。任何一次慢查询（`kb_chunk` 全表扫描、talent 聚合）都会让 780 个线程在 maxWait 上排队 60s 后批量抛异常。且未配 `removeAbandoned`，连接泄漏无法自愈。

---

## P3 低危

1. **上传 1GB + `getBytes()` 全量读堆**：`application.yml:115` `max-file-size: 1024MB`，`KnowledgeBaseController.java:205`、`TalentCsvImportService.java:278`、`AsrController.java:53` 都是 `file.getBytes()`。3 个并发 800MB 上传即 OOM。
2. **前端 ECharts tooltip 未转义（存储型 XSS）**：`ruoyi-ui/src/views/project/talent/index.vue:489/520/565/600`、`competency/index.vue:611-614/837-840` 的 formatter 直接拼 `skillName/majorName/industry`（来自 CSV 导入的爬虫数据）。对比 `topology/index.vue:537-544` 已经写了 `escapeHtml` 并注明「tooltip 走 innerHTML 必须转义」——同一个项目里两套做法，talent/competency 漏了。
3. **`request.js` 请求拦截器错误被吞**：`:77-78` `Promise.reject(error)` 缺 `return`，`config` 变成 `undefined` 继续往下走，抛出的错误变成无关的 `Cannot read properties of undefined`。`:103`/`:112` 用字符串 reject，业务层 `catch(e => e.message)` 拿不到内容。
4. **轮询定时器未清理**：`chapter/index.vue:670` 的 `setInterval(tick, 8000)` 全文件无 `onBeforeUnmount`；`ChapterManagePanel.vue:480` 同。上传后切页，定时器永久存活并对已卸载组件写 ref。
5. **伪分页**：`link/index.vue:227-247` 一次拉 `pageSize: 9999` 再前端过滤，分页控件点了数据不变；`chapter/index.vue:414` 客户端 `slice` 与服务端分页语义冲突。
6. **前端硬编码 `userId: 1`**：`learn/index.vue:1138/1196`、`ai-result.vue:786/866`、`result.vue:555/646`。当前后端 `add/edit/list` 都会用 `getUserId()` 覆盖，**暂无越权**，但属定时炸弹——一旦后端改成信任前端参数就立刻出错。
7. **`vite.config.js` 硬编码后端地址**：`:5` `const baseUrl = 'http://localhost:8081'`；`:15` 的 `base` 三元两边都是 `'/'`（死代码），配合 `createWebHistory()` 无 base，部署到子路径会全部 404。无 `manualChunks`，`chunkSizeWarningLimit: 2000` 只是把告警藏起来。
8. **`doc/RUNNING.md` 已过期**：文档称「AI 对话使用讯飞星火工作流，AK/SK 硬编码在 LearnController」，实际 `LearnController` 已改为 LangChain4j + 百炼；数据库口令仍写 `123456`。

---

## 数据实况核对（只读查询结果）

| 指标 | 数值 | 说明 |
| --- | --- | --- |
| 学科总数 | 24 | 1-20 为占位学科，92/93/94 为完整图谱 |
| 知识点总数 | 2573 | 94 占 1987、93 占 382、其余 204 |
| 知识点无 `chapter_id` | **379** | 无法进入「章节 → 知识点」学习路径 |
| 知识库切片 | 2923 | 全部属于学科 94 |
| 知识库文档 | 1896 | point 1781 / transcript 50 / quiz 49 / chapter 15 / **document 仅 1** |
| 学科 93 的切片 | **0** | 有 382 个知识点，但从未入库，学习路径会检索不到内容 |
| 孤儿数据 | 0 | 文档↔切片一致性好，这点做得对 |
| 无向量切片 | 0 | local 模式下 embedding 齐全 |

> 注：`du -sh fiveEi` = 128MB，且当前有 344 个文件（含 BOSS 直聘原始爬取 CSV）已 `git add` 暂存。数据文件进 Git 会永久留在历史里，建议 `git reset` 后改用 LFS 或外部存储。

---

## 已核查确认**无问题**的项

避免后续重复排查：

- **SQL 注入**：`ruoyi-project` 下全部 Mapper XML 与注解 SQL 均为 `#{}` 参数化，无 `${}` 拼接。仅有的 `${params.dataScope}` 由 `DataScopeAspect` 的 `clearDataScope()` 保护，`orderBy` 经 `SqlUtil.escapeOrderBySql` 白名单。
- **`@Anonymous`**：全仓库零使用。
- **文件下载路径穿越**：`CommonController.java:50/143` 的 `FileUtils.checkAllowDownload` 同时拦 `..` 与扩展名白名单。
- **视频上传**：`CourseVideoImportService.java:246-262` 有后缀白名单 + `getFileName()` 去目录。
- **`v-html`**：3 处全部安全（静态常量或先 `escapeHtml` 再插标签）。
- **命令执行**：`ProcessBuilder` 仅 `TalentCrawlService` 三处，脚本路径来自配置，用户不可控。
- **空 catch 块**：全量扫描零命中。
- **切片入库事务**：`KbChunkTxService` 用独立 Bean 规避了自调用事务失效，批量插入按 50 条分批，这块设计是对的。

---

## 建议的修复顺序

**第一批（半天，止血）**
1. P0-1 rebuild 加来源白名单 —— 防止误删数据
2. P1-1 MediaKit 加权限 + 路径白名单 —— 堵住任意文件读取
3. P1-3 关闭 Druid 匿名访问
4. P1-4 AI 对话历史补归属校验（照抄同类 Controller）
5. P0-3 把 LLM 调用移出 `@Transactional`

**第二批（2-3 天，稳定性）**
6. P0-2 导入改为「先写新、后删旧」
7. P0-5 切片列表去掉 embedding + 真分页
8. P2-13 连接池调到 50-100 + `maxWait` 降到 3-5s
9. P1-5 五个 Controller 补权限注解
10. P2-6 / P2-7 去掉锁内远程调用

**第三批（一周，正确性）**
11. P2-1 五个 Mapper 补 `del_flag = '0'`
12. P2-2 / P2-3 唯一键与软删策略统一
13. P2-9 / P2-10 修 rerank 截断与关键词阈值
14. P0-6 收敛 SQL 脚本，引入 Flyway

**第四批（排期）**
15. P1-6 / P1-7 密钥全部外置并轮换
16. P3-2 前端 tooltip 统一转义
17. 补核心链路的单元测试（当前只有 9 个测试类，`KbIngestService` / `KbDocumentImportService` / `CourseVideoImportService` 三个最复杂的类零覆盖）
