# 知识库 + 课程学习 功能代码审查报告

> 项目：RuoYi-Vue 3.9.0
> 技术栈：Spring Boot + MyBatis + MySQL + Milvus + Vue 3.5 + Element Plus 2.9 + Pinia + Vite 6
> 审查范围：`ruoyi-project`（约 1.5 万行 Java）、`ruoyi-admin` 下 project 包 Controller、`ruoyi-ui/src/views/project`（约 1.3 万行 Vue）、`config/application.yml`
> 审查日期：2026-10-27
> 说明：本次仅审查，未改动任何源码。

---

## 一、总体评价

功能完整度高，知识库 RAG、视频导入→ASR→章节切分→知识点抽取→思维导图→练习题的链路是通的，前端交互也做得比较细。但**工程健壮性是明显短板**：事务边界、权限模型、远程调用失败处理这三块基本是「按 demo 写的」，一旦多用户并发或上游模型抖动就会出数据问题。

问题分布：

| 级别 | 数量 | 典型后果 |
|---|---|---|
| 高危 | 12 | 全表误删、越权、OOM、数据永久丢失、存储型 XSS |
| 中危 | 16 | 性能退化、数据漂移、前端卡死、体验缺陷 |
| 低危 | 10 | 可维护性 |

**最该先处理的三件事**：`/project/kb/clear` 可清空整张表、学习记录 userId 硬编码 1、视频导入整段音频进内存。

---

## 二、高危问题（上线即出事）

### H1. 知识库「清空」接口可以清空整张 kb_chunk 表 ✅已核验

- `ruoyi-project/src/main/resources/mapper/project/KbChunkMapper.xml:74-83`
  ```xml
  <delete id="deleteKbChunkByFilter">
      delete from kb_chunk
      <where>
          1 = 1
          <if test="subjectId != null"> and subject_id = #{subjectId}</if>
          ...
  ```
- `ruoyi-admin/.../KnowledgeBaseController.java:159-161` 三个参数全部 `required = false`，没有任何「至少一个条件」校验。
- 触发路径：前端 `kb/index.vue:395` 的「全部知识库」行 `subjectId` 为 null，点清空 → 四个 `<if>` 全不成立 → SQL 退化为 `delete from kb_chunk`。
- 更糟：`MilvusKbVectorStore.java:208-215` 有 `if (!filter.isEmpty())` 保护 → **MySQL 被清空、Milvus 向量全留**，之后检索命中的全是空 content。

修复：Controller 强制 `subjectId != null`；Mapper 去掉 `1 = 1`，改在 `<where>` 内先拼 `subject_id is not null`；`clear` 补 `@Transactional`。

### H2. Milvus filter 表达式字符串拼接 → 可扩大删除范围

- `MilvusKbVectorStore.java:428-435`：`quoted.add("'" + type + "'")`，`type` 直接来自 `KnowledgeBaseController.java:161` 的 `sourceType` 请求参数。
- 传 `x' or subject_id > 0 or source_type == 'x` 即可绕过学科限制做跨库删除/检索。

修复：`sourceType` 走白名单（`chapter` / `point` / `transcript` / `quiz`），非法值直接抛错。

### H3. 学习记录越权 + userId 硬编码 1 ✅已核验

- 前端 6 处写死 `userId: 1`：
  `learn/index.vue:1081,1140`、`learn/result.vue:382,473`、`learn/ai-result.vue:871,953`
  （`result.vue:382` 的注释自己都写了「实际应该从 store 取」）
- 后端 `UserVideoWatchMapper.xml:23-33`：`user_id` 只是可选筛选条件，不传就返回全表。
- `UserVideoWatchServiceImpl.java:54-58`：`insert` 不写 `userId`，完全信任前端传值。
- 结果：多账号下所有学习记录/测评/观看进度都写到 1 号用户，互相污染；任意登录用户可通过 `/project/watch/list` 不传参拿到所有人的记录。

修复（学生自助 + 管理员代管并存）：
- 学生端接口（`/project/learn/**`、`/project/course/**`、`/project/watch/**`）在 Service 层强制 `setUserId(SecurityUtils.getUserId())`，忽略前端传值；
- 管理端拆独立路径（如 `/project/watch/manage/list`），带 `project:watch:list` 权限，才允许按 `userId` 查询；
- `update`/`delete` 前校验 `userId` 归属，防止改他人记录。

### H4. 三个付费 LLM 接口没有任何权限注解 ✅已核验

`CourseStudyController`、`CourseMindmapController`、`LearnController` 的 `@PreAuthorize` 数量为 **0**（其余 Controller 都有 7 个）。涉及：

| 接口 | 行号 | 风险 |
|---|---|---|
| `POST /project/course/chat` | CourseStudyController:37 | 任意登录用户无限调用大模型 |
| `GET /project/course/models` | :65 | 泄露模型配置 |
| `GET /project/course/quiz` | :85 | 拉取题库 |
| `POST /project/learn/chat` | LearnController:35 | 无限调用 LangGraph 工作流 |
| `/project/mindmap/**` | CourseMindmapController:35,55,90 | 可写知识图谱 |

修复：补 `@PreAuthorize`，并对 LLM 接口加 Redis 限流（如 20 次/人/小时）。

### H5. 视频导入把整段音频读进内存 → OOM ✅已核验

- `CourseVideoImportService.java:273`：`transcriber.transcribe(Files.readAllBytes(Paths.get(parsed.audioPath)))`
- 下游 `WavAudio.java:109/143/158` 把 `byte[]` 再转 `float[]`（放大 4 倍），`toPcm16Le`/`toWavBytes`/`ByteArrayOutputStream` 又多次整段拷贝。
- 1 小时视频单任务峰值约 600MB+，而线程池是 `Executors.newFixedThreadPool(2)`（`:120`）→ 两个任务并发必 OOM。

修复：改流式——`WavAudio` 提供基于 `FileChannel` 的分片读取，`AsrService` 签名从 `byte[]` 改为 `Path + offset + length`。这是本次改动量最大的一项。

### H6. 视频导入持久化没有事务 ✅已核验

`CourseVideoImportService.java` 全文件 **无 `@Transactional`**（`:286,579,596,600,630,655` 附近）。`persist()` 先删逐字稿/章节知识点、再 insert，中途失败即「旧数据已删、新数据没写」。

修复：抽独立 Bean 方法加 `@Transactional(rollbackFor = Exception.class)`（同类自调用事务不生效，必须跨 Bean）。

### H7. 知识库重建在事务内「先删后向量化」→ 失败即永久丢库 ✅已核验

- `KbIngestService.java:93` `@Transactional` → `:111` `store.clear(...)` → `:186` `embedBatch(...)`
- MySQL 侧能回滚，但 **Milvus 的 delete 已在远端生效且不可回滚**。embedding 一报错，库就空了。

修复：先算完向量、再在同一事务里 `delete + insert`；或改成版本化 `docId` 的 upsert-then-purge。

### H8. 删除学科不清向量库 ✅已核验

`KbBaseService.java:87-94` 只删 `kb_base` + `kb_chunk`，**没有注入 `KbVectorStoreFactory`**，Milvus 里该学科向量永久残留。`SubjectServiceImpl.java:96-113` 也无 `@Transactional`。

修复：`removeBySubjectIds` 内调 `store.clear(subjectId, null, null)`；删除方法补事务；循环删除合并为一条 `in`。

### H9. 双写无补偿 → MySQL 与 Milvus 必然漂移

`MilvusKbVectorStore.java:97-103` 先写 MySQL、`:121-125` 再写 Milvus，后者失败时前者已提交。反向：`:151-176` 检索后回填原文，若原文缺失则 `title/content/startTime` 全 null，但 `CourseStudyServiceImpl.java:102` 仍标记 `strategy="rag"` → 模型拿到空上下文还以为是 RAG 结果。

修复：写 Milvus 失败时补偿删除 MySQL 行并抛出；或加按 `subject_id` 的定时对账任务。

### H10. 并发处理同一学科 → 重复章号/题号/知识点

`CourseVideoImportService.java:120` 2 线程池 + `nextChapterNo`/`sortOrder`/`max(question_number)`/`findOrCreatePoint` 全部「先查后写」。两个视频同时导入同一学科会撞号。

修复：按 `subjectId` 串行（单线程队列或 Redis 锁）+ 表加唯一索引兜底。

### H11. 存储型 XSS（ECharts tooltip）✅已核验

`course/index.vue:1538-1543`：
```js
formatter: params => {
  const point = params.data?.point
  return point ? `<strong>${point.name}</strong><br/>${point.content || ''}` : ...
}
```
`point.content` 来自 `syncMindmap` 把 LLM 输出写库的内容，ECharts 用 innerHTML 渲染 tooltip，hover 即执行。`topology/index.vue:349-364` 同样问题。

> 说明：`course/index.vue:289` 的 `v-html` 前面有 `escapeHtml`，**不构成 XSS**，这条是误报，已排除。

修复：formatter 内每个插值都 `escapeHtml`，或改 `tooltip.renderMode: 'richText'`。

### H12. 密钥明文落盘（未进 git，但会随压缩包外发）✅已核验

`config/application.yml:6,16,23,28` 有 4 处明文 key（`sk-ws-H.YHMRPLX...`、`sk-c8df4556...`）。文件头注释自称「请勿提交到代码仓库」——已确认 `git ls-files config/` 为空，**没进 git**，但同目录存在 `Knowledge.zip`（160MB），一旦外发即泄露。

修复：改 `${DASHSCOPE_API_KEY:}` 环境变量；`.gitignore` 显式排除 `config/`；建议轮换这两把 key。

---

## 三、中危问题

### 数据正确性

| # | 位置 | 问题 |
|---|---|---|
| M1 | `KbIngestService.java:164` | `testId == null ? 102L : testId` 魔法默认值；前端 `kb/index.vue:239,323,345,377` 也写死 102、`subjectId === 94`。给任意学科重建都会把「计算机网络第1章」的题写进该学科知识库。 |
| M2 | `learn/result.vue:326-336` | 完成度用「全局记录数」除以「本岗位知识点数」，多岗位用户必然 >100%，`el-progress` 会告警，"待学习"变负数。 |
| M3 | `learn/result.vue:313-320,348-351` | `filteredSubjects` 是 map 出的新数组，`toggleSubject(idx)` 却写 `subjects.value[idx]._expanded` → 搜索状态下折叠错乱。 |
| M4 | `learn/index.vue:1202-1211` | 「清空所有记录」实际只删当前页 10 条（`historyList` 是分页结果）。 |
| M5 | `learn/index.vue:663-667` | 生成后用 `historyList.value[0].id` 跳转，用户停在第 2 页时跳错记录；应用 `saveRes.data.id`。 |
| M6 | `learn/index.vue:945-956`、`ai-result.vue:1138-1148` | 判卷把「选项下标（数字）」与 AI 返回的 `answer[0]` 直接 `===`，AI 若返回 `"A"` 则全卷判错；`index: q.id - 1` 在 `q.id` 非数字时为 NaN，显示"第 NaN 题"。（实际返回格式**待确认**） |
| M7 | `KbIngestService.java:130` | `chapter.getChapterNo() + " " + nullToEmpty(...)`，`chapterNo` 为 null 时标题变成 `"null 第一章"`。 |
| M8 | `MilvusKbVectorStore.java:167-169` | `asLong(0L)` 把 null 的 chapterId/pointId 变成 0，前端按 0 查章节落空。 |

### 性能

| # | 位置 | 问题 |
|---|---|---|
| M9 | `LocalKbVectorStore.java:74,82` | 每次检索 `selectKbChunkList(..., null)` 拉该学科**全部**行（含 1024 维 embedding ≈10KB/行），1 万 chunk ≈100MB 进堆，且逐行 `readValue` 串行解析。 |
| M10 | `learn/index.vue:549-563`、`profile.vue:183-213` | 双层循环内 `await listPoint({pageSize:9999})`：10 岗位 × 3 学科 = 30 次串行全量查询，进页面卡数十秒且无 loading。 |
| M11 | `KbIngestService.java:137-150` | 每个章节一次 `selectChapterPointList` → N+1。 |
| M12 | `CourseMindmapServiceImpl.java:675` + `KnowledgePointMapper.xml:34` | 每个知识点一次 `name like '%x%'`，无法走索引。 |
| M13 | `CourseVideoImportService.java:186`（经 `EmbeddingClient.java:146`） | 事务内串行调用 embedding，`batchSize=10`，500 块 = 50 次往返，全程持有 DB 事务与行锁，分钟级长事务。 |
| M14 | `course/index.vue:1143-1157` | `transcriptContext` computed 依赖 `currentTime`（4Hz），每秒重建几十 KB 字符串 4 次。 |
| M15 | `course/index.vue:1163-1171` | 每句高亮都 `scrollIntoView({behavior:'smooth'})` 两个容器，会滚动所有祖先节点，播放时整页被拽动。 |
| M16 | `CourseTranscriptMapper.xml:28-34` | 单条 INSERT 拼全部行，2000+ 行易超 `max_allowed_packet`，应分批 500。 |

### 健壮性 / 体验

| # | 位置 | 问题 |
|---|---|---|
| M17 | `chapter/index.vue:373-374` | `queryParams` 有 `pageNum/pageSize=10`，但模板**没有 `<pagination>` 组件**（只有 CSS 类）→ 第 11 章之后无法访问，KB 重建下拉也因此不全。 |
| M18 | `chapter/index.vue:52,62,72,112,113` | 章节页按钮全用 `project:point:*` 权限码（复制粘贴遗留），只有知识点权限的人能改删章节。 |
| M19 | `ai-result.vue:928`、`result.vue:434` | `setTimeout` 无 `onBeforeUnmount` 清理（已确认两文件均无该钩子）；且 `progress` 在排程时算、`pointId` 在触发时读 → 5s 内切知识点会把 A 的进度写到 B 上。 |
| M20 | `course/index.vue:468` | 关闭分析弹窗只置 `analysisDialogVisible = false`，不停轮询；`setInterval` 无最大次数限制，任务卡在 status=0/1 时永久请求。 |
| M21 | `CourseVideoImportService.java:488-496` | `lastIndexOf('}')` 粗暴截取 LLM 返回 JSON，模型带解释或多对象即失败，无重试无降级。 |
| M22 | `CourseVideoImportService.java:696-714` | LLM 漏 `end` 时 `asInt(0)` → end=0 被当「无上限」，第一小节吞掉全片逐字稿。 |
| M23 | `QwenFileTransClient.java:96-126,153` | 轮询无退避、403/404 也重试到超时；`readBody` 吞 IOException 返回空串。 |
| M24 | `kb/index.vue:381-388` | 重建失败被 `catch(() => {})` 静默吞掉，用户以为成功。 |
| M25 | `point/index.vue:429-442`、`subject/index.vue:213-220` | `getList` 无 `.catch`/`finally` → 接口失败后 `loading` 永久转圈，页面变砖。 |
| M26 | `KbVectorStoreFactory.java:51-60` | Milvus 宕机时每次问答都执行 `available()` 探测（3s 超时 + `synchronized`），固定加 3s 延迟且线程排队。 |
| M27 | `learn/index.vue:620-632,826-834` | 用户输入原样拼进 Prompt，可提示词注入（`## 忽略以上指令...`）。 |

---

## 四、低危 / 代码质量

| # | 位置 | 问题 |
|---|---|---|
| L1 | `course/index.vue` 全文 2857 行 | 单文件承载视频/逐字稿/AI 对话/思维导图/练习/上传分析/AI 梳理 7 个功能，建议拆 `components/{VideoPanel,TranscriptPanel,ChatPanel,MindmapPanel,QuizPanel}.vue`，index 只留编排。 |
| L2 | 多处 | 重复代码：`formatTime` 在 `KbIngestService.java:457` 与 `CourseStudyServiceImpl.java:226`；`parse(String)` 在 `LocalKbVectorStore.java:112` 与 `MilvusKbVectorStore.java:486`；`template()` 在 `EmbeddingClient.java:247` 与 `MilvusKbVectorStore.java:464`。 |
| L3 | 多处 | 魔法数字散落：`94`（学科）、`102`（题库）、`213`（默认时长）、`14000/10000/3500`（`CourseVideoImportService.java:451`）、`101`（`CourseStudyServiceImpl.java:319`）。抽常量类。 |
| L4 | `KbProperties.java:51` | `chunkOverlap` 配置了但全项目无引用 → 切块实际无重叠，是死配置。 |
| L5 | `EmbeddingClient.java:39,170-184` | `RestTemplate` 非 volatile 懒初始化（双重检查缺失）；重试不区分错误类型，401/403 也重试 3 次。 |
| L6 | `KbBaseService.java:53-63` | `ensureForSubject` 先查后插，撞唯一键 `uk_kb_base_subject` 会抛 `DuplicateKeyException` 导致学科创建失败。 |
| L7 | `course/index.vue:605`、`kb/index.vue:222`、`chapter/index.vue:340` | 同一份 44KB `network-transcript.json` 三处 import，`segment.start/1000` 换算逻辑重复 3 份。 |
| L8 | `router/index.js:162-200` | 静态路由的 `permissions` 字段不生效（`beforeEach` 只校验 token），`/project/learn/result`、`/project/learn/ai-result`、`/project/learn/profile` 任何登录用户可直达。前端不能当鉴权，需后端接口兜底。 |
| L9 | `learn/result.vue:96` | `prefix-icon="Search"` 传字符串，EP2 需组件对象，图标不显示。 |
| L10 | `CourseStudyServiceImpl.java:84-198` | RAG `ask()` 全实现无任何调用方（死代码），实际 `/chat` 用前端传的 context（可被注入）。 |

---

## 五、架构层面优化建议

1. **双存储一致性**（最值得做）：MySQL 与 Milvus 现在是「尽力双写」，建议引入 `kb_doc_version` 概念——每次重建生成新版本号，检索只查 `version = current`，旧版本由定时任务清理。这样重建失败也不影响线上可用，彻底解决 H7/H9。
2. **长任务异步化规范化**：视频导入目前是自建 `newFixedThreadPool(2)`，无 `@PreDestroy`、无界队列、重启后任务永久卡在 status=1。建议统一走 Spring `ThreadPoolTaskExecutor`（有界 + 拒绝策略），启动时把未完成任务置失败。
3. **远程调用统一治理**：embedding / ASR / LLM 三处各自 new `RestTemplate`、各自写重试。建议抽一个 `RemoteCallTemplate`，统一超时、指数退避 + 抖动、按 5xx/超时重试、4xx 直接失败、埋点耗时。
4. **权限模型分层**：学生 + 管理员并存时，建议明确三档——`公开`（登录即可，userId 强制从 token 取）、`本人`（校验归属）、`管理`（`project:*:*` 权限码 + 可按 userId 查）。目前是混在一起的。
5. **切分参数真正生效**：`chunkSize`/`chunkOverlap` 现在 overlap 是死配置，检索召回质量受影响，建议实现尾部重叠。

---

## 六、建议修复顺序

| 批次 | 内容 | 改动量 | 说明 |
|---|---|---|---|
| 第 1 批 | H1 全表删除、H2 filter 注入、H12 密钥 | 小（各 5-10 行） | 单请求即可触发，风险最高收益最大 |
| 第 2 批 | H3 越权、H4 权限注解 | 中 | 涉及 Controller + Service 分层改造 |
| 第 3 批 | H6/H7/H8 事务与清理顺序 | 中 | 数据一致性，改动集中在 3 个 Service |
| 第 4 批 | H11 XSS、M19 定时器、M20 轮询 | 小 | 前端三处，10 行以内 |
| 第 5 批 | H5 流式音频 | 大 | 需改 `WavAudio`/`AsrService` 接口，建议单独分支 |
| 第 6 批 | H9/H10 一致性补偿与并发串行 | 中 | 建议配合第 1 条架构优化一起做 |
| 第 7 批 | M1-M27 逐项、L1-L10 清理 | 分散 | 可穿插在日常迭代 |

---

## 七、已排除的误报（供参考）

| 说法 | 实际 |
|---|---|
| `task.status === '2'` 因类型不匹配导致死循环 | **误报**。`CourseVideoImport.java:29` 中 `status` 是 `String`，字符串比较正确。真实问题是「无最大重试次数 + 关弹窗不停轮询」。 |
| `course/index.vue` 轮询定时器泄漏 | **部分误报**。`:1188` 的 `onBeforeUnmount` 已调用 `stopAnalysisPolling()`，卸载是干净的。 |
| `course/index.vue:289` 的 `v-html` 存在 XSS | **误报**。前面有 `escapeHtml` 先转义再插标签。真正的 XSS 在 ECharts tooltip（H11）。 |
| 项目是 Vue 2 + Element UI | **误报**。`package.json` 为 Vue 3.5.16 + Element Plus 2.9.9 + Pinia 3 + Vite 6。 |
