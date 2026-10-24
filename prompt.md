# AgentFlow 项目复现提示词

请使用 AI Coding 工具从零复现一个名为 **AgentFlow** 的 Java 后端项目。以下是完整的需求描述、技术栈、架构设计和实现细节，请严格按照要求逐步实现。

---

## 一、项目概述

AgentFlow 是一个支持**文件上传与嵌入**的向量搜索服务，同时集成了多种 **LangGraph4j 智能体工作流** Demo。项目核心能力包括：

1. **文档管理**：支持 PDF / DOCX / TXT 文件上传，自动分块、向量化存储与语义检索
2. **智能问答**：基于 RAG（检索增强生成）架构，向量检索 + LLM 总结回答
3. **聊天机器人**：基于 DeepSeek 模型的多轮对话，支持 SmartToolProvider 动态工具加载与 Tool 工具调用（天气、经纬度、节假日、本地命令行执行）
4. **智能体工作流**：基于 LangGraph4j 实现多种工作流模式的 Demo 演示（知识助手、报销审批、圆桌讨论、Plan-and-Execute、Reflection 反思迭代、Subagent 多 Agent 协作、Fan-Out 并发多 Agent、Agent Loop 智能体循环）
5. **知识图谱 3.0**：文档上传后自动抽取知识三元组（实体 + 关系），写入知识图谱，支持按文件查询图谱
6. **商品查询（Text-to-SQL）**：自然语言转 SQL 查询商品销售数据，支持分页、SQL 校验与安全控制
7. **向量存储双后端**：通过 `ChunkVectorRepository` 抽象层支持 PostgreSQL(pgvector) 和 Milvus 两种向量存储后端，由配置切换
8. **前端界面**：内置单页应用（index.html），提供可视化的交互体验

---

## 二、技术栈与版本要求

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 21 | 开发语言 |
| Spring Boot | 3.5.16 | 基础 Web 框架 |
| Spring AI | 1.1.7 | AI 模型集成（OpenAI 嵌入） |
| LangChain4j | 1.20.0 | AI 应用开发框架（向量化、对话） |
| LangGraph4j | 1.8.25 | 智能体工作流编排引擎 |
| PostgreSQL + pgvector | - | 向量数据库存储（后端之一） |
| Milvus | 2.6.3（SDK） | 向量数据库存储（后端之二，可选） |
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
│   │   ├── MilvusConfig.java               # Milvus 向量库配置
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
│   │   ├── DocumentV3Controller.java       # 知识图谱 3.0 控制器
│   │   ├── FanOutController.java
│   │   ├── LangGraphController.java
│   │   ├── MultiAgentController.java
│   │   ├── PlanExecuteController.java
│   │   ├── ProductController.java          # 商品查询（Text-to-SQL）控制器
│   │   ├── ReflectionController.java
│   │   ├── ReimbursementController.java
│   │   └── SubagentController.java
│   ├── model/                              # 数据模型
│   │   ├── ChatbotMessage.java
│   │   ├── DocumentChunk.java
│   │   ├── ExtractedTriple.java            # LLM 抽取的知识三元组
│   │   ├── KgEntity.java                   # 知识图谱实体
│   │   ├── KgRelation.java                 # 知识图谱关系
│   │   └── ProductSales.java               # 商品销售实体
│   ├── service/                            # 服务层
│   │   ├── AgentLoopService.java
│   │   ├── ChatService.java
│   │   ├── ChatbotService.java
│   │   ├── ChatbotV2Assistant.java         # AiService 接口（动态代理）
│   │   ├── ChatbotV2Service.java
│   │   ├── ChunkVectorRepository.java      # 向量仓储抽象（Postgres/Milvus）
│   │   ├── EmbeddingService.java
│   │   ├── EntityResolutionService.java    # 实体对齐与规范化
│   │   ├── FanOutService.java
│   │   ├── KnowledgeExtractionService.java # LLM 三元组抽取与校验
│   │   ├── KnowledgeGraphIngestService.java # 知识图谱导入编排
│   │   ├── KnowledgeGraphStore.java        # 知识图谱 JDBC 读写
│   │   ├── LangGraphService.java
│   │   ├── Langchain4jVectorStoreService.java
│   │   ├── MilvusChunkVectorRepository.java  # Milvus 向量仓储实现
│   │   ├── MultiAgentService.java
│   │   ├── PlanExecuteService.java
│   │   ├── PostgresChunkVectorRepository.java # PostgreSQL 向量仓储实现
│   │   ├── ProductQueryService.java        # 商品查询服务（Text-to-SQL）
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
│   ├── product.sql                         # 商品销售表初始化脚本
│   └── schema.sql                          # 数据库初始化脚本
├── samples/
│   └── knowledge-graph-demo.txt            # 知识图谱测试样例文档
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
        <java.version>21</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring-ai.version>1.1.7</spring-ai.version>
        <pdfbox.version>3.0.3</pdfbox.version>
        <poi.version>5.3.0</poi.version>
        <langchain4j.version>1.20.0</langchain4j.version>
        <langgraph4j.version>1.8.25</langgraph4j.version>
        <milvus.sdk.version>2.6.3</milvus.sdk.version>
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

        <!-- Milvus Java SDK（向量库，与 vector-store.type=milvus 配合使用） -->
        <dependency>
            <groupId>io.milvus</groupId>
            <artifactId>milvus-sdk-java</artifactId>
            <version>${milvus.sdk.version}</version>
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

# 向量存储后端开关：postgres（pgvector）或 milvus
vector-store:
  type: ${VECTOR_STORE_TYPE:postgres}   # postgres | milvus
  milvus:
    uri: ${MILVUS_URI:http://localhost:19530}
    token: ${MILVUS_TOKEN:}
    username: ${MILVUS_USERNAME:root}
    password: ${MILVUS_PASSWORD:123456}
    database: ${MILVUS_DATABASE:default}
    dimension: ${MILVUS_DIMENSION:1024}
    collection-document-chunk: document_chunk
    collection-langchain4j: document_chunk_langchain4j
    collection-v3: document_chunk_v3

# 知识图谱 3.0：按块抽取三元组
knowledge-graph:
  confidence-threshold: 0.6
  require-mention-in-chunk: true
  allowed-types: Person,Org,Concept,Location,Event,Product
  allowed-predicates: 属于,包含,依赖,导致,位于,参与,相关,任职于

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

-- Create document_chunk_v3 table (知识图谱 3.0 分块溯源)
CREATE TABLE IF NOT EXISTS document_chunk_v3 (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    chunk_index INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    embedding vector(1024),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE document_chunk_v3 IS '文档分块表（知识图谱 3.0），存储上传文档分块文本、向量，并作为图谱关系溯源';
COMMENT ON COLUMN document_chunk_v3.id IS '主键ID，自增';
COMMENT ON COLUMN document_chunk_v3.file_name IS '上传的文件名';
COMMENT ON COLUMN document_chunk_v3.chunk_index IS '分块在文档中的序号（从0开始）';
COMMENT ON COLUMN document_chunk_v3.chunk_text IS '分块的文本内容';
COMMENT ON COLUMN document_chunk_v3.embedding IS '嵌入向量（1024维）';
COMMENT ON COLUMN document_chunk_v3.created_at IS '记录创建时间';

CREATE INDEX IF NOT EXISTS idx_document_chunk_v3_embedding
    ON document_chunk_v3 USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX IF NOT EXISTS idx_document_chunk_v3_file_name
    ON document_chunk_v3 (file_name);

-- 知识图谱实体
CREATE TABLE IF NOT EXISTS kg_entity (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(512) NOT NULL,
    canonical_name VARCHAR(512) NOT NULL,
    type VARCHAR(64) NOT NULL,
    aliases TEXT,
    embedding vector(1024),
    extra JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_kg_entity_canonical_type UNIQUE (canonical_name, type)
);

COMMENT ON TABLE kg_entity IS '知识图谱实体表';
COMMENT ON COLUMN kg_entity.name IS '实体展示名称';
COMMENT ON COLUMN kg_entity.canonical_name IS '规范化名称，用于跨文档对齐';
COMMENT ON COLUMN kg_entity.type IS '实体类型：Person/Org/Concept/Location/Event/Product/Document/DocSection';

-- 知识图谱关系
CREATE TABLE IF NOT EXISTS kg_relation (
    id BIGSERIAL PRIMARY KEY,
    from_entity_id BIGINT NOT NULL REFERENCES kg_entity(id),
    predicate VARCHAR(64) NOT NULL,
    to_entity_id BIGINT NOT NULL REFERENCES kg_entity(id),
    confidence DOUBLE PRECISION,
    chunk_id BIGINT REFERENCES document_chunk_v3(id),
    file_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_kg_relation_edge UNIQUE (from_entity_id, predicate, to_entity_id, chunk_id)
);

COMMENT ON TABLE kg_relation IS '知识图谱关系表（主谓宾三元组，带分块溯源）';
COMMENT ON COLUMN kg_relation.predicate IS '关系谓词（受控词表）';
COMMENT ON COLUMN kg_relation.confidence IS '抽取置信度 0~1';
COMMENT ON COLUMN kg_relation.chunk_id IS '来源分块 ID';
COMMENT ON COLUMN kg_relation.file_name IS '来源文件名';

CREATE INDEX IF NOT EXISTS idx_kg_relation_file_name ON kg_relation (file_name);
CREATE INDEX IF NOT EXISTS idx_kg_relation_from ON kg_relation (from_entity_id);
CREATE INDEX IF NOT EXISTS idx_kg_relation_to ON kg_relation (to_entity_id);

-- 商品销售明细表（Text-to-SQL）
CREATE TABLE IF NOT EXISTS product_sales (
    id BIGSERIAL PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    product_id VARCHAR(16) NOT NULL,
    product_name VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    quantity INTEGER NOT NULL,
    discount DECIMAL(3,2) NOT NULL DEFAULT 1.00,
    total_amount DECIMAL(12,2) NOT NULL,
    customer_name VARCHAR(32) DEFAULT NULL,
    region VARCHAR(32) DEFAULT NULL,
    payment_method VARCHAR(16) DEFAULT NULL,
    order_status VARCHAR(16) NOT NULL DEFAULT '待付款',
    sale_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_sales_order_no UNIQUE (order_no)
);

COMMENT ON TABLE product_sales IS '商品销售明细表';
COMMENT ON COLUMN product_sales.id IS '主键ID，自增';
COMMENT ON COLUMN product_sales.order_no IS '订单编号';
COMMENT ON COLUMN product_sales.product_id IS '商品编码';
COMMENT ON COLUMN product_sales.product_name IS '商品名称';
COMMENT ON COLUMN product_sales.category IS '商品类别';
COMMENT ON COLUMN product_sales.unit_price IS '商品单价(元)';
COMMENT ON COLUMN product_sales.quantity IS '购买数量';
COMMENT ON COLUMN product_sales.discount IS '折扣率(1.00=无折扣)';
COMMENT ON COLUMN product_sales.total_amount IS '订单金额=单价*数量*折扣';
COMMENT ON COLUMN product_sales.customer_name IS '客户姓名';
COMMENT ON COLUMN product_sales.region IS '销售区域';
COMMENT ON COLUMN product_sales.payment_method IS '支付方式';
COMMENT ON COLUMN product_sales.order_status IS '订单状态';
COMMENT ON COLUMN product_sales.sale_date IS '销售日期';
COMMENT ON COLUMN product_sales.created_at IS '创建时间';
COMMENT ON COLUMN product_sales.updated_at IS '更新时间';

CREATE INDEX IF NOT EXISTS idx_product_sales_product_id ON product_sales (product_id);
CREATE INDEX IF NOT EXISTS idx_product_sales_category ON product_sales (category);
CREATE INDEX IF NOT EXISTS idx_product_sales_sale_date ON product_sales (sale_date);
CREATE INDEX IF NOT EXISTS idx_product_sales_region ON product_sales (region);
```

### 6.2 关键设计要点

- 向量维度：1024 维（通义千问 text-embedding-v3 模型输出）
- 索引类型：IVFFlat 余弦距离索引（`vector_cosine_ops`）
- 相似度计算：`embedding <=> CAST(? AS vector)` 计算余弦距离，`1 - cosine_distance` 得到相似度
- **关键陷阱**：必须使用 `CAST(? AS vector)` 显式转换类型，否则 JDBC 会将 String 参数绑定为 `character varying`，pgvector 的 `<=>` 操作符不支持隐式转换
- **知识图谱表**：`kg_entity` 使用 `(canonical_name, type)` 唯一约束实现 upsert；`kg_relation` 使用 `(from_entity_id, predicate, to_entity_id, chunk_id)` 唯一约束去重

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

### 7.10 向量存储双后端架构（ChunkVectorRepository）

项目通过 `ChunkVectorRepository` 接口抽象向量存储操作，支持 PostgreSQL(pgvector) 和 Milvus 两种后端：

**接口定义**：
- `saveChunks(storeName, fileName, texts, embeddings)`：批量保存分块与向量
- `search(storeName, queryEmbedding, topK)`：向量相似度搜索

**实现类**：
- `PostgresChunkVectorRepository`：当 `vector-store.type=postgres`（默认）且 `database.available=true` 时注册，使用 `JdbcTemplate` + `CAST(? AS vector)`
- `MilvusChunkVectorRepository`：当 `vector-store.type=milvus` 时注册，使用 `MilvusClientV2` 操作 Collection

**服务层委托**：
- `VectorStoreService` 和 `Langchain4jVectorStoreService` 均委托 `ChunkVectorRepository` 实现存储与检索
- 通过 `@ConditionalOnBean(ChunkVectorRepository.class)` 确保后端可用时才注册服务

**Milvus 配置**：
- `MilvusConfig` 在 `vector-store.type=milvus` 时初始化 `MilvusClientV2` 并自动创建三个 Collection（document_chunk、document_chunk_langchain4j、document_chunk_v3）
- Collection 字段：id(Int64 PK)、file_name(VarChar)、chunk_index(Int32)、chunk_text(VarChar)、embedding(FloatVector)、created_at(Int64)
- 索引类型：COSINE 度量

### 7.11 商品查询模块（Text-to-SQL）

项目提供自然语言查询商品销售数据的功能，通过 DeepSeek 将用户自然语言转换为 SQL 并执行分页查询：

**核心组件**：
- `ProductController`：REST 接口层，接收自然语言或已有 SQL，返回分页查询结果
- `ProductQueryService`：业务逻辑层，调用 DeepSeek 生成 SQL，执行 SQL 校验与分页查询
- `ProductSales`：数据模型，对应 `product_sales` 表

**Text-to-SQL 流程**：
1. 用户输入自然语言问题（如"查询北京地区已完成的手机数码订单"）
2. `ProductQueryService` 构造 system prompt（包含表结构和规则）+ user message
3. 调用 DeepSeek `/v1/chat/completions` 接口生成 SQL
4. SQL 校验：去除 Markdown 代码块、拒绝写操作关键字（INSERT/UPDATE/DELETE/DROP 等）、仅允许单条 SELECT
5. 去除模型可能附带的 LIMIT/OFFSET，由服务端统一分页
6. 执行 COUNT 查询获取总条数，再执行分页查询获取数据
7. 返回基础 SQL、分页 SQL、当前页数据、总条数、page、size

**SQL 安全控制**：
- 正则匹配禁止的写操作关键字（INSERT/UPDATE/DELETE/DROP/ALTER/TRUNCATE/CREATE/GRANT/REVOKE/COPY/EXECUTE/CALL/MERGE/REPLACE/ATTACH/DETACH）
- 禁止多条 SQL（检查分号）
- 仅允许 SELECT 或 WITH...SELECT 语句
- temperature 设置为 0.1 降低随机性，提高 SQL 生成准确性

**分页优化**：
- 首次查询传入 `question`，系统生成 SQL 并执行分页查询
- 翻页时可传入 `sql`（复用已生成的 SQL），避免重复调用大模型
- 默认 page=1, size=10，最大 size=100

### 7.12 知识图谱 3.0

文档上传后自动抽取知识三元组，构建知识图谱：

**导入流程（KnowledgeGraphIngestService）**：
1. 文本提取与分块（复用 TextExtractionService）
2. 分块向量化写入 `document_chunk_v3`（通过 EmbeddingService + KnowledgeGraphStore）
3. 创建 Document 实体和 DocSection 实体，写入结构边（包含、下一节）
4. 按块调用 DeepSeek 抽取三元组（KnowledgeExtractionService）
5. 校验三元组（实体类型受控词表、谓词受控词表、置信度阈值、原文提及检查）
6. 实体对齐与 upsert（EntityResolutionService + KnowledgeGraphStore）
7. 写入关系边，并添加“提及”边连接章节与实体

**抽取校验规则（KnowledgeExtractionService）**：
- 实体类型必须在 `knowledge-graph.allowed-types` 内
- 谓词必须在 `knowledge-graph.allowed-predicates` 内
- 置信度必须 >= `knowledge-graph.confidence-threshold`
- 实体名必须在原文分块中出现（可配置 `knowledge-graph.require-mention-in-chunk`）

**实体对齐（EntityResolutionService）**：
- 一期仅做名称规范化（去首尾空白、压缩连续空白）
- 精确匹配交给数据库 `(canonical_name, type)` 唯一约束

**知识图谱存储（KnowledgeGraphStore）**：
- 分块保存使用 `RETURNING id` 获取生成的主键，用于后续关系边关联
- 实体 upsert 使用 `ON CONFLICT (canonical_name, type) DO UPDATE` 实现幂等
- 关系写入使用 `ON CONFLICT (from_entity_id, predicate, to_entity_id, chunk_id) DO NOTHING` 去重
- 图谱查询按文件名返回触及的实体列表与全部关系三元组

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

### 8.6 文档管理 V3（知识图谱）- `/api/documents/v3`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/documents/v3/upload` | 上传文档并构建知识图谱（抽取三元组） |
| GET | `/api/documents/v3/graph` | 按文件名查询知识图谱（实体 + 关系） |

### 8.7 商品查询（Text-to-SQL）- `/api/product`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/product/query` | 自然语言 / 已有 SQL 分页查询商品销售数据 |

**请求体示例**：

```json
// 首次查询（传入自然语言）
{
  "question": "查询北京地区已完成的手机数码订单",
  "page": 1,
  "size": 10
}

// 翻页查询（复用已生成的 SQL）
{
  "sql": "SELECT * FROM product_sales WHERE region = '北京' AND order_status = '已完成'",
  "page": 2,
  "size": 10
}
```

**响应示例**：

```json
{
  "success": true,
  "question": "查询北京地区已完成的手机数码订单",
  "sql": "SELECT * FROM product_sales WHERE region = '北京' AND order_status = '已完成' AND category = '手机数码'",
  "pageSql": "SELECT * FROM product_sales WHERE region = '北京' AND order_status = '已完成' AND category = '手机数码' LIMIT 10 OFFSET 0",
  "data": [
    {
      "id": 1,
      "order_no": "SO20240501001",
      "product_id": "P01",
      "product_name": "iPhone 15 Pro",
      "category": "手机数码",
      "unit_price": 7999.00,
      "quantity": 2,
      "discount": 1.00,
      "total_amount": 15998.00,
      "customer_name": "张伟",
      "region": "北京",
      "payment_method": "微信支付",
      "order_status": "已完成",
      "sale_date": "2024-05-01"
    }
  ],
  "total": 5,
  "page": 1,
  "size": 10
}
```

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

### 9.3 ExtractedTriple

```java
public class ExtractedTriple {
    private String subject;        // 主语实体名称
    private String subjectType;    // 主语实体类型
    private String predicate;      // 关系谓词
    private String object;         // 宾语实体名称
    private String objectType;     // 宾语实体类型
    private Double confidence;     // 抽取置信度 0~1
}
```

### 9.4 KgEntity

```java
public class KgEntity {
    private Long id;
    private String name;           // 实体展示名称
    private String canonicalName;  // 规范化名称
    private String type;           // Person/Org/Concept/Location/Event/Product/Document/DocSection
}
```

### 9.5 KgRelation

```java
public class KgRelation {
    private Long id;
    private Long fromEntityId;
    private String fromName;       // 查询展示用，非表字段
    private String fromType;       // 查询展示用，非表字段
    private String predicate;
    private Long toEntityId;
    private String toName;         // 查询展示用，非表字段
    private String toType;         // 查询展示用，非表字段
    private Double confidence;
    private Long chunkId;
    private String fileName;
}
```

### 9.6 ProductSales

```java
public class ProductSales {
    private Long id;
    private String orderNo;          // 订单编号
    private String productId;        // 商品编码
    private String productName;      // 商品名称
    private String category;         // 商品类别：手机数码/家用电器/运动户外/食品饮料/美妆个护/电脑办公
    private BigDecimal unitPrice;    // 商品单价(元)
    private Integer quantity;        // 购买数量
    private BigDecimal discount;     // 折扣率(1.00=无折扣)
    private BigDecimal totalAmount;  // 订单金额=单价*数量*折扣
    private String customerName;     // 客户姓名
    private String region;           // 销售区域：北京/上海/广州/深圳/杭州/成都/武汉/南京/西安/重庆
    private String paymentMethod;    // 支付方式：微信支付/支付宝/银行卡/信用卡
    private String orderStatus;      // 订单状态：待付款/已发货/已完成/已取消
    private LocalDate saleDate;      // 销售日期
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

---

## 十、实现步骤建议

1. **初始化项目**：创建 Spring Boot 项目，配置 pom.xml 依赖
2. **数据库层**：编写 schema.sql 和 product.sql，实现 DatabaseAvailabilityEnvironmentPostProcessor 和 DatabaseConfig
3. **Embedding 服务**：分别实现 Spring AI 和 LangChain4j 两套 Embedding 体系
4. **文本提取**：实现 TextExtractionService（支持 PDF/DOCX/TXT，按标题分块）
5. **向量存储抽象层**：实现 ChunkVectorRepository 接口及其 Postgres/Milvus 两个实现类
6. **文档管理接口**：实现 DocumentController 和 DocumentV2Controller（通过 VectorStoreService/Langchain4jVectorStoreService 委托仓储层）
7. **Milvus 集成**（可选）：实现 MilvusConfig、MilvusChunkVectorRepository
8. **聊天机器人**：实现 ChatbotV2Config（AiService + SmartToolProvider）
9. **工具类**：实现 WeatherTool、GeocodingTool、HolidayTool、CommandLineTool、ExternalApiFetcher
10. **LangGraph4j 工作流**：逐个实现 8 个工作流配置和对应的 Service/Controller
11. **知识图谱 3.0**：实现 KnowledgeExtractionService、KnowledgeGraphIngestService、KnowledgeGraphStore、EntityResolutionService、DocumentV3Controller
12. **商品查询（Text-to-SQL）**：实现 ProductQueryService、ProductController、ProductSales 模型，构造 system prompt 引导 DeepSeek 生成 SQL，实现 SQL 安全校验与分页查询
13. **前端页面**：实现 index.html 单页应用
14. **RestClient 配置**：处理 application/octet-stream 兼容问题
15. **重试机制**：配置 RetryConfig 和 ExternalApiFetcher 的 @Retryable

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
11. **知识图谱 upsert 幂等**：`kg_entity` 使用 `(canonical_name, type)` 唯一约束，`kg_relation` 使用 `(from_entity_id, predicate, to_entity_id, chunk_id)` 唯一约束，重复导入不会产生重复数据
12. **ChunkVectorRepository 条件注册**：Postgres 实现需要 `vector-store.type=postgres` 且 `database.available=true`；Milvus 实现需要 `vector-store.type=milvus`，服务层通过 `@ConditionalOnBean` 依赖仓储层
13. **Text-to-SQL 安全控制**：必须对 LLM 生成的 SQL 进行严格校验，包括：拒绝写操作关键字、禁止多条 SQL、仅允许 SELECT/WITH...SELECT、去除 Markdown 代码块、去除模型附带的 LIMIT/OFFSET
14. **Text-to-SQL 分页优化**：翻页时允许传入已生成的 SQL 复用，避免重复调用大模型，降低延迟和成本
15. **DeepSeek temperature 设置**：Text-to-SQL 场景建议将 temperature 设置为 0.1，降低随机性，提高 SQL 生成准确性

---

## 十二、运行环境

- **JDK**：21
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
