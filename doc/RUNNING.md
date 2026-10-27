# 智程学途（RuoYi-Vue 3.9.0）本地运行手册

本文记录本项目在当前机器（Windows 11）上的完整启动流程、已验证结果和踩坑记录，按本文操作可复现运行。

## 1. 技术栈与运行环境

| 组件 | 项目要求 | 当前使用版本 | 说明 |
| --- | --- | --- | --- |
| JDK | 1.8 | Corretto 1.8.0_492 | 项目 `maven.compiler.source/target=1.8`，Spring Boot 2.5.15 |
| Maven | 3.x | 3.9.8 | 机器默认 Maven 4.0.0-rc-5 仅支持 JDK 17+，不用于本项目 |
| MySQL | 5.7/8.x | 8.0.45（服务名 MySQL80） | 地址 `localhost:3306` |
| Redis | 5.x+ | 5.0.14.1（Windows 版） | 地址 `localhost:6379`，无密码 |
| Node.js | 18+ | v24.16.0 / npm 11.13.0 | 前端 Vue3 + Vite 6 |

## 2. 端口与关键配置

| 服务 | 地址 |
| --- | --- |
| 前端（Vite dev） | http://localhost:5173 |
| 后端 API | http://localhost:8081 |
| MySQL | localhost:3306 / 数据库 `personnel_information` |
| Redis | localhost:6379 / db 0 |

关键配置位置：

- `ruoyi-admin/src/main/resources/application.yml`
  - `server.port: 8081`（本机 8080 被 Steam 的 `steamwebhelper` 占用，已由 8080 改为 8081）
  - `spring.redis.host/port: localhost/6379`，无密码
  - `ruoyi.profile: ./upload`
- `ruoyi-admin/src/main/resources/application-druid.yml`
  - `jdbc:mysql://localhost:3306/personnel_information`
  - `username: root` / `password: 123456`
- `ruoyi-ui/vite.config.js`
  - `baseUrl = 'http://localhost:8081'`，`/dev-api` 代理到后端
- `ruoyi-ui/.env.development`
  - `VITE_APP_BASE_API = '/dev-api'`

> 说明：项目没有 `.env` 形式的后端环境变量文件，数据库、Redis、端口等均在上面的 YAML/JS 中配置。
> AI 对话（`/project/learn/chat`）使用讯飞星火工作流，AK/SK 硬编码在
> `ruoyi-admin/src/main/java/com/ruoyi/web/controller/project/LearnController.java`，无需额外配置。

## 3. 首次启动步骤

以下命令均在项目根目录 `D:\AAAWork\Knowledge\Knowledge` 下执行（前端除外）。

### 3.1 启动 MySQL

MySQL 已注册为 Windows 服务 `MySQL80`，确认其处于运行状态：

```powershell
Get-Service MySQL80
# 如未运行：
Start-Service MySQL80
```

### 3.2 启动 Redis

Redis 已下载到固定目录 `D:\AAAWork\Knowledge\tools\redis-5.0.14.1`（不提交到代码仓库）。
**单独打开一个终端**运行（前台常驻，关闭窗口即停止）：

```powershell
cd D:\AAAWork\Knowledge\tools\redis-5.0.14.1
.\redis-server.exe --port 6379 --save "" --appendonly no
```

验证：

```powershell
D:\AAAWork\Knowledge\tools\redis-5.0.14.1\redis-cli.exe ping   # 返回 PONG
```

> Redis 5 的 Windows 版没有自带服务安装能力；如需开机自启，可将其注册为 Windows 服务
> （需要管理员权限），或在启动脚本中后台拉起 `redis-server.exe`。

### 3.3 数据库初始化（当前环境已完成，可跳过）

当前 `personnel_information` 库已存在，业务表和迁移字段均已就绪：

- `ai_chat_history.raw_response`、`ai_chat_history.user_id`
- `user_learn_record.point_quiz_score`
- `user_knowledge_assessment.ai_response`

管理员账号：`admin / admin123`。

若是全新数据库，按下面步骤初始化：

```powershell
$mysql = 'C:\mysql\MySQL\MySQL Server 8.0\bin\mysql.exe'
& $mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS personnel_information DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
cmd /c "`"$mysql`" -uroot -p123456 personnel_information < knowledge.sql"

# 如使用的是未包含新字段的旧库，再执行 sql 目录下的迁移脚本：
# sql/migration_add_ai_response.sql
# sql/migration_add_point_quiz_score.sql
# sql/migration_add_userid_to_aichathistory.sql
```

> `knowledge.sql` 是 `knowledge` 库的导出文件（不含 CREATE DATABASE），本身可直接导入
> `personnel_information`。其余业务表（course、ai_room 等）属于完整业务库，本仓库 SQL 未包含，
> 新环境如需这些功能请使用完整数据库备份。

### 3.4 编译并启动后端

必须使用 JDK 8 + Maven 3.9.8：

```powershell
$env:JAVA_HOME = 'C:\Users\liu\.jdks\corretto-1.8.0_492'
$env:Path = "$env:JAVA_HOME\bin;C:\xinxinxin\wenjian\apache-maven-3.9.8\bin;$env:Path"

mvn -DskipTests clean package
java -jar ruoyi-admin\target\ruoyi-admin.jar
```

启动成功标志（日志）：

```text
Starting ProtocolHandler ["http-nio-8081"]
Started RuoYiApplication in 7.933 seconds
若依启动成功
```

### 3.5 安装依赖并启动前端

```powershell
cd D:\AAAWork\Knowledge\Knowledge\ruoyi-ui
npm install
npm run dev
```

启动成功后访问：**http://localhost:5173**，使用 `admin / admin123` 登录。

### 3.6 API Key 配置（百炼 DashScope）

AI 问答（百炼 `qwen3.8-flash` + DeepSeek `deepseek-chat`）与语音识别
（`qwen-audio-3.1-asr-flash-filetrans`）的 Key 已配置，采用下面**两种方式**，任一生效即可：

**方式一（当前已启用，推荐）：外部配置文件**

`config/application.yml`（相对项目根目录，Spring Boot 自动加载，优先级高于 jar 内配置）：

```yaml
langchain:
  api-key: sk-...            # 百炼（qwen）
ai:
  chat:                      # 可选模型清单（列表整体覆盖，需写全）
    default-model: qwen3.8-flash
    models:
      - id: qwen3.8-flash
        base-url: https://.../compatible-mode/v1
        api-key: sk-...      # 百炼 Key
        model: qwen3.8-flash
      - id: deepseek-chat
        base-url: https://api.deepseek.com/v1
        api-key: sk-...      # DeepSeek Key
        model: deepseek-chat
  asr:
    api-key: sk-...          # 百炼 Key（与 langchain 相同）
```

- 该文件已加入 `.gitignore`（`/config/application.yml`），不会被提交
- 只要在项目根目录执行 `java -jar ruoyi-admin\target\ruoyi-admin.jar`，无需设置任何环境变量即可生效
- 新增/修改该文件后需**重启后端**，无需重新编译

**方式二：用户级环境变量（已持久化写入）**

```powershell
[Environment]::SetEnvironmentVariable('DASHSCOPE_API_KEY','sk-...','User')  # 百炼
[Environment]::SetEnvironmentVariable('DEEPSEEK_API_KEY','sk-...','User')   # DeepSeek
# 之后新开的终端会继承该变量；已在运行中的终端/IDE 需重启才能读到
$env:DASHSCOPE_API_KEY = "..."   # 仅当前窗口临时生效
java -jar ruoyi-admin\target\ruoyi-admin.jar
```

验证 Key 是否生效（登录后带 token 调用）：

```powershell
GET /project/langchain/health   # 期望 apiKeyConfigured=true
GET /project/course/models      # 期望两个模型均 available=true
```

### 3.7 LangChain 工作流（Java / LangChain4j）

讯飞工作流已转换为 LangGraph StateGraph 语义清单，并由 Java 端在启动时自动加载、校验：

- 转换产物：`langchain/rencai-huaxiang-20260914150500.langgraph.json`
- 原始 DSL：`langchain/source/人才画像20260914150500.yml`（逐字保留，转换文件中亦内嵌于 `extensions.source`）
- Java 资源：`ruoyi-project/src/main/resources/langchain/`
- AI 问答模型：`qwen3.8-flash`（百炼 OpenAI 兼容接口）
- 语音识别模型：`qwen-audio-3.1-asr-flash-filetrans`（录音文件识别异步任务）
- 语音识别仅用于**音频文件转文本**，未启用麦克风实时采集、流处理或 WebSocket 实时识别组件
- 详细说明：`langchain/README.md`

Key 的配置方式见 3.6 节（已配置完成），修改后重启后端即可真实调用。

相关接口（需登录 token）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/project/langchain/workflow` | 返回完整图清单 |
| GET | `/project/langchain/health` | 节点数、LLM 数、模型、Key 是否配置 |
| POST | `/project/langchain/invoke` | 执行工作流，body：`{"input":"[0]后端开发工程师"}` |
| POST | `/project/learn/chat` | 前端 AI 学习路径对话（qwen3.8-flash） |
| POST | `/project/asr/transcribe` | 上传 WAV，语音识别（qwen-audio-3.1-asr-flash-filetrans） |

转换、JSON Schema 校验、LangGraph 实际构建执行：

```powershell
cd D:\AAAWork\Knowledge\Knowledge\langchain
python .\convert_iflytek_workflow.py .\source\人才画像20260914150500.yml .\rencai-huaxiang-20260914150500.langgraph.json
python .\validate_workflow.py .\rencai-huaxiang-20260914150500.langgraph.json .\workflow.schema.json
python .\build_langgraph.py .\rencai-huaxiang-20260914150500.langgraph.json
```

## 4. 已验证结果（2026-10-27）

| 验证项 | 结果 |
| --- | --- |
| 后端编译 `mvn -DskipTests clean package` | BUILD SUCCESS（8 个模块全部成功） |
| 后端启动 | `Started RuoYiApplication in 7.933 seconds`，监听 8081 |
| 数据库连接 | Druid 初始化成功，登录时正常查询 `sys_config`、`sys_job` 等 |
| Redis 连接 | `PONG`，验证码、字典、参数缓存正常读取 |
| `GET /captchaImage` | `code=200`，返回 uuid 与 Base64 验证码图片 |
| 登录 `POST /login`（admin/admin123 + 验证码） | `code=200`，返回 JWT token |
| `GET /getInfo`（带 token） | `code=200`，`userName=admin`，角色 `admin` |
| `GET /system/user/list` | `code=200`，`total=4` |
| `GET /project/point/list` | `code=200`，`total=204` |
| `GET /project/subject/list` | `code=200`，`total=21` |
| `GET /project/record/list` | `code=200`，`total=2` |
| 前端首页 `GET http://localhost:5173/` | HTTP 200，标题「智程学途」 |
| 前端代理 `GET /dev-api/captchaImage` | `code=200`，链路打通 |
| 前端代理登录 `/dev-api/login`、`/dev-api/getInfo` | `code=200` |
| 前端生产构建 `npm run build:prod` | 构建成功，产物输出到 `ruoyi-ui/dist` |
| AI 对话 `/dev-api/project/learn/chat` | HTTP 200，外部工作流返回成功但内容为空（证书有效，见问题 5） |
| LangChain 清单 JSON Schema + 语义校验 | PASS：7 节点 / 9 边 / 1 分支器 / 4 分支 / 3 LLM，原文 SHA256 一致 |
| LangGraph 实际构建执行（假模型） | 4 个输入全部 PASS：`[0]`/`[1]`/`[2]` 三分支 + 默认分支 |
| Java 单元测试 `mvn -pl ruoyi-project -am test` | 13 个测试全部通过 |
| Java 启动加载 LangChain | `LangChain 工作流加载成功: nodes=7, edges=9` |
| `GET /project/langchain/health` | `code=200`，nodes=7，llm=3，model=qwen3.8-flash |
| LangChain4j → 本地假 OpenAI 服务 | 三个分支分别返回对应标记，默认分支输出空串；全链路通过 |
| Qwen 录音文件识别客户端 | 本地模拟 DashScope：data URL→创建任务→轮询→结果提取全部通过 |
| 真实百炼 qwen3.8-flash（充值后） | `200`，返回合法 JSON |
| 真实 qwen-audio-3.1-asr-flash-filetrans（充值后） | `200`，返回「hello world，这里是阿里巴巴语音实验室。」 |
| 知识图谱幂等导入 | subject 93「数据结构与算法（完整图谱）」：382 知识点 / 620 关系 / 0 孤立关系 |
| 知识图谱接口查询（含前端代理） | subject id=93，point total=382，point 10001 详情正常 |
| 实时语音组件检查 | 源码中无 AudioContext / getUserMedia / WebSocket / ws-url / Java-WebSocket |
| 语音识别文本提取（含前端代理） | `200`，返回「hello world，这里是阿里巴巴语音实验室。」 |
| 课程学习页面 | 菜单「课程学习」可进入，视频、视频文本、AI 对话、思维导图、随堂练习均正常渲染 |
| 课程题库 API `GET /project/course/quiz` | `code=200`，返回 10 道题，选项/答案解析正常 |
| 课程 AI 助教 `POST /project/course/chat` | `code=200`，返回带课程时间戳引用的回答 |
| 模型列表 `GET /project/course/models` | `code=200`，返回 `qwen3.8-flash`（默认）与 `deepseek-chat`，均 `available=true` |
| 多模型真实问答 | `model=qwen3.8-flash` 与 `model=deepseek-chat` 均 `code=200` 并返回正确答案，响应回显所选模型 |
| 课程动态路由 | `/getRouters` 返回 `course -> project/course/index` |
| Key 外部配置生效（2026-10-27 重启后） | 未设置任何环境变量，`GET /project/langchain/health` 返回 `apiKeyConfigured=true`、`model=qwen3.8-flash` |
| Key 真实调用（外部配置） | `POST /project/langchain/invoke` → `code=200`，返回模型生成内容；`/project/asr/transcribe` 可提交到 DashScope（静音测试返回 `ASR_RESPONSE_HAVE_NO_WORDS`，证明 Key 已通过鉴权） |
| 未配置 Key 调用 AI/ASR | 返回明确错误：未配置 DASHSCOPE_API_KEY |

## 5. 问题记录与解决方案

### 问题 1：默认 JDK 17 + Maven 4 与项目 Java 8 不匹配

- 现象：环境默认 `java 17.0.19`、`Maven 4.0.0-rc-5`，而项目 `pom.xml` 指定 `java.version=1.8`、
  Spring Boot 2.5.15；Maven 4 本身要求 JDK 17+ 运行。
- 解决：编译和运行统一显式使用 JDK 8（`corretto-1.8.0_492`）+ Maven 3.9.8
  （`C:\xinxinxin\wenjian\apache-maven-3.9.8`），通过设置 `JAVA_HOME` 和 `Path` 切换。

### 问题 2：本机未安装 Redis，后端启动失败

- 现象：`Unable to connect to localhost:6379`，`sysConfigServiceImpl` 初始化失败，应用退出。
- 原因：项目强依赖 Redis（验证码、字典、参数缓存）。
- 解决：下载 Windows 版 Redis 5.0.14.1，解压到 `D:\AAAWork\Knowledge\tools\redis-5.0.14.1`，
  以 `--port 6379 --save "" --appendonly no` 启动。

### 问题 3：GitHub 下载 Redis 失败

- 现象 1：`schannel: CRYPT_E_NO_REVOCATION_CHECK`。
  解决：`curl.exe --ssl-no-revoke -L ...` 关闭吊销列表检查。
- 现象 2：下载速度约 30KB/s，超时只拿到部分文件；`client returned ERROR on write`。
  解决：用 `curl.exe -C -` 断点续传，最终拿到完整 12,617,669 字节压缩包后再解压。

### 问题 4：8080 端口被 Steam 占用

- 现象：`127.0.0.1:8080` 被 `steamwebhelper`（PID 27420）监听；虽然通配地址可绑定，
  但 localhost 请求可能被 Steam 抢走，后端验证不可靠。
- 解决：后端端口改为 `8081`（`application.yml`），前端代理目标同步改为
  `http://localhost:8081`（`vite.config.js`）。

### 问题 5：AI 对话返回空内容

- 现象：`/project/learn/chat` 返回 `code=200`，但 `reply` 为讯飞工作流的原始 JSON，
  `choices[0].delta.content` 为空。
- 结论：网络与鉴权正常（HTTP 200、`code=0`），属于外部工作流侧返回内容为空，
  不影响项目启动与其它功能；需要时在讯飞星火工作流控制台检查节点输出/输入格式。

### 问题 6：PowerShell 启动参数被拆分

- 现象：`java -Dfile.encoding=UTF-8 -jar ...` 在 PowerShell 中报
  `Could not find or load main class .encoding=UTF-8`。
- 解决：JVM 参数加引号：`java '-Dfile.encoding=UTF-8' -jar ruoyi-admin\target\ruoyi-admin.jar`。

### 问题 7：后台启动的 Redis/后端进程随终端退出

- 现象：用 `Start-Process` 在临时终端拉起的 `redis-server`，命令结束后进程被回收，
  后端因连不上 Redis 而退出。
- 解决：在**独立常驻终端**中前台运行 Redis 和后端；或使用 Windows 服务/计划任务托管。
  本机当前由持久后台会话托管 Redis、后端和 Vite，关闭会话后需按第 3 节重新启动。

### 问题 8：LangChain4j 高版本要求 Java 17，与 JDK 8 不兼容

- 现象：`langchain4j-core:0.36.2` 的 class 文件版本为 61（Java 17），JDK 8 无法加载。
- 解决：使用 `langchain4j-open-ai:0.35.0`（class 版本 52 / Java 8），已通过编译、
  Builder 实例化和 13 个单元测试验证。
- 结论：升级 LangChain4j 前必须先评估 JDK 升级，不能直接改版本号。

### 问题 9：LangChain 没有“整张工作流”的官方 YAML/JSON 标准

- 事实：LangChain/LangGraph 官方以代码定义 Graph，没有可视化 DAG 的通用 Schema；
  LangGraph 的 `langgraph.json` 只声明入口，不描述节点/边。
- 解决：采用 **LangGraph StateGraph 语义图清单**，并同时提供：
  - `build_langgraph.py`：用官方 `langgraph` 库从清单真实构建并执行；
  - Java `LangChain4j` 执行器：加载同一份清单执行；
  - `extensions.source`：逐字保存原始讯飞 DSL，保证零信息丢失。
- 注意：该清单不是虚构的“官方 YAML 标准”，而是可执行的图清单 + 两套官方运行时的适配层。

### 问题 10：百炼账号模型未开通/欠费，真实 AI 调用被拒绝

- 现象 1：`qwen3.8-flash` 对话返回
  `403 AccessDenied.Unpurchased: Access to model denied`。
- 现象 2：`qwen-audio-3.1-asr-flash-filetrans` 创建任务返回 200，但查询任务返回
  `403 AccessDenied.Unpurchased`；公网 DashScope 端点返回 `Arrearage`（账户欠费）。
- 解决：在百炼控制台充值并确认模型开通后已恢复；`qwen3.8-flash` 与录音文件识别均已真实调用成功。
- 说明：充值/开通前应用可正常启动，仅 AI/ASR 接口返回明确错误。

### 问题 11：录音文件识别不接受临时 OSS `oss://` 地址，且首次查询可能 403

- 现象 1：把音频上传到 DashScope 临时 OSS 后，以 `oss://dashscope-instant/...` 作为
  `file_urls`，任务返回 `REQUEST_INVALID_FILE_URL_VALUE`。
- 现象 2：任务创建成功后立即查询，可能短暂返回 `403 AccessDenied.Unpurchased`。
- 解决：
  - 不再使用临时 OSS，改为把归一化后的 16k WAV 转成 `data:audio/wav;base64,...`，
    该格式已被 `qwen-audio-3.1-asr-flash-filetrans` 异步任务接口实际接受并成功转写；
  - 轮询前等待一个周期，并对 `403/404` 持续重试，避免把任务刚创建时的短暂状态误判为失败。

## 6. 本次变更文件

| 文件 | 变更 |
| --- | --- |
| `ruoyi-admin/src/main/resources/application.yml` | 后端端口 `8080` → `8081`；新增 `langchain:` 配置 |
| `ruoyi-ui/vite.config.js` | 代理目标 `http://localhost:8080` → `http://localhost:8081` |
| `ruoyi-ui/dist/**` | 执行生产构建后重新生成 |
| `langchain/**` | 原始 DSL、LangGraph 清单、JSON Schema、转换/校验/LangGraph 运行脚本、README |
| `ruoyi-project/pom.xml` | 新增 `langchain4j-open-ai:0.35.0` 与测试依赖 |
| `ruoyi-project/src/main/java/com/ruoyi/project/langchain/**` | 清单模型、加载校验、执行器、LangChain4j 适配、服务 |
| `ruoyi-project/src/main/resources/langchain/**` | Java 运行时使用的 JSON 清单与 Schema |
| `ruoyi-project/src/test/java/com/ruoyi/project/langchain/**` | 7 个 LangChain/Java 单元测试 |
| `ruoyi-project/src/main/java/com/ruoyi/project/asr/**` | Qwen 录音文件识别客户端、WAV 解析/重采样、ASR 服务 |
| `ruoyi-project/src/test/java/com/ruoyi/project/asr/**` | 4 个 ASR 单元测试（含本地模拟 DashScope HTTP 全流程） |
| `ruoyi-admin/src/main/java/com/ruoyi/web/controller/project/AsrController.java` | 新增 `/project/asr/transcribe` 接口 |
| `ruoyi-ui/src/api/project/asr.js` | 前端语音识别 API |
| `ruoyi-ui/src/views/project/learn/index.vue` | 新增 WAV 音频文件上传识别、文本提取结果回填 |
| `ruoyi-admin/src/main/java/com/ruoyi/web/controller/project/LearnController.java` | AI 对话改为百炼 qwen3.8-flash |
| `ruoyi-admin/src/main/java/com/ruoyi/web/controller/project/LangChainWorkflowController.java` | 新增 LangChain REST 接口 |
| `doc/RUNNING.md` | 新增本运行手册及 LangChain 章节 |

## 7. 停止服务

```powershell
# 前端 / 后端 / Redis 按启动终端分别 Ctrl+C
# 或按端口结束进程（谨慎操作）：
Get-NetTCPConnection -LocalPort 5173,8081,6379 -State Listen |
  Select-Object LocalPort,OwningProcess
# Stop-Process -Id <PID>
```

## 8. 课程知识图谱导入

`sql/import_dsa_kg.sql` 由《数据结构与算法》（王曙燕）297 页教材 OCR 图谱转换而来，
覆盖 9 章、146 节、382 个知识点、620 条知识点关系，导入为独立学科，不影响原有数据。

```powershell
# 目标库：personnel_information（见 application-druid.yml）
$env:MYSQL_PWD='你的数据库密码'
& "C:\mysql\MySQL\MySQL Server 8.0\bin\mysql.exe" -h 127.0.0.1 -u root `
    --default-character-set=utf8mb4 -D personnel_information `
    -e "source D:/AAAWork/Knowledge/Knowledge/sql/import_dsa_kg.sql"
```

- 学科：`数据结构与算法（完整图谱）`，`code=sub_algorithm_kg`；原有 `subject.id=1`
  “数据结构与算法”保持不变。
- 脚本可重复执行：先删除本学科旧知识点（关系由外键级联删除），再全量重建；
  知识点固定 ID `10001–10382`，关系固定 ID `10001–10620`。
- 章节、节号、页码、别名等层级信息写入 `knowledge_point.content`；
  原始关系类型（PART_OF / IMPLEMENTS 等）写入 `knowledge_point_relation.description`。
- 前端「知识图谱」页面选择该学科即可查看；知识点超过 120 个时自动进入大图模式
  （缩小节点、节点右侧显示防重叠标签，可通过「显示知识点名称」开关控制；
  hover 显示完整名称、点击查看详情）。
- 关系线文字同样可切换：「显示连线文字」开关控制边标签（前置依赖/相关参考/扩展延伸）；
  小图默认显示，大图默认隐藏以避免 600+ 条边标签堆叠，可手动打开（标签带半透明白底，保证可读）。
- 实现注意：ECharts `graph` 系列的边标签必须配置在 `series.edgeLabel`（每条边写法会被内部映射为
  `edgeLabel`，直接在 `edges[].label` 上配置不生效），否则开关切换后连线文字不会出现。

校验 SQL：

```sql
SELECT (SELECT COUNT(*) FROM knowledge_point WHERE subject_id = s.id) AS points,
       (SELECT COUNT(*) FROM knowledge_point_relation r
          JOIN knowledge_point p ON p.id = r.source_id
         WHERE p.subject_id = s.id) AS relations
FROM subject s WHERE s.code = 'sub_algorithm_kg';
-- 期望：points = 382，relations = 620
```

| 文件 | 变更 |
| --- | --- |
| `sql/import_dsa_kg.sql` | 新增课程知识图谱导入脚本（382 知识点 / 620 关系） |
| `ruoyi-ui/src/views/project/topology/index.vue` | 大图模式标签显示修复：知识点 > 120 时仍显示防重叠标签，新增「显示知识点名称」开关；新增「显示连线文字」开关（边标签可显示/隐藏） |
| `doc/RUNNING.md` | 补充本节导入说明 |

## 9. 课程学习页面（视频 + AI 助教 + 思维导图 + 随堂练习）

左侧菜单「课程学习」对应：

- 菜单：`sys_menu.menu_id=2049`，`path=course`，`component=project/course/index`
- 页面：`ruoyi-ui/src/views/project/course/index.vue`
- 菜单脚本：`sql/menu_course_study.sql`（可重复执行）

页面结构：

- 左侧：课程视频、视频文本（逐句时间戳跳转）、课程设计、重点标签、课堂测验、章节时间轴
- 右侧教学空间：概览、视频文本、AI 对话、思维导图、随堂练习
- 视频文本粒度：按 ASR 一句话级片段逐句展示（共 221 句），仅在视觉上按 5 个章节分组，不再把整章拼接成大段文字；播放时当前句高亮并在列表内自动滚动定位，搜索按句子过滤
- 案例课程：`计算机网络 · 第一章 概念和功能`
- 视频：`ruoyi-ui/public/videos/network-course.mp4`（21:46，来自 `work_cn/video/1.1.1 概念和功能.mp4`）
- 逐字稿：`ruoyi-ui/src/views/project/course/network-transcript.json`（221 句 / 5 章节，来自 `work_cn/out/transcript_segments.json`）
- 知识点：学科 `subject_id=94`（计算机网络原理与实践完整图谱，第 1 章 50 个知识点、6 个小节；页面按 `第1章` 过滤）
- 思维导图：按 6 个小节分组，每节精选最多 4 个代表知识点；全部 50 个知识点可在左侧「重点标签」查看
- 题库：`question.test_id=102`（根据第 1 章内容整理的 10 道随堂练习，SQL：`sql/import_network_questions.sql`）

接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/project/course/quiz?testId=102&limit=10` | 返回解析后的题目、选项、答案、解析 |
| GET | `/project/course/models` | 返回 AI 助教可选模型（`id/label/model/available/default`） |
| POST | `/project/course/chat` | 入参 `{question, context, model}`，`model` 为空时用默认模型 |

### 9.1 可切换模型（qwen3.8-flash / DeepSeek）

AI 对话面板底部新增「回答模型」下拉框，切换后新的提问走所选模型；未配置 Key 的模型会置灰。

模型清单来自 `ai.chat.models`（见 3.6 节），当前配置：

| 模型 id | 展示名 | 接口 | 配置 |
| --- | --- | --- | --- |
| `qwen3.8-flash`（默认） | 通义千问 3.8 Flash | 百炼 OpenAI 兼容接口 | `config/application.yml` |
| `deepseek-chat` | DeepSeek Chat | `https://api.deepseek.com/v1` | `config/application.yml` |

后端实现：`AiChatProperties` 读取清单，`LangChain4jChatModelClient` 按模型 id 懒加载并缓存
`OpenAiChatModel`；未知模型返回「不支持的模型」并列出可选值。

菜单初始化：

```powershell
$mysql = 'C:\mysql\MySQL\MySQL Server 8.0\bin\mysql.exe'
& $mysql -uroot -p123456 --default-character-set=utf8mb4 -D personnel_information `
  -e "source D:/AAAWork/Knowledge/Knowledge/sql/menu_course_study.sql"
```

已验证：

- 页面无启动报错，五个功能区均可正常渲染；
- 视频文本逐句渲染 221 句（5 章节分组：51 / 48 / 55 / 53 / 14），时间戳点击可跳转；
- 题库接口返回 10 题，首题「从逻辑结构上看，计算机网络通常由哪两部分组成？」选项 A-D 正常；
- AI 助教返回带 `[00:49]` 等时间戳引用的回答；
- `/getRouters` 返回课程路由，菜单可点击进入。

| 文件 | 变更 |
| --- | --- |
| `ruoyi-ui/src/views/project/course/index.vue` | 新增课程学习整页（视频/逐句文本/AI/导图/题库），视频文本按句渲染并高亮当前句 |
| `ruoyi-ui/src/views/project/course/network-transcript.json` | 计算机网络第 1 章逐字稿（221 句 / 5 章节） |
| `ruoyi-ui/src/api/project/course.js` | 新增课程 AI 与题库 API |
| `ruoyi-admin/.../CourseStudyController.java` | 新增课程问答与随堂练习接口 |
| `ruoyi-project/.../course/**` | 题库读取、选项/答案解析、AI 助教服务 |
| `sql/menu_course_study.sql` | 新增课程学习菜单 |
| `sql/import_network_questions.sql` | 新增计算机网络第 1 章随堂练习 |
| `ruoyi-ui/public/videos/network-course.mp4` | 计算机网络第 1 章案例视频（21:46） |

## 10. 计算机网络知识图谱导入

`sql/import_cn_kg.sql` 由陈鸣《计算机网络原理与实践》扫描教材 OCR（7 章 / 57 节 / 145 子节）
与 1.1.1 视频逐字稿构建的课程图谱转换而来，导入为独立学科，不影响原有数据。

```powershell
$env:MYSQL_PWD='你的数据库密码'
& "C:\mysql\MySQL\MySQL Server 8.0\bin\mysql.exe" -h 127.0.0.1 -u root `
    --default-character-set=utf8mb4 -D personnel_information `
    -e "source D:/AAAWork/Knowledge/Knowledge/sql/import_cn_kg.sql"
```

- 学科：`计算机网络原理与实践（完整图谱）`，`code=sub_network_kg`，`subject_id=94`；
  原有 `subject.id=3`“计算机网络”保持不变。
- 规模：**476 个知识点 / 383 条知识点关系**（知识点 ID `11001–11476`，关系 ID `11001–11383`）。
- 关系类型映射到目标表 3 类：
  `PREREQUISITE → prerequisite`；`RELATED_TO / CONTRAST_WITH → related`；
  `IS_A / PART_OF / USES / IMPLEMENTS / APPLIES_TO / MEASURED_BY → extension`；
  原始类型与说明保留在 `description`。
- 目标库不存“节”节点，章节位置、页码、别名、标签写入 `knowledge_point.content`；
  同节内孤立知识点会补一条到本节锚点的“同节相关”关系（79 条）。
- 脚本可重复执行：先删除本学科知识点（关系级联删除），再全量重建。

校验 SQL：

```sql
SELECT (SELECT COUNT(*) FROM knowledge_point WHERE subject_id = 94) AS points,
       (SELECT COUNT(*) FROM knowledge_point_relation r
          JOIN knowledge_point p ON p.id = r.source_id
         WHERE p.subject_id = 94) AS relations;
-- 期望：points = 476，relations = 383
```

实际校验结果：重复节点对 0、悬空外键 0、重名 0；仅 1 个孤立点（1.6 小结中的“计算机网络定义”，该节只有 1 个知识点）。

前端「知识图谱」页面选择“计算机网络原理与实践（完整图谱）”即可查看；
知识点超过 120 个时自动进入大图模式（小节点、hover 显示名称、点击查看详情）。

| 文件 | 变更 |
| --- | --- |
| `sql/import_cn_kg.sql` | 新增计算机网络知识图谱导入脚本（476 知识点 / 383 关系） |
| `doc/RUNNING.md` | 补充本节导入说明 |

## 11. 视频 / 文档通用解析组件（media_kit）

已把通用解析组件内置到 `tools/media_kit`，并提供 Java 代理 `MediaKitController`
（`/project/media/**`）。解析由同机 Python 服务完成，需要：

```powershell
# 1) 安装依赖（Python 3.10+）
python -m pip install -r tools\media_kit\requirements.txt

# 2) 启动解析服务（默认 127.0.0.1:8130）
.\tools\start_media_kit.ps1
```

`application.yml` 已新增 `media-kit` 配置（base-url/token/timeout-seconds/upload-dir），
修改后需重启后端。接口与示例见 `doc/MEDIA_KIT.md`，组件说明见 `tools/media_kit/README.md`。

| 文件 | 变更 |
| --- | --- |
| `tools/media_kit/**` | 内置通用视频/文档解析组件（35 个测试用例） |
| `tools/start_media_kit.ps1` | 启动解析服务 |
| `ruoyi-admin/.../MediaKitController.java` | 新增 `/project/media` 代理接口 |
| `ruoyi-admin/.../application.yml` | 新增 `media-kit` 配置 |
| `doc/MEDIA_KIT.md` | 接入与接口说明 |

## 12. 学科 / 知识点 Excel 导入

「知识图谱管理 → 学科管理」与「知识图谱管理 → 知识节点」两个页面工具栏均新增「导入」按钮，
支持 `.xlsx / .xls`，可勾选「是否更新已经存在的数据」，并提供「下载模板」。

接口（需登录 token，权限复用新增权限 `project:subject:add` / `project:point:add`）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/project/subject/importTemplate` | 下载学科导入模板 |
| POST | `/project/subject/importData?updateSupport=false` | multipart 字段 `file`，按「学科名称」判重 |
| POST | `/project/point/importTemplate` | 下载知识点导入模板 |
| POST | `/project/point/importData?updateSupport=false` | multipart 字段 `file`，按「所属学科 + 知识点名称」判重 |

模板列：

- 学科：学科名称（必填）、学科编码、学科描述、图标、显示排序
- 知识点：知识点名称（必填）、所属学科（填学科名称）、知识点内容、难度等级、重要程度、知识点类型、显示排序、状态、附件路径、视频路径
  - 难度等级：简单/较易/中等/较难/困难（或 1-5）
  - 重要程度：了解/掌握/重点/核心（或 1-4）
  - 状态：正常/停用（或 0/1）

导入行为：

- 未勾选更新：重名数据跳过；勾选更新：按主键更新 Excel 中填写的字段（空单元格不覆盖原值）；
- 所属学科不存在、名称为空的行会跳过，并在结果弹窗中列出行号与原因；
- 返回示例：`导入完成：新增 2 条，更新 0 条，跳过 1 条` + 明细。

已验证（2026-10-27）：两套模板表头正确；学科导入 `新增 2 / 跳过 1（重名）`；知识点导入
`新增 2 / 跳过 2（重名 + 学科不存在）`，难度/重要程度/状态文本正确落库；`updateSupport=true`
时两条数据均按预期更新；测试数据已清理。

| 文件 | 变更 |
| --- | --- |
| `ruoyi-admin/.../SubjectController.java` | 新增 `importData` / `importTemplate` |
| `ruoyi-admin/.../KnowledgePointController.java` | 新增 `importData` / `importTemplate` 与文本枚举解析 |
| `ruoyi-project/.../domain/KnowledgePointImport.java` | 新增知识点导入 DTO |
| `ruoyi-project/.../domain/KnowledgePoint.java` | 修正导出/导入枚举（去掉 `-` 前缀与无效 `如=` 映射） |
| `ruoyi-project/.../domain/Subject.java` | 学科编码加入 Excel 列 |
| `ruoyi-ui/src/views/project/subject/index.vue` | 新增导入按钮、导入对话框、模板下载 |
| `ruoyi-ui/src/views/project/point/index.vue` | 新增导入按钮、导入对话框、模板下载 |




