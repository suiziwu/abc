# LangChain / LangGraph 工作流转换与 Java 集成

本目录把讯飞星火工作流 `人才画像20260914150500.yml` 转换为 **LangGraph StateGraph 语义的 JSON 图清单**，
并在 Java 端通过 **LangChain4j + 阿里云百炼 qwen3.8-flash（OpenAI 兼容接口）** 执行；
语音识别使用 **qwen-audio-3.1-asr-flash-filetrans** 录音文件识别。

> 重要说明：LangChain/LangGraph 官方没有用于描述“整张可视化 DAG”的通用 YAML/JSON Schema。
> 本目录采用的是 **LangGraph StateGraph 的可执行图清单**（节点、边、条件分支、状态、Prompt、模型配置），
> 不是虚构的“官方 YAML 标准”。该清单已通过 `langgraph` 库实际构建并执行验证，
> 同时被 Java 端加载和执行；`extensions.source` 逐字保留原始讯飞 DSL，任何字段都没有丢弃。

## 1. 文件说明

| 文件 | 说明 |
| --- | --- |
| `source/人才画像20260914150500.yml` | 原始讯飞星火 DSL，未做任何修改 |
| `rencai-huaxiang-20260914150500.langgraph.json` | 转换后的 LangGraph 图清单（含原始 DSL 全文） |
| `workflow.schema.json` | 图清单的 JSON Schema（Draft-07） |
| `convert_iflytek_workflow.py` | YAML → LangGraph JSON 转换器（可重复执行） |
| `validate_workflow.py` | JSON Schema + 图结构 + 原始数据一致性校验 |
| `build_langgraph.py` | 使用 `langgraph` 从 JSON 构建并运行 StateGraph |
| `../ruoyi-project/src/main/resources/langchain/` | Java 端运行时使用的同一份 JSON 与 Schema |
| `../ruoyi-project/src/main/java/com/ruoyi/project/langchain/` | Java 加载器、执行器、LangChain4j 适配器 |
| `../ruoyi-admin/.../LangChainWorkflowController.java` | REST 接口 |

## 2. 转换后的结构

顶层字段：

```text
schema_version  版本
framework       langchain/langgraph
format          langgraph-state-graph
flow            名称、描述、分类、头像、advancedConfig（对象 + 原始字符串）
state           状态 schema 与默认值
models          openai-compatible 模型配置（百炼 qwen3.8-flash）
graph           节点、边、条件边
execution       入口、出口、输入/输出键、输出模板、递归上限
extensions      原始讯飞 DSL（逐字保留）、源文件 SHA256、转换器信息
```

节点映射：

| 讯飞节点 | 转换后 type | runtime_name | 说明 |
| --- | --- | --- | --- |
| 开始节点 | `start` | `start` | 写入 `AGENT_USER_INPUT` |
| 代码 | `code` | `split_input` | 实现 `code=input[:3]; item=input[3:]`，并提供 `split_at` 声明式操作 |
| 分支器 | `router` | `route` | 4 个分支：`[0]`/`[1]`/`[2]`/默认 |
| 大模型_1 | `llm` | `subject_selector` | 学科筛选 Prompt |
| 大模型_2 | `llm` | `question_generator` | 面试出题 Prompt |
| 大模型_3 | `llm` | `wrong_answer_diagnoser` | 错题知识点诊断 Prompt |
| 结束节点 | `end` | `end` | 模板 `{{output}}{{output2}}{{output3}}` |

分支目标：

| 分支 | 条件 | 目标 |
| --- | --- | --- |
| `branch_one_of::8302...` | `code == "[0]"` | `subject_selector` |
| `branch_one_of::e421...` | `code == "[1]"` | `question_generator` |
| `branch_one_of::7bd6...` | `code == "[2]"` | `wrong_answer_diagnoser` |
| `branch_one_of::ac1c...` | 默认 | `end` |

## 3. 重新生成与校验

```powershell
cd D:\AAAWork\Knowledge\Knowledge\langchain

# 1) 重新生成
python .\convert_iflytek_workflow.py `
  .\source\人才画像20260914150500.yml `
  .\rencai-huaxiang-20260914150500.langgraph.json

# 2) JSON Schema + 图结构 + 原文一致性校验
python .\validate_workflow.py `
  .\rencai-huaxiang-20260914150500.langgraph.json `
  .\workflow.schema.json

# 3) 用 langgraph 实际构建并执行（使用假模型，不需要 API Key）
python .\build_langgraph.py .\rencai-huaxiang-20260914150500.langgraph.json
```

依赖：Python 3.10+，`pip install pyyaml jsonschema langgraph`。

已验证输出：

```text
PASS: JSON Schema 校验通过
PASS: 图结构校验通过
PASS: 原始数据一致性校验通过
summary: nodes=7 edges=9 routers=1 branches=4 llm=3
source_sha256: dd3b3ad15f1d29f1557f3a05d48eb9eda3f4e935d4e17c280c4972221cc2400b

PASS input='[0]后端开发工程师' output='SUBJECT_SELECTOR_RESULT' llm_calls=1
PASS input='[1]Java开发工程师' output='QUESTION_GENERATOR_RESULT' llm_calls=1
PASS input='[2]错题诊断' output='WRONG_ANSWER_DIAGNOSER_RESULT' llm_calls=1
PASS input='无前缀输入' output='' llm_calls=0
```

## 4. Java 端集成

### 4.1 依赖与兼容性

`ruoyi-project/pom.xml` 已加入：

```xml
<dependency>
  <groupId>dev.langchain4j</groupId>
  <artifactId>langchain4j-open-ai</artifactId>
  <version>0.35.0</version>
</dependency>
```

- 项目运行在 **JDK 8**；LangChain4j `0.36.2+` 的字节码为 Java 17（major 61），不可用；
- `0.35.0` 字节码为 Java 8（major 52），已通过 JDK 8 编译和运行测试；
- 阿里云百炼提供 OpenAI 兼容接口，使用 `OpenAiChatModel` 指向
  `https://ws-uxxtz5szu7puw1so.cn-beijing.maas.aliyuncs.com/compatible-mode/v1`，模型 `qwen3.8-flash`。
- 语音识别使用 DashScope 录音文件识别异步任务接口，模型 `qwen-audio-3.1-asr-flash-filetrans`。

### 4.2 配置

`ruoyi-admin/src/main/resources/application.yml`：

```yaml
langchain:
  workflow-location: classpath:langchain/rencai-huaxiang-20260914150500.langgraph.json
  base-url: https://ws-uxxtz5szu7puw1so.cn-beijing.maas.aliyuncs.com/compatible-mode/v1
  api-key: ${DASHSCOPE_API_KEY:}
  model-name: qwen3.8-flash
  temperature: 1.0
  timeout-seconds: 120
  log-requests: false
  log-responses: false

ai:
  asr:
    dashscope-base-url: https://ws-uxxtz5szu7puw1so.cn-beijing.maas.aliyuncs.com/api/v1
    api-key: ${DASHSCOPE_API_KEY:}
    model: qwen-audio-3.1-asr-flash-filetrans
    sample-rate: 16000
    language: zh
    max-audio-seconds: 300
    poll-interval-ms: 2000
    timeout-seconds: 180
```

配置 API Key（二选一）：

```powershell
$env:DASHSCOPE_API_KEY = "sk-..."
# 或启动参数：
java -jar ruoyi-admin\target\ruoyi-admin.jar --langchain.api-key=sk-...
```

未配置 Key 时应用仍可正常启动，仅在调用 AI/ASR 接口时返回明确错误。

### 4.3 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/project/langchain/workflow` | 返回完整图清单 |
| GET | `/project/langchain/health` | 节点数、LLM 数、模型、Key 是否配置 |
| POST | `/project/langchain/invoke` | 执行工作流 |
| POST | `/project/learn/chat` | 前端 AI 学习路径对话入口（内部走 qwen3.8-flash） |
| POST | `/project/asr/transcribe` | 上传 WAV，调用 qwen-audio-3.1-asr-flash-filetrans 转写 |

请求示例：

```json
{"input": "[0]后端开发工程师"}
```

响应关键字段：

```json
{
  "code": 200,
  "data": {
    "input": "[0]后端开发工程师",
    "output": "……模型返回……",
    "path": ["start", "split_input", "route", "subject_selector", "end"],
    "modelOutputs": {"output": "……"},
    "steps": 5,
    "durationMs": 1234
  }
}
```

输入约定：前 3 个字符为分支标记：

- `[0]` → 学科筛选（`subject_selector`）
- `[1]` → 面试出题（`question_generator`）
- `[2]` → 错题诊断（`wrong_answer_diagnoser`）
- 其它 → 默认分支，直接结束，输出空字符串

### 4.4 Java 类说明

| 类 | 职责 |
| --- | --- |
| `LangChainWorkflow` | 图清单 POJO（snake_case 映射） |
| `LangChainWorkflowLoader` | JSON 加载 + 全量结构校验 |
| `LangChainWorkflowExecutor` | start/code/router/llm/end 执行器 |
| `ChatModelClient` | 模型调用抽象（便于替换/测试） |
| `LangChain4jChatModelClient` | LangChain4j `OpenAiChatModel` → 百炼 qwen3.8-flash 适配 |
| `LangChainWorkflowService(Impl)` | 启动时加载清单，提供 invoke |
| `LangChainWorkflowController` | REST 接口 |
| `QwenFileTransClient` | 16k WAV → base64 data URL → 录音文件识别任务 → 轮询 → 结果提取 |
| `WavAudio` | WAV 解析、混单声道、重采样、输出 16k WAV |
| `AsrService(Impl)` / `AsrController` | 语音识别服务与 `/project/asr/transcribe` 接口 |

### 4.5 编译与测试

```powershell
$env:JAVA_HOME = 'C:\Users\liu\.jdks\corretto-1.8.0_492'
$env:Path = "$env:JAVA_HOME\bin;C:\xinxinxin\wenjian\apache-maven-3.9.8\bin;$env:Path"

mvn -pl ruoyi-project -am test
mvn -DskipTests package
java -jar ruoyi-admin\target\ruoyi-admin.jar
```

已验证：`11` 个 Java 单元测试全部通过（清单加载、4 个分支执行、LangChain4j Builder、
缺少 Key 报错、WAV 解析/重采样、录音文件识别上传/任务/轮询/结果提取）。

### 4.6 端到端验证（不消耗真实 API）

用一个本地 OpenAI 兼容假服务返回固定内容，启动参数覆盖 `base-url` 与 `api-key`：

```powershell
java -jar ruoyi-admin\target\ruoyi-admin.jar `
  --langchain.base-url=http://127.0.0.1:9099/v1 `
  --langchain.api-key=test `
  --langchain.model-name=fake
```

登录后调用结果：

```text
[0]岗位A -> FAKE_SUBJECT_SELECTOR
[1]岗位B -> FAKE_QUESTION_GENERATOR
[2]错题   -> FAKE_WRONG_ANSWER_DIAGNOSER
无前缀    -> ""（默认分支，未调用模型）
```

证明 Java → LangChain4j → OpenAI 兼容 HTTP → 模型响应 → 输出聚合全链路可用。

## 5. 限制

- `code` 节点的 `split_at` 操作是针对本工作流 Python 代码的声明式实现；如更换代码节点，
  需要扩展 `LangChainWorkflowExecutor` 或改用脚本引擎。
- 三个 LLM 节点使用同一份运行配置；原始 `modelName=deepseek` 等 provider 参数完整保留在
  `graph.nodes[].llm.provider`，运行模型名通过 `langchain.model-name` 配置为 `qwen3.8-flash`。
- 未提交任何真实 API Key；`DASHSCOPE_API_KEY` 需在本地环境设置。
- 录音文件识别会先把 WAV 统一转换为 16k/16bit/mono，再以
  `data:audio/wav;base64,...` 作为 `file_urls` 提交异步任务；单次上限 120 秒。
- **语音识别仅用于“音频文件 → 文本”的文本提取**：不包含麦克风实时采集、音频流处理、
  WebSocket 实时识别等组件；前端通过上传 WAV 文件触发识别。

## 6. 真实调用验证（充值后，2026-10-27）

| 调用 | 结果 |
| --- | --- |
| `qwen3.8-flash` 对话 | `200`，返回合法 JSON（selected_subjects / overall_analysis） |
| `qwen-audio-3.1-asr-flash-filetrans` | `200`，返回 `hello world，这里是阿里巴巴语音实验室。` |
| 语音识别耗时 | 约 2.5 秒（3.83 秒音频） |
| Java 单元测试 | 11 / 11 通过 |

说明：百炼任务创建后的首次查询可能短暂返回 `403/404`，客户端已加入初始等待与持续重试，
不会误判为账号失败。
