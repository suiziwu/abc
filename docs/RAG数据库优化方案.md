# RAG 数据层优化方案（对标阿里云百炼知识库）

> 项目：`Knowledge`（若依 3.9 + MySQL 8.0.45 + Milvus 2.5 + text-embedding-v3）
> 日期：2026-10-28
> 范围：`kb_base` / `kb_chunk` / Milvus `course_kb` 集合，以及入库与检索链路

---

## 一、结论摘要

本次改造把原来「知识库 + 分块」两层扁平结构，升级为百炼同款的
**知识库 → 文档 → 切片** 三层模型，并补齐元数据、全文索引、增量入库与双路召回。

| 指标 | 改造前 | 改造后（含学科 94 重建） |
|---|---:|---:|
| MySQL 分块 | 87 | 77 |
| 文档记录 | 无此概念 | 53 |
| Milvus 存活向量 | 105 | 77 |
| 孤儿向量（有向量无原文） | 18 | 0 |
| Milvus 墓碑行（含已删） | 329 | 已触发清理 |
| 分块索引数 | 5（含 3 个低效单列） | 4 + 1 个 ngram 全文索引 |
| 检索方式 | 纯向量 | 向量 + 关键词双路 RRF 融合（可选 rerank） |
| 重建成本 | 每次全量向量化 | 仅变化文档重新向量化 |

切片数从 87 降到 77 不是丢数据：逐字稿窗口从 320 字符放大到 600 字符后，
26 个窗口合并为 16 个，单块上下文更完整；题库仍是 10 题，知识点 50 条一条不少。

实测：学科 93 第 1 章首次入库 39 条切片，二次重建 **0 次向量化、39 条全部命中增量**；
学科 94 重建后二次重建同样 **0 次向量化、77 条全部命中增量**。

---

## 二、百炼知识库的数据层是怎么做的

参考阿里云百炼官方文档（知识库创建、切片与向量化、知识检索，2026-10 版）：

### 1. 三层实体模型

```
知识库（索引配置 + 向量模型 + 检索参数）
  └── 文档（一次导入的源文件，带解析状态、标签、来源）
        └── 切片（检索的最小单元，带页码/标题路径等元数据）
```

这层「文档」不是可有可无的装饰：删除文档、重解析、按标签过滤、统计导入进度、
按文档维度做增量更新，全都依赖它。只有切片表时，这些都只能用 `doc_id` 字符串硬凑。

### 2. 切片策略

| 切片方式 | 适用场景 | 百炼建议 |
|---|---|---|
| 智能切分（默认） | 大部分场景，自动识别章节/段落边界 | 600 token |
| 按长度 | FAQ、问答对 | 256 token |
| 按标题 | 合同、手册等层级清晰的文档 | 512 token |
| 按页 | 每页一个独立主题 | — |
| 按正则 / 按符号 | 有固定分隔标记的文本 | — |

最大分段长度范围 10–6000 token，默认 600。**换 embedding 模型会触发全量重建**。

### 3. 检索链路（四步）

```
Query 改写（可选）
   ↓
向量检索 TopK 1~100  ∥  关键词检索 TopK 1~100     ← 两路并行召回
   ↓
Rerank 精排（qwen3-rerank，问答模式 / 相似模式）
   ↓
相似度阈值过滤（0.01~1.0）→ 最大召回 1~20 条
```

关键点：
- **混合检索不是「二选一」**，而是两路各召回一批，再统一精排；
- 只开向量检索时，专有名词、缩写、编号这类「精确匹配」需求召回很差；
- 相似度阈值作用在**精排之后**，不是召回阶段；
- 多知识库场景支持路由与权重，单次最多绑定 15 个库。

### 4. 元数据

百炼用「文档标签」做检索范围收窄（metadata filtering）。对应到本项目的自然映射是
`subject_id / chapter_id / source_type / section_path`，其中 `section_path`
（如「第4章 树 > 4.4 二叉树 > 4.4.2 遍历」）既可用于过滤，也可直接拼进上下文帮助模型定位。

---

## 三、改造前的实测诊断

### 1. 结构缺层

只有 `kb_base`（知识库）和 `kb_chunk`（切片），没有文档层。`doc_id` 字段同时承担了
「文档标识」和「切片标识」两个职责，导致语义混乱：

- `chapter:10` —— 文档级
- `point:11001` —— 文档级
- `transcript:94:10:3` —— 切片级（窗口序号被写进了 doc_id）
- `quiz:999:39` —— 切片级（题目 ID 被写进了 doc_id）

### 2. `chunk_index` 语义错误（最严重）

`KbIngestService` 里 `chunk_index` 取的是 `chunks.size()`，也就是**整个批次的全局序号**，
不是「文档内序号」。后果：

- 唯一键 `uk_kb_doc_chunk(doc_id, chunk_index)` 形同虚设——同一文档每次重建都会换一个序号；
- 章节/知识点/题目本应只有一个切片，却拿到了 `#0`、`#1`、`#30` 这种编号；
- 一旦某章新增一个知识点，后面所有文档的序号整体平移，`ON DUPLICATE KEY` 全部落空，
  只能靠「重建后删陈旧」兜底，容易积累重复行。

实测数据里 `point:11030#30`、`quiz:999:39#10` 就是这个问题留下的痕迹。

### 3. 向量库与原文表漂移

| 项 | 数值 |
|---|---:|
| Milvus `get_stats` rowCount | 329 |
| Milvus 实际存活（query count） | 105 |
| MySQL `kb_chunk` | 87 |
| 孤儿向量（subject_id=98，学科已删） | 18 |
| 缺失向量 | 0 |

224 条是删除后未压缩的墓碑行；18 条是学科删除时向量没清干净留下的孤儿。
检索侧虽然会丢弃回填不到原文的命中，但每次检索都要多做一次无效回表。

### 4. 索引设计

改造前 5 个二级索引：`uk_kb_doc_chunk`、`idx_kb_subject`、`idx_kb_chapter`、
`idx_kb_point`、`idx_kb_type`。

其中 subject/chapter/type 三个单列索引被同一个查询同时使用（`WHERE subject_id=? AND chapter_id=? AND source_type=?`），
MySQL 只能选一个，其余两个纯属写放大。**没有全文索引**，中文关键词召回无从谈起。

### 5. 检索参数

| 参数 | 改造前 | 百炼建议 | 问题 |
|---|---|---|---|
| top-k | 5 | 精排后 1~20 | 尚可 |
| 召回候选 | 5（等于 topK） | 50~100 | 候选池太小，rerank 无从发挥 |
| min-score | 0.2（余弦） | 0.01~1.0（精排分） | 阈值作用在召回阶段，偏低 |
| 混合检索 | 无 | 向量 + 关键词 | 专有名词召回弱 |
| Rerank | 无 | qwen3-rerank | 排序精度受限 |
| 去重 | 无 | — | 同一内容多来源时会重复占上下文 |

### 6. 存储开销

`embedding` 以 JSON 文本存 `mediumtext`，1024 维约 **12.9 KB/行**（实测 87 行 1.1 MB）。
按 1 万切片估算约 130 MB，且每次检索都要 JSON 反序列化。Milvus 模式下这份数据其实是冗余的
（只在 `provider=local` 回退时才用得上）。

---

## 四、改造方案

### 1. 三层模型落地

```
kb_base（知识库，与学科一对一）
  └── kb_document（文档，doc_key 唯一）
        └── kb_chunk（切片，doc_pk 外键 + chunk_index 文档内序号）
```

`doc_key` 取值规则（**文档级，不含窗口/题目序号**）：

| 语料 | doc_key | chunk_index |
|---|---|---|
| 章节 | `chapter:{chapterId}` | 0 |
| 知识点 | `point:{pointId}` | 0 |
| 视频逐字稿 | `transcript:{subjectId}:{chapterId}` | 0..n（窗口序号） |
| 题库 | `quiz:{testId}` | 0..n（题目序号） |

### 2. 切片表新增字段

| 字段 | 类型 | 用途 |
|---|---|---|
| `kb_id` | bigint | 归属知识库 |
| `doc_pk` | bigint | 归属文档 |
| `section_path` | varchar(500) | 章节路径，用于过滤与上下文标注 |
| `page_start/page_end` | int | 教材页码（预留） |
| `token_count` | int | 上下文预算（按 1.5 字符/token 粗估） |
| `content_hash` | char(32) | 内容指纹，增量入库 + 去重 |
| `status` | tinyint | 1 启用 / 0 停用（软下线，不删数据） |
| `update_time` | datetime | 变更时间 |

### 3. 索引重设计

| 索引 | 字段 | 服务的查询 |
|---|---|---|
| `idx_kb_scope` | (subject_id, chapter_id, source_type) | 按学科/章节统计、按类型清空 |
| `idx_kb_doc_pk` | (doc_pk, chunk_index) | 按文档回填、按文档重建 |
| `idx_kb_hash` | (content_hash) | 去重、增量比对 |
| `ft_kb_text` | (title, content) WITH PARSER ngram | 中文关键词召回 |

删除 `idx_kb_subject`、`idx_kb_chapter`、`idx_kb_type`（被 `idx_kb_scope` 前缀覆盖）。
`EXPLAIN` 确认关键词查询走 `ft_kb_text`：

```
type=fulltext  key=ft_kb_text  Extra=Using where; Ft_hints: sorted
```

### 4. 增量入库

重建时先读现有切片的 `content_hash`，按文档聚合比对：

- 指纹与切片数都一致 → 跳过，不调用 embedding；
- 任一变化 → 先算新向量（失败则旧数据原样保留），成功后再「先删后写」该文档。

「先删后写」同时解决了切片数变少时旧序号残留的问题。

### 5. 混合检索 + RRF + 可选 Rerank

```
向量召回 candidateK=50 ─┐
                        ├─ RRF(k=60) 融合 → content_hash 去重 → topK
关键词召回 candidateK=50 ┘                                      ↓
                                                     rerank（默认关闭）
```

- 用 RRF 而不是加权求和：余弦相似度与 MySQL 全文相关度量纲完全不同，按排名融合更稳；
- 关键词查询用 `NATURAL LANGUAGE MODE`，避免用户输入 `+ - *` 触发布尔语法错误；
- rerank 失败自动退化为融合顺序，不影响问答主链路。

---

## 五、已落地的改动

### 数据库

| 文件 | 内容 |
|---|---|
| `sql/migration_kb_rag_v2.sql` | 幂等迁移：备份、建 `kb_document`、加 9 个字段、重建索引、回填历史数据、健康视图 |

已执行并验证幂等（重复执行输出 `already up to date`）。备份表：
`kb_chunk_bak_20261028`、`kb_base_bak_20261028`。

### 代码

| 文件 | 改动 |
|---|---|
| `domain/KbChunk.java` | 新增 9 个元数据字段 + `vectorKey()` |
| `domain/KbDocument.java` | 新增文档实体 |
| `mapper/KbDocumentMapper.java` + XML | 文档 upsert（`LAST_INSERT_ID(id)` 回填主键）、按条件删除 |
| `mapper/KbChunkMapper.java` + XML | 新增关键词召回、指纹查询、按 docPk 查询；插入语句补齐字段 |
| `kb/KbIngestService.java` | 三层构建、`chunk_index` 改为文档内序号、增量入库、RRF 融合 |
| `kb/RerankClient.java` | 新增，百炼 qwen3-rerank 兼容，默认关闭、失败降级 |
| `kb/KbProperties.java` | 新增 `kb.retrieval.*`、`kb.rerank.*` |
| `kb/store/MilvusKbVectorStore.java` | 命中回填改为整行返回，元数据不再丢失 |
| `course/service/CourseStudyServiceImpl.java` | 去掉重复的 min-score 过滤（会误杀关键词命中），上下文加章节路径 |
| `mapper/KbBaseMapper.xml` | 相关子查询改 `LEFT JOIN` 聚合，避免逐行扫描 |
| `application.yml` | 切片 600 字符、重叠 100、topK 6、双路召回 50 |

---

## 六、验证记录

| 项 | 结果 |
|---|---|
| `mvn package` | BUILD SUCCESS |
| 服务启动 | `Started RuoYiApplication in 9.591 seconds` |
| 学科 93 第 1 章首次重建 | 39 文档 / 39 切片 / rewritten 39 |
| 同参数二次重建 | embedded 0 / unchanged 39（增量生效） |
| 学科 94 全量重建（第 10 章 + 221 句逐字稿 + testId 102） | 53 文档 / 77 切片 / 清理旧文档 36 条 |
| 学科 94 同参数二次重建 | embedded 0 / unchanged 77（增量生效） |
| 检索「分组交换」 | top1 `分组交换` score 0.703，`sectionPath` 正确带出 |
| 检索「TCP/IP 参考模型有几层」 | top1 随堂练习 7，score 0.905 |
| 检索「包子怎么做好吃」（口语化） | top3 全部命中视频逐字稿，说明语义召回正常 |
| 清空学科 93 | removed 39，文档表同步清理 |
| `tools/kb_health.py` | 77 分块 = 77 向量，孤儿 0、缺失 0 |

---

## 七、运维手册

```bash
# 健康巡检（只读，发现漂移返回非 0）
python tools/kb_health.py
python tools/kb_health.py --subject 94
python tools/kb_health.py --fix-orphans        # 删除孤儿向量

# 重建某个学科（会走增量）
curl -X POST http://127.0.0.1:8081/project/kb/rebuild \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"subjectId":94}'

# 检索自测
curl "http://127.0.0.1:8081/project/kb/search?q=分组交换&subjectId=94&topK=5" \
  -H "Authorization: Bearer $TOKEN"

# 启动服务（Windows）
start-app.bat 8081
```

回滚：
1. 代码：`git checkout` 相关文件后重新打包；
2. 数据：`RENAME TABLE kb_chunk TO kb_chunk_v2, kb_chunk_bak_20261028 TO kb_chunk;`
   （`kb_document` 可保留，旧代码不读它）

---

## 八、后续建议（按收益排序）

1. ~~重建学科 94 的知识库~~ **已完成**：第 10 章全量重建，切片 87 → 77
   （逐字稿窗口从 320 字符放大到 600 字符，26 块合并为 16 块），
   `chunk_index` 已改为文档内序号，旧文档清理 36 条。
   重建入参：`{"subjectId":94,"chapterId":10,"testId":102,"transcript":[221 句]}`
   （逐字稿来自 `ruoyi-ui/src/views/project/course/network-transcript.json`，`start` 需毫秒转秒）。
   回滚备份：`kb_chunk_bak_rebuild94`、`kb_document_bak_rebuild94`。
2. **Milvus 加 `doc_id` 字段与标量索引**。当前按 `id like 'doc#%'` 删除，数据量大后会退化为扫描；
   加字段需重建集合，建议与下次全量重建一起做。
3. **embedding 二进制化**。`provider=local` 时才需要 MySQL 存向量，可改为 `MEDIUMBLOB` 存 float32
   （1024 维 4 KB，体积降 70%，解析更快）；Milvus 模式下直接不写该列。
4. **父子切片**。知识点内容目前平均 192 字符，切片粒度已经够细；逐字稿建议改成
   「父块 1200 字 + 子块 300 字」，召回子块、喂给模型父块，兼顾精度与上下文完整性。
5. **建评估集**。抽 30~50 个真实学生问题 + 标准答案片段，每次改切片/换模型后跑一遍
   Recall@5、MRR，避免「凭感觉调参」。
6. **升级 embedding 模型**。百炼当前推荐 `text-embedding-v4`；换模型会触发全量重建，
   建议与第 2 项合并执行。
