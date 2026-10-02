# AgentFlow 项目复现提示词

请使用 AI Coding 工具从零复现一个名为 **AgentFlow** 的 Java 后端项目。以下是完整的需求描述、技术栈、架构设计和实现细节，请严格按照要求逐步实现。

---

## 一、项目概述

AgentFlow 是一个支持**文件上传与嵌入**的向量搜索服务，同时集成了多种 **LangGraph4j 智能体工作流** Demo。项目核心能力包括：

1. **文档管理**：支持 PDF / DOCX / TXT 文件上传，自动分块、向量化存储与语义检索
2. **智能问答**：基于 RAG（检索增强生成）架构，向量检索 + LLM 总结回答
3. **聊天机器人**：基于 DeepSeek 模型的多轮对话，支持 SmartToolProvider 动态工具加载与 Tool 工具调用（天气、经纬度、节假日、本地命令行执行）
4. **智能体工作流**：基于 LangGraph4j 实现多种工作流模式的 Demo 演示（知识助手、报销审批、圆桌讨论、Plan-and-Execute、Reflection 反思迭代、Subagent 多 Agent 协作、Fan-Out 并发多 Agent、Agent Loop 智能体循环）
5. **前端界面**：内置单页应用（index.html），提供可视化的交互体验

---

## 二、技术栈与版本要求

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 21 | 开发语言 |
| Spring Boot | 3.5.16 | 基础 Web 框架 |
| Spring AI | 1.1.7 | AI 模型集成（OpenAI 嵌入） |
| LangChain4j | 1.20.0 | AI 应用开发框架（向量化、对话） |
| LangGraph4j | 1.8.25 | 智能体工作流编排引擎 |
| PostgreSQL + pgvector | - | 向量数据库存储 |
| DeepSeek | - | 大语言模型（对话、总结、工作流节点） |
| 通义千问 Qwen | - | Embedding 模型（text-embedding-v3，1024 维） |
| Spring Retry | - | 外部 API 工具调用的注解式重试支持 |
| Spring AOP | - | @EnableRetry 注解式重试依赖 AspectJ 织入 |
| Apache PDFBox | 3.0.3 | PDF 文本提取 |
| Apache POI | 5.3.0 | DOCX 文件解析 |
| SpringDoc OpenAPI | 2.6.0 | Swagger UI 接口文档 |
| Lombok | - | 简化代码 |

**重要版本约束**：LangChain4j 1.20.0 要求 Spring Boot 3.5.x，升级前必须验证版本兼容性。

---

## 三、项目结构

```
agentflow/
├── pom.xml
├── src/main/java/com/agentflow/
│   ├── AgentFlowApplication.java              # 启动类
│   ├── config/                             # 配置类
│   │   ├── AgentLoopConfig.java            # Agent Loop 工作流配置
│   │   ├── ChatbotV2Config.java            # 聊天机器人 2.0 配置
│   │   ├── DatabaseAvailabilityEnvironmentPostProcessor.java  # 数据库可用性检测
│   │   ├── DatabaseConfig.java             # 数据库配置
│   │   ├── DeepSeekConfig.java             # DeepSeek API 配置
│   │   ├── FanOutConfig.java               # Fan-Out 并发多 Agent 工作流配置
│   │   ├── LangGraphConfig.java            # LangGraph 知识助手工作流配置
│   │   ├── Langchain4jConfig.java          # LangChain4j 配置
│   │   ├── MultiAgentConfig.java           # Multi Agent 圆桌讨论配置
│   │   ├── PlanExecuteConfig.java          # Plan-and-Execute 工作流配置
│   │   ├── ReflectionConfig.java           # Reflection 反思迭代配置
│   │   ├── ReimbursementConfig.java        # 报销流程工作流配置
│   │   ├── RestClientConfig.java           # REST 客户端配置
│   │   ├── RetryConfig.java                # Spring Retry 重试配置
│   │   └── SubagentConfig.java             # Subagent 多 Agent 协作配置
│   ├── controller/                         # 控制器层
│   │   ├── AgentLoopController.java
│   │   ├── ChatbotController.java
│   │   ├── ChatbotV2Controller.java
│   │   ├── DocumentController.java
│   │   ├── DocumentV2Controller.java
│   │   ├── FanOutController.java
│   │   ├── LangGraphController.java
│   │   ├── MultiAgentController.java
│   │   ├── PlanExecuteController.java
│   │   ├── ReflectionController.java
│   │   ├── ReimbursementController.java
│   │   └── SubagentController.java
│   ├── model/                              # 数据模型
│   │   ├── ChatbotMessage.java
│   │   └── DocumentChunk.java
│   ├── service/                            # 服务层
│   │   ├── AgentLoopService.java
│   │   ├── ChatService.java
│   │   ├── ChatbotService.java
│   │   ├── ChatbotV2Assistant.java         # AiService 接口（动态代理）
│   │   ├── ChatbotV2Service.java
│   │   ├── EmbeddingService.java
│   │   ├── FanOutService.java
│   │   ├── LangGraphService.java
│   │   ├── Langchain4jVectorStoreService.java
│   │   ├── MultiAgentService.java
│   │   ├── PlanExecuteService.java
│   │   ├── ReflectionService.java
│   │   ├── ReimbursementService.java
│   │   ├── SubagentService.java
│   │   ├── TextExtractionService.java
│   │   └── VectorStoreService.java
│   └── tool/                               # 工具类（供 Agent 调用）
│       ├── CommandLineTool.java
│       ├── ExternalApiFetcher.java
│       ├── GeocodingTool.java
│       ├── HolidayTool.java
│       ├── SmartToolProvider.java
│       └── WeatherTool.java
├── src/main/resources/
│   ├── META-INF/spring.factories           # 注册 EnvironmentPostProcessor
│   ├── static/index.html                   # 前端页面
│   ├── application.yml                     # 应用配置
│   └── schema.sql                          # 数据库初始化脚本
└── src/test/java/com/agentflow/
    └── AgentFlowApplicationTests.java
```

---

## 四、Maven 依赖配置（pom.xml）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.16</version>
        <relativePath/>
    </parent>

    <groupId>com.agentflow</groupId>
    <artifactId>agentflow</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>agentflow</name>
    <description>Vector search service with file upload and embedding</description>

    <properties>
        <java.version>17</java.version>
        <spring-ai.version>1.1.7</spring-ai.version>
        <pdfbox.version>3.0.3</pdfbox.version>
        <poi.version>5.3.0</poi.version>
        <langchain4j.version>1.20.0</langchain4j.version>
        <langgraph4j.version>1.8.25</langgraph4j.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- SpringDoc OpenAPI (Swagger UI) -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.6.0</version>
        </dependency>

        <!-- Spring Boot JDBC -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>

        <!-- Spring AI OpenAI (Embedding) -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-openai</artifactId>
        </dependency>

        <!-- PostgreSQL Driver -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Apache PDFBox for PDF text extraction -->
        <dependency>
            <groupId>org.apache.pdfbox</groupId>
            <artifactId>pdfbox</artifactId>
            <version>${pdfbox.version}</version>
        </dependency>

        <!-- Apache POI for DOCX text extraction -->
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>${poi.version}</version>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- LangChain4j Spring Boot Starter -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-spring-boot-starter</artifactId>
        </dependency>

        <!-- LangChain4j Core -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j</artifactId>
        </dependency>

        <!-- LangChain4j OpenAI -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-open-ai</artifactId>
        </dependency>

        <!-- LangChain4j PgVector -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-pgvector</artifactId>
        </dependency>

        <!-- LangGraph4j Core -->
        <dependency>
            <groupId>org.bsc.langgraph4j</groupId>
            <artifactId>langgraph4j-core</artifactId>
        </dependency>

        <!-- LangGraph4j LangChain4j 集成 -->
        <dependency>
            <groupId>org.bsc.langgraph4j</groupId>
            <artifactId>langgraph4j-langchain4j</artifactId>
        </dependency>

        <!-- Spring Retry -->
        <dependency>
            <groupId>org.springframework.retry</groupId>
            <artifactId>spring-retry</artifactId>
        </dependency>

        <!-- Spring AOP -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>dev.langchain4j</groupId>
                <artifactId>langchain4j-bom</artifactId>
                <version>${langchain4j.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.bsc.langgraph4j</groupId>
                <artifactId>langgraph4j-bom</artifactId>
                <version>${langgraph4j.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 五、应用配置（application.yml）

```yaml
spring:
  application:
    name: agentflow

  datasource:
    url: jdbc:postgresql://192.168.141.128:5432/interview_guide
    username: postgres
    password: 123456
    driver-class-name: org.postgresql.Driver

  sql:
    init:
      mode: never

  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB

  ai:
    retry:
      max-attempts: 5
      backoff:
        initial-interval: 2s
        multiplier: 5
        max-interval: 3m
      on-client-errors: false
      exclude-on-http-codes: [401, 403, 404]
      on-http-codes: [429, 500, 502, 503, 504]
    openai:
      api-key: ${OPENAI_API_KEY:your-api-key}
      base-url: ${OPENAI_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode}
      embedding:
        options:
          model: text-embedding-v3

langchain4j:
  embedding:
    base-url: ${LANGCHAIN4J_EMBEDDING_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}
    api-key: ${LANGCHAIN4J_EMBEDDING_API_KEY:your-api-key}
    model: ${LANGCHAIN4J_EMBEDDING_MODEL:text-embedding-v3}
    max-retries: ${LANGCHAIN4J_EMBEDDING_MAX_RETRIES:2}

deepseek:
  api-key: ${DEEPSEEK_API_KEY:your-api-key}
  base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
  model: ${DEEPSEEK_MODEL:deepseek-flash}
  max-retries: ${DEEPSEEK_MAX_RETRIES:2}

vector-search:
  similarity-threshold: 0.4

server:
  port: 8081

logging:
  level:
    com.agentflow: DEBUG
    org.springframework.ai: DEBUG
```

---

## 六、数据库设计

### 6.1 初始化脚本（schema.sql）

```sql
-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create document_chunk table (Spring AI)
CREATE TABLE IF NOT EXISTS document_chunk (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    chunk_index INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    embedding vector(1024),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE document_chunk IS '文档分块表，存储上传文档的分块文本及其嵌入向量';
COMMENT ON COLUMN document_chunk.id IS '主键ID，自增';
COMMENT ON COLUMN document_chunk.file_name IS '上传的文件名';
COMMENT ON COLUMN document_chunk.chunk_index IS '分块在文档中的序号（从0开始）';
COMMENT ON COLUMN document_chunk.chunk_text IS '分块的文本内容';
COMMENT ON COLUMN document_chunk.embedding IS '嵌入向量（1024维，由通义千问模型生成）';
COMMENT ON COLUMN document_chunk.created_at IS '记录创建时间';

CREATE INDEX IF NOT EXISTS idx_document_chunk_embedding
    ON document_chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- Create document_chunk_langchain4j table (LangChain4j)
CREATE TABLE IF NOT EXISTS document_chunk_langchain4j (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    chunk_index INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    embedding vector(1024),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE document_chunk_langchain4j IS '文档分块表（LangChain4j版），存储上传文档经 LangChain4j 分块和向量化的文本及嵌入向量';
COMMENT ON COLUMN document_chunk_langchain4j.id IS '主键ID，自增';
COMMENT ON COLUMN document_chunk_langchain4j.file_name IS '上传的文件名';
COMMENT ON COLUMN document_chunk_langchain4j.chunk_index IS '分块在文档中的序号（从0开始）';
COMMENT ON COLUMN document_chunk_langchain4j.chunk_text IS '分块的文本内容';
COMMENT ON COLUMN document_chunk_langchain4j.embedding IS '嵌入向量（1024维，由通义千问模型生成，通过 LangChain4j 调用）';
COMMENT ON COLUMN document_chunk_langchain4j.created_at IS '记录创建时间';

CREATE INDEX IF NOT EXISTS idx_document_chunk_langchain4j_embedding
    ON document_chunk_langchain4j USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

### 6.2 关键设计要点

- 向量维度：1024 维（通义千问 text-embedding-v3 模型输出）
- 索引类型：IVFFlat 余弦距离索引（`vector_cosine_ops`）
- 相似度计算：`embedding <=> CAST(? AS vector)` 计算余弦距离，`1 - cosine_distance` 得到相似度
- **关键陷阱**：必须使用 `CAST(? AS vector)` 显式转换类型，否则 JDBC 会将 String 参数绑定为 `character varying`，pgvector 的 `<=>` 操作符不支持隐式转换

---

## 七、核心模块实现要点

### 7.1 数据库可选启动机制

- 使用 `EnvironmentPostProcessor` 在 Spring Boot 启动早期检测 PostgreSQL 连接可用性
- 通过 `spring.factories` 注册 `DatabaseAvailabilityEnvironmentPostProcessor`
- 数据库可连接时设置 `database.available=true`，启用向量存储和文档管理功能
- 数据库不可连接时设置 `database.available=false`（默认），跳过数据库相关功能，其他功能正常运行
- 依赖数据库的服务和控制器通过 `@ConditionalOnProperty(name = "database.available", havingValue = "true")` 条件化注册

### 7.2 双 Embedding 体系

项目同时使用 Spring AI 和 LangChain4j 两套 Embedding 体系：

**Spring AI Embedding**：
- 通过 `spring-ai-starter-model-openai` 依赖，使用 OpenAI 兼容协议调用通义千问
- `EmbeddingService` 调用 `EmbeddingModel.call()` 批量生成向量
- `VectorStoreService` 使用 `JdbcTemplate` 存储到 `document_chunk` 表

**LangChain4j Embedding**：
- 通过 `langchain4j-open-ai` 依赖，使用 `OpenAiEmbeddingModel` 调用通义千问
- `Langchain4jVectorStoreService` 使用 LangChain4j 的 `DocumentSplitter` 分块 + `EmbeddingModel` 向量化
- 存储到 `document_chunk_langchain4j` 表
- **注意**：baseUrl 需包含 `/v1` 路径（如 `https://dashscope.aliyuncs.com/compatible-mode/v1`），因为 LangChain4j 内部会在 baseUrl 后拼接 `/embeddings`

### 7.3 RestClient 配置陷阱

通义千问等第三方 OpenAI 兼容 API 可能返回 `Content-Type: application/octet-stream`，导致 Spring AI 的 RestClient 反序列化失败。需要通过 `RestClientCustomizer` 将 `APPLICATION_OCTET_STREAM` 加入 Jackson 转换器的支持媒体类型。

### 7.4 文本提取与分块策略（TextExtractionService）

- 支持 TXT、PDF、DOCX 三种格式
- **优先按标题结构分块**：正则匹配 Markdown 标题（`#`）、中文章节（`第一章`）、数字编号（`1.1`）、中文数字（`一、`）等
- **无标题时回退到固定大小分块**：1500 字符/块，100 字符重叠
- **超长分块二次分片**：单块超过 6000 字符时自动拆分（Embedding 模型 token 上限约束）
- DOCX 通过段落样式（Heading1~Heading6）识别标题

### 7.5 聊天机器人 2.0（LangChain4j AiService）

- 使用 `AiServices.builder()` 创建 `ChatbotV2Assistant` 动态代理
- 集成 `SmartToolProvider` 实现按需动态加载工具
- 使用 `MessageWindowChatMemory.withMaxMessages(20)` 管理对话记忆
- `ChatbotV2Assistant` 是一个接口，由 LangChain4j 动态代理实现，通过 `@SystemMessage` 和 `@UserMessage` 注解定义行为

### 7.6 SmartToolProvider 动态工具加载

- 实现 LangChain4j 的 `ToolProvider` 接口
- 根据用户消息中的关键词匹配，按需加载工具（天气、经纬度、节假日、命令行）
- 无关键词匹配时回退到提供全部工具
- `isDynamic()` 返回 `true`，确保多轮工具调用场景下每次 LLM 调用前重新评估工具列表
- 工具注册使用 `toolSpecificationsFrom()` 提取 `@Tool` 注解方法规格

### 7.7 外部 API 工具与重试机制

**ExternalApiFetcher**：
- 集中承载对外部免费 API 的 HTTP 调用
- 使用 `@Retryable` 注解实现瞬时故障自动重试（指数退避：1s → 2s → 4s，最多 3 次）
- 可重试：网络超时/连接失败（`ResourceAccessException`）、5xx 服务端错误
- 不可重试：4xx 客户端错误（参数或认证问题）
- **注意**：`@Retryable` 基于 Spring AOP 代理生效，必须从其他 Bean 跨 bean 调用

**工具类**：
- `WeatherTool`：通过 wttr.in 免费 API 查询天气
- `GeocodingTool`：通过 Open-Meteo 免费地理编码 API 查询经纬度
- `HolidayTool`：通过 timor.tech API 查询节假日，API 不可用时回退到本地计算
- `CommandLineTool`：执行本地命令行指令，自动适配 Windows/Linux，内置超时控制和输出截断

### 7.8 LangGraph4j 工作流架构

所有工作流共享统一的架构模式：

**状态管理**：
- 使用 `MessagesState<Object>` 作为状态类型
- 使用 `LC4jJacksonStateSerializer` 进行 JSON 序列化（解决 langchain4j 消息类未实现 Serializable 的问题）
- 节点通过 `node_async()` 包装同步操作

**工作流列表**：

1. **知识助手（LangGraphConfig）**：START → 问题分析 → 知识生成 → 回答格式化 → END
2. **报销流程（ReimbursementConfig）**：START → 提交申请 → 部门审批 → 财务审核 → 完成报销 → END（审批可能被驳回）
3. **圆桌讨论（MultiAgentConfig）**：START → 话题分析 → 产品经理 → 架构师 → 开发者 → 测试工程师 → 综合共识 → END
4. **Plan-and-Execute（PlanExecuteConfig）**：START → Planner → Executor → Replanner → 综合输出 → END
5. **Reflection 反思迭代（ReflectionConfig）**：START → 初稿生成 → 反思评审 → 修正改进（循环）→ 最终输出 → END
6. **Subagent 多 Agent 协作（SubagentConfig）**：START → 查询改写 → Supervisor（条件边路由）→ coding/writing/qa → 汇总格式化 → END
7. **Fan-Out 并发多 Agent（FanOutConfig）**：START → 任务分解 → [并发 Agent 执行] → 归总汇总 → END（并发在 Service 层通过 CompletableFuture 实现）
8. **Agent Loop 智能体循环（AgentLoopConfig）**：START → Agent（LLM 推理）→ 条件边（有工具调用？→ tools → 回到 agent 循环 / 无 → END）

**所有工作流复用同一个 `chatbotV2ChatModel` Bean**（DeepSeek ChatModel）。

### 7.9 LangChain4j 1.20.0 关键 API 变更

- `ChatModel.chat()` 返回 `ChatResponse` 而非 `String`，需通过 `chatResponse.aiMessage().text()` 获取文本
- `ChatRequest` 用于传入工具规格（`toolSpecifications`）
- `AiMessage.toolExecutionRequests()` 获取工具调用请求列表

---

## 八、API 接口设计

### 8.1 文档管理（Spring AI）- `/api/documents`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/documents/upload` | 上传文档（multipart/form-data） |
| POST | `/api/documents/chat` | 智能问答（RAG） |
| GET | `/api/documents/search` | 向量搜索 |

### 8.2 文档管理 V2（LangChain4j）- `/api/documents/v2`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/documents/v2/upload` | 上传文档（LangChain4j 分块） |
| POST | `/api/documents/v2/chat` | 智能问答（支持查询改写） |
| GET | `/api/documents/v2/search` | 向量搜索（LangChain4j） |

### 8.3 聊天机器人 - `/api/chatbot`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chatbot/send` | 发送消息（支持多轮对话历史） |

### 8.4 聊天机器人 2.0 - `/api/chatbot/v2`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chatbot/v2/send` | 发送消息（AiService + 动态工具） |

### 8.5 LangGraph4j 工作流接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/langgraph/process` | 知识助手工作流 |
| POST | `/api/reimbursement/process` | 报销流程 |
| POST | `/api/multiagent/process` | 圆桌讨论 |
| POST | `/api/planexecute/process` | Plan-and-Execute |
| POST | `/api/reflection/process` | Reflection 反思迭代 |
| POST | `/api/subagent/process` | Subagent 多 Agent 协作 |
| POST | `/api/agentloop/process` | Agent Loop 智能体循环 |
| POST | `/api/fanout/process` | Fan-Out 并发多 Agent |

---

## 九、数据模型

### 9.1 DocumentChunk

```java
public class DocumentChunk {
    private Long id;
    private String fileName;
    private Integer chunkIndex;
    private String chunkText;
    private float[] embedding;
    private Double cosineDistance;  // 余弦距离，范围[0,2]，值越小越相似
    private Double similarity;     // 相似度 = 1 - cosineDistance，值越大越相似
    private LocalDateTime createdAt;
}
```

### 9.2 ChatbotMessage

```java
public class ChatbotMessage {
    private String role;     // user / assistant / system
    private String content;
}
```

---

## 十、实现步骤建议

1. **初始化项目**：创建 Spring Boot 项目，配置 pom.xml 依赖
2. **数据库层**：编写 schema.sql，实现 DatabaseAvailabilityEnvironmentPostProcessor 和 DatabaseConfig
3. **Embedding 服务**：分别实现 Spring AI 和 LangChain4j 两套 Embedding 体系
4. **文本提取**：实现 TextExtractionService（支持 PDF/DOCX/TXT，按标题分块）
5. **向量存储**：实现 VectorStoreService 和 Langchain4jVectorStoreService（注意 CAST(? AS vector)）
6. **文档管理接口**：实现 DocumentController 和 DocumentV2Controller
7. **聊天机器人**：实现 ChatbotV2Config（AiService + SmartToolProvider）
8. **工具类**：实现 WeatherTool、GeocodingTool、HolidayTool、CommandLineTool、ExternalApiFetcher
9. **LangGraph4j 工作流**：逐个实现 8 个工作流配置和对应的 Service/Controller
10. **前端页面**：实现 index.html 单页应用
11. **RestClient 配置**：处理 application/octet-stream 兼容问题
12. **重试机制**：配置 RetryConfig 和 ExternalApiFetcher 的 @Retryable

---

## 十一、关键陷阱与注意事项

1. **PostgreSQL JDBC 向量参数绑定**：必须使用 `CAST(? AS vector)` 显式转换，否则报 "operator does not exist: vector <=> character varying" 错误
2. **LangChain4j Embedding baseUrl**：需包含 `/v1` 路径，因为 LangChain4j 内部会拼接 `/embeddings`
3. **LangChain4j ChatModel.chat() 返回类型**：1.20.0 版本返回 `ChatResponse`，不是 `String`
4. **LangGraph4j 序列化**：必须使用 `LC4jJacksonStateSerializer` 替代默认 Java 序列化器
5. **Spring AI RestClient 兼容**：通义千问返回 `application/octet-stream` 时需额外配置
6. **@Retryable 跨 Bean 调用**：工具类必须注入 ExternalApiFetcher 跨 bean 调用，自调用不会触发重试
7. **LangChain4j 与 Spring Boot 版本兼容**：LangChain4j 1.20.0 要求 Spring Boot 3.5.x
8. **向量维度一致性**：Embedding 模型输出维度（1024）必须与数据库 `vector(1024)` 字段一致
9. **RowMapper 显式映射**：必须显式映射 `cosine_distance` 和 `similarity` 字段
10. **LLM 改写输出兜底**：查询改写结果需要程序化兜底截断，防止异常输出

---

## 十二、运行环境

- **JDK**：17+
- **数据库**：PostgreSQL（需安装 pgvector 扩展）
- **端口**：8081
- **前端页面**：`http://localhost:8081/`
- **Swagger UI**：`http://localhost:8081/swagger-ui.html`

### 快速启动

```bash
# 1. 确保 PostgreSQL 已启动并安装 pgvector 扩展
# 2. 执行数据库初始化脚本
psql -h localhost -U postgres -d interview_guide -f src/main/resources/schema.sql

# 3. 启动应用
mvn spring-boot:run

# 4. 访问
# 前端页面：http://localhost:8081/
# Swagger UI：http://localhost:8081/swagger-ui.html
```
