# AgentFlow - 向量检索与智能体工作流演示服务

## 项目简介

AgentFlow 是一个支持**文件上传与嵌入**的向量搜索服务，同时集成了多种 **LangGraph4j 智能体工作流** Demo，涵盖知识助手、报销审批、圆桌讨论、Plan-and-Execute、Reflection 反思迭代、Subagent 多 Agent 协作、Fan-Out 并发多 Agent、Agent Loop 智能体循环等场景。

核心能力：
- **文档管理**：支持 PDF / DOCX / TXT 文件上传，自动分块、向量化存储与语义检索
- **智能问答**：基于 RAG（检索增强生成）架构，向量检索 + LLM 总结回答
- **聊天机器人**：基于 DeepSeek 模型的多轮对话，支持 SmartToolProvider 动态工具加载与 Tool 工具调用（天气、经纬度、节假日、本地命令行执行）
- **智能体工作流**：基于 LangGraph4j 实现多种工作流模式的 Demo 演示
- **前端界面**：内置单页应用（index.html），提供可视化的交互体验

---

## 技术栈

| 技术 | 版本     | 说明 |
|------|--------|------|
| Java | 21     | 开发语言 |
| Spring Boot | 3.5.16 | 基础 Web 框架 |
| Spring AI | 1.1.7  | AI 模型集成（OpenAI 嵌入） |
| LangChain4j | 1.20.0 | AI 应用开发框架（向量化、对话） |
| LangGraph4j | 1.8.25 | 智能体工作流编排引擎 |
| PostgreSQL + pgvector | -      | 向量数据库存储 |
| DeepSeek | -      | 大语言模型（对话、总结、工作流节点） |
| 通义千问 Qwen | -      | Embedding 模型（text-embedding-v3，1024 维） |
| Spring Retry | -      | 外部 API 工具调用的注解式重试支持 |
| Spring AOP | -      | @EnableRetry 注解式重试依赖 AspectJ 织入 |
| Apache PDFBox | 3.0.3  | PDF 文本提取 |
| Apache POI | 5.3.0  | DOCX 文件解析 |
| SpringDoc OpenAPI | 2.6.0  | Swagger UI 接口文档 |
| Lombok | -      | 简化代码 |

---

## 项目结构

```
src/main/java/com/agentflow/
├── config/                     # 配置类
│   ├── AgentLoopConfig.java        # Agent Loop 工作流配置
│   ├── ChatbotV2Config.java        # 聊天机器人 2.0 配置
│   ├── DatabaseAvailabilityEnvironmentPostProcessor.java  # 数据库可用性检测
│   ├── DatabaseConfig.java          # 数据库配置
│   ├── DeepSeekConfig.java          # DeepSeek API 配置
│   ├── FanOutConfig.java            # Fan-Out 并发多 Agent 工作流配置
│   ├── LangGraphConfig.java         # LangGraph 知识助手工作流配置
│   ├── Langchain4jConfig.java       # LangChain4j 配置
│   ├── MultiAgentConfig.java        # Multi Agent 圆桌讨论配置
│   ├── PlanExecuteConfig.java       # Plan-and-Execute 工作流配置
│   ├── ReflectionConfig.java        # Reflection 反思迭代配置
│   ├── ReimbursementConfig.java     # 报销流程工作流配置
│   ├── RestClientConfig.java        # REST 客户端配置
│   ├── RetryConfig.java             # Spring Retry 重试配置
│   └── SubagentConfig.java          # Subagent 多 Agent 协作配置
├── controller/                 # 控制器层
│   ├── AgentLoopController.java     # Agent Loop 智能体循环接口
│   ├── ChatbotController.java       # 聊天机器人接口
│   ├── ChatbotV2Controller.java     # 聊天机器人 2.0 接口
│   ├── DocumentController.java      # 文档管理接口（Spring AI）
│   ├── DocumentV2Controller.java    # 文档管理 V2 接口（LangChain4j）
│   ├── FanOutController.java        # Fan-Out 并发多 Agent 接口
│   ├── LangGraphController.java     # LangGraph 知识助手接口
│   ├── MultiAgentController.java    # Multi Agent 圆桌讨论接口
│   ├── PlanExecuteController.java   # Plan-and-Execute 工作流接口
│   ├── ReflectionController.java    # Reflection 反思迭代接口
│   ├── ReimbursementController.java # 报销流程接口
│   └── SubagentController.java      # Subagent 多 Agent 协作接口
├── model/                      # 数据模型
│   ├── ChatbotMessage.java          # 聊天消息模型
│   └── DocumentChunk.java           # 文档分块模型
├── service/                    # 服务层
│   ├── AgentLoopService.java        # Agent Loop 服务
│   ├── ChatService.java             # 对话总结服务
│   ├── ChatbotService.java          # 聊天机器人服务
│   ├── ChatbotV2Assistant.java      # 聊天机器人 2.0 助手
│   ├── ChatbotV2Service.java        # 聊天机器人 2.0 服务
│   ├── EmbeddingService.java        # 嵌入向量服务（Spring AI）
│   ├── FanOutService.java           # Fan-Out 并发多 Agent 服务
│   ├── LangGraphService.java        # LangGraph 工作流服务
│   ├── Langchain4jVectorStoreService.java  # LangChain4j 向量存储服务
│   ├── MultiAgentService.java       # Multi Agent 服务
│   ├── PlanExecuteService.java      # Plan-and-Execute 服务
│   ├── ReflectionService.java       # Reflection 服务
│   ├── ReimbursementService.java    # 报销流程服务
│   ├── SubagentService.java         # Subagent 服务
│   ├── TextExtractionService.java   # 文本提取服务
│   └── VectorStoreService.java      # 向量存储服务（Spring AI）
├── tool/                       # 工具类（供 Agent 调用）
│   ├── CommandLineTool.java         # 本地命令行执行工具
│   ├── ExternalApiFetcher.java      # 外部 API 取数组件（含注解式重试）
│   ├── GeocodingTool.java           # 经纬度查询工具
│   ├── HolidayTool.java             # 节假日查询工具
│   ├── SmartToolProvider.java       # 智能工具提供者（按需动态加载工具）
│   └── WeatherTool.java             # 天气查询工具
└── AgentFlowApplication.java      # 启动类
```

---

## 运行环境

- **JDK**：17+
- **数据库**：PostgreSQL（需安装 pgvector 扩展）
- **端口**：8081
- **前端页面**：`http://localhost:8081/`
- **Swagger UI**：`http://localhost:8081/swagger-ui.html`

### 数据库初始化

执行 `src/main/resources/schema.sql` 创建数据表：
- `document_chunk`：Spring AI 向量存储表
- `document_chunk_langchain4j`：LangChain4j 向量存储表

两张表结构相同，均包含 1024 维向量字段，并建立 IVFFlat 余弦距离索引。

---

## API 接口详情

### 1. 文档管理（Spring AI）

> 控制器：`DocumentController` | 路径前缀：`/api/documents`
> 
> 仅在 `database.available=true` 时注册

#### 1.1 上传文档

- **URL**：`POST /api/documents/upload`
- **描述**：上传文档文件，自动提取文本、分块、生成嵌入向量并存入数据库
- **Content-Type**：`multipart/form-data`
- **请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | MultipartFile | 是 | 文档文件，支持 TXT、PDF、DOCX 格式 |

- **响应示例**：

```json
{
  "success": true,
  "message": "文件处理成功",
  "fileName": "example.pdf",
  "chunkCount": 12
}
```

#### 1.2 智能问答

- **URL**：`POST /api/documents/chat`
- **描述**：根据用户提问，通过向量检索找到最相关的文档分块，由 DeepSeek 总结回答
- **Content-Type**：`application/json`
- **请求体**：

```json
{
  "question": "用户的问题"
}
```

- **响应示例**：

```json
{
  "success": true,
  "answer": "DeepSeek 总结的回答内容",
  "references": [
    {
      "fileName": "example.pdf",
      "chunkIndex": 3,
      "similarity": 0.85,
      "text": "相关文档分块的文本内容"
    }
  ]
}
```

#### 1.3 向量搜索

- **URL**：`GET /api/documents/search`
- **描述**：根据查询文本的语义，在已上传的文档分块中检索最相似的结果
- **请求参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| query | String | 是 | - | 搜索查询文本 |
| topK | int | 否 | 5 | 返回结果数量 |

- **响应示例**：

```json
{
  "success": true,
  "query": "搜索关键词",
  "results": [
    {
      "rank": 1,
      "fileName": "example.pdf",
      "chunkIndex": 0,
      "text": "分块文本内容",
      "cosineDistance": 0.15,
      "similarity": 0.85,
      "createdAt": "2025-01-01T12:00:00"
    }
  ]
}
```

---

### 2. 文档管理 V2（LangChain4j）

> 控制器：`DocumentV2Controller` | 路径前缀：`/api/documents/v2`
> 
> 仅在 `database.available=true` 时注册

#### 2.1 上传文档（LangChain4j）

- **URL**：`POST /api/documents/v2/upload`
- **描述**：使用 LangChain4j 进行文件切割和向量化存储，存入 `document_chunk_langchain4j` 表
- **Content-Type**：`multipart/form-data`
- **请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | MultipartFile | 是 | 文档文件，支持 TXT、PDF、DOCX 格式 |

- **响应示例**：

```json
{
  "success": true,
  "message": "文件处理成功（LangChain4j）",
  "fileName": "example.pdf",
  "chunkCount": 15
}
```

#### 2.2 智能问答（LangChain4j）

- **URL**：`POST /api/documents/v2/chat`
- **描述**：通过 LangChain4j 向量检索找到最相关的文档分块，再由 DeepSeek 总结回答。支持查询改写优化检索效果。
- **Content-Type**：`application/json`
- **请求体**：

```json
{
  "question": "用户的问题"
}
```

- **响应示例**：

```json
{
  "success": true,
  "answer": "DeepSeek 总结的回答内容",
  "rewrittenQuestion": "改写后的检索优化问题",
  "references": [
    {
      "fileName": "example.pdf",
      "chunkIndex": 3,
      "similarity": 0.85,
      "text": "相关文档分块的文本内容"
    }
  ]
}
```

#### 2.3 向量搜索（LangChain4j）

- **URL**：`GET /api/documents/v2/search`
- **描述**：使用 LangChain4j 生成的向量，在 `document_chunk_langchain4j` 表中进行语义相似度搜索
- **请求参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| query | String | 是 | - | 搜索查询文本 |
| topK | int | 否 | 10 | 返回结果数量 |

- **响应示例**：同 [1.3 向量搜索](#13-向量搜索)

---

### 3. 聊天机器人

> 控制器：`ChatbotController` | 路径前缀：`/api/chatbot`

#### 3.1 发送聊天消息

- **URL**：`POST /api/chatbot/send`
- **描述**：发送用户消息，返回机器人回复。支持传入对话历史实现多轮对话。
- **Content-Type**：`application/json`
- **请求体**：

```json
{
  "message": "用户消息内容",
  "history": [
    { "role": "user", "content": "之前的消息" },
    { "role": "assistant", "content": "之前的回复" }
  ]
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| message | String | 是 | 用户消息内容 |
| history | Array | 否 | 对话历史，包含 role 和 content 字段 |

- **响应示例**：

```json
{
  "success": true,
  "reply": "机器人的回复内容"
}
```

---

### 4. 聊天机器人 2.0（LangChain4j + DeepSeek）

> 控制器：`ChatbotV2Controller` | 路径前缀：`/api/chatbot/v2`

#### 4.1 发送聊天消息（2.0）

- **URL**：`POST /api/chatbot/v2/send`
- **描述**：基于 LangChain4j + DeepSeek 模型，支持多轮对话及 SmartToolProvider 动态工具加载。根据用户消息意图按需加载工具（天气查询、经纬度查询、节假日查询、本地命令行执行），减少 token 消耗。当无法判断意图时回退到提供全部工具。
- **Content-Type**：`application/json`
- **请求体**：

```json
{
  "message": "用户消息内容",
  "history": [
    { "role": "user", "content": "之前的消息" },
    { "role": "assistant", "content": "之前的回复" }
  ]
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| message | String | 是 | 用户消息内容 |
| history | Array | 否 | 对话历史，包含 role 和 content 字段 |

- **响应示例**：

```json
{
  "success": true,
  "reply": "机器人的回复内容"
}
```

---

### 5. LangGraph4j 知识助手工作流

> 控制器：`LangGraphController` | 路径前缀：`/api/langgraph`

#### 5.1 提交问题到工作流

- **URL**：`POST /api/langgraph/process`
- **描述**：基于 LangGraph4j 状态图工作流，依次执行 **问题分析 → 知识生成 → 回答格式化** 三个节点，返回最终回答及各步骤中间结果
- **Content-Type**：`application/json`
- **请求体**：

```json
{
  "question": "用户的问题"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| question | String | 是 | 问题内容 |

- **响应示例**：

```json
{
  "success": true,
  "answer": "最终回答内容",
  "steps": {
    "analyze": "问题分析结果",
    "generate": "知识生成结果",
    "format": "格式化后的回答"
  }
}
```

---

### 6. LangGraph4j 报销流程

> 控制器：`ReimbursementController` | 路径前缀：`/api/reimbursement`

#### 6.1 提交报销申请

- **URL**：`POST /api/reimbursement/process`
- **描述**：基于 LangGraph4j 状态图工作流，依次执行 **提交申请 → 部门审批 → 财务审核 → 完成报销** 四个节点。审批节点可能被 LLM 判定为驳回，此时流程提前终止。
- **Content-Type**：`application/json`
- **请求体**：

```json
{
  "request": "报销申请内容"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| request | String | 是 | 报销申请内容 |

- **响应示例**：

```json
{
  "success": true,
  "result": "最终审批结果",
  "steps": {
    "submit": "提交申请结果",
    "department": "部门审批结果",
    "finance": "财务审核结果",
    "complete": "完成报销结果"
  }
}
```

---

### 7. LangGraph4j Multi Agent 圆桌讨论

> 控制器：`MultiAgentController` | 路径前缀：`/api/multiagent`

#### 7.1 提交话题到圆桌讨论工作流

- **URL**：`POST /api/multiagent/process`
- **描述**：基于 LangGraph4j 多专家协作模式：**话题分析 → 产品经理视角 → 架构师视角 → 开发者视角 → 测试工程师视角 → 综合共识**。展示多角色从不同角度分析同一话题的能力。
- **Content-Type**：`application/json`
- **请求体**：

```json
{
  "topic": "讨论话题"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| topic | String | 是 | 讨论话题 |

- **响应示例**：

```json
{
  "success": true,
  "result": "综合共识结果",
  "steps": {
    "analyze": "话题分析",
    "product_manager": "产品经理观点",
    "architect": "架构师观点",
    "developer": "开发者观点",
    "tester": "测试工程师观点",
    "consensus": "综合共识"
  }
}
```

---

### 8. LangGraph4j Plan-and-Execute 工作流

> 控制器：`PlanExecuteController` | 路径前缀：`/api/planexecute`

#### 8.1 提交请求到 Plan-and-Execute 工作流

- **URL**：`POST /api/planexecute/process`
- **描述**：基于 LangGraph4j 实现 Plan-and-Execute 模式：**Planner 制定计划 → Executor 执行 → Replanner 评估调整 → 综合输出**。支持动态规划与条件路由，展示智能任务分解能力。
- **Content-Type**：`application/json`
- **请求体**：

```json
{
  "request": "用户请求内容"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| request | String | 是 | 请求内容 |

- **响应示例**：

```json
{
  "success": true,
  "result": "最终输出结果",
  "steps": {
    "plan": "制定的计划",
    "execute": "执行结果",
    "replan": "评估调整结果"
  }
}
```

---

### 9. LangGraph4j Reflection 反思迭代

> 控制器：`ReflectionController` | 路径前缀：`/api/reflection`

#### 9.1 提交请求到 Reflection 反思迭代工作流

- **URL**：`POST /api/reflection/process`
- **描述**：基于 LangGraph4j 实现 Reflection 模式：**初稿生成 → 反思评审 → 修正改进循环 → 最终输出**。支持自我反思与迭代优化，展示 LLM 自我改进能力。
- **Content-Type**：`application/json`
- **请求体**：

```json
{
  "request": "用户请求内容"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| request | String | 是 | 请求内容 |

- **响应示例**：

```json
{
  "success": true,
  "result": "最终优化后的输出",
  "steps": {
    "generate": "初稿内容",
    "reflect": "反思评审意见",
    "revise": "修正改进内容"
  }
}
```

---

### 10. LangGraph4j Subagent 多 Agent 协作

> 控制器：`SubagentController` | 路径前缀：`/api/subagent`

#### 10.1 提交请求到多 Agent 协作工作流

- **URL**：`POST /api/subagent/process`
- **描述**：基于 LangGraph4j 条件边实现 Supervisor 路由模式：**查询改写 → Supervisor 分析请求类型 → 动态路由到编程助手/写作助手/通用问答专家子 Agent → 汇总格式化输出**。展示多 Agent 协作与条件边能力。
- **Content-Type**：`application/json`
- **请求体**：

```json
{
  "request": "用户请求内容"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| request | String | 是 | 请求内容 |

- **响应示例**：

```json
{
  "success": true,
  "result": "最终输出结果",
  "steps": {
    "rewrite": "查询改写结果",
    "route": "Supervisor 路由决策",
    "subagent": "子 Agent 处理结果",
    "format": "汇总格式化输出"
  }
}
```

---

### 11. LangGraph4j Agent Loop 智能体循环

> 控制器：`AgentLoopController` | 路径前缀：`/api/agentloop`

#### 11.1 提交请求到 Agent Loop 工作流

- **URL**：`POST /api/agentloop/process`
- **描述**：基于 LangGraph4j Agent Loop 模式（ReAct），智能体通过 SmartToolProvider 按需加载工具（天气查询、经纬度查询、节假日查询、本地命令行执行），循环推理与工具调用直到获得最终答案，返回执行步骤及最终回答。
- **Content-Type**：`application/json`
- **请求体**：

```json
{
  "question": "用户的问题"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| question | String | 是 | 问题内容 |

- **响应示例**：

```json
{
  "success": true,
  "answer": "最终回答",
  "steps": [
    {
      "type": "reasoning",
      "content": "Agent 推理过程"
    },
    {
      "type": "tool_call",
      "tool": "WeatherTool",
      "content": "工具调用结果"
    }
  ]
}
```

### 12. LangGraph4j Fan-Out 并发多 Agent 工作流

> 控制器：`FanOutController` | 路径前缀：`/api/fanout`

#### 12.1 提交任务到并发多 Agent 工作流

- **URL**：`POST /api/fanout/process`
- **描述**：基于 Fan-Out / Fan-In 模式：**任务分解 → 4 位专家 Agent（调研分析师、创意策划、技术顾问、执行专家）通过 CompletableFuture 并发执行各自子任务 → 归总汇总产出最终报告**。展示多 Agent 并发编排能力，区别于 MultiAgent 的串行圆桌讨论和 Subagent 的单专家路由模式。
- **Content-Type**：`application/json`
- **请求体**：

```json
{
  "task": "用户任务描述"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| task | String | 是 | 任务描述 |

- **响应示例**：

```json
{
  "success": true,
  "answer": "最终归总报告",
  "steps": [
    {
      "step": "task_planner",
      "label": "任务分解",
      "icon": "📋",
      "output": "任务分解结果",
      "status": "completed"
    },
    {
      "step": "agent_调研分析师",
      "label": "调研分析师",
      "icon": "🔍",
      "output": "调研分析结果",
      "status": "completed",
      "isConcurrent": true
    },
    {
      "step": "agent_创意策划",
      "label": "创意策划",
      "icon": "💡",
      "output": "创意策划结果",
      "status": "completed",
      "isConcurrent": true
    },
    {
      "step": "agent_技术顾问",
      "label": "技术顾问",
      "icon": "⚙️",
      "output": "技术分析结果",
      "status": "completed",
      "isConcurrent": true
    },
    {
      "step": "agent_执行专家",
      "label": "执行专家",
      "icon": "🎯",
      "output": "执行方案结果",
      "status": "completed",
      "isConcurrent": true
    },
    {
      "step": "aggregator",
      "label": "归总汇总",
      "icon": "📊",
      "output": "最终归总报告",
      "status": "completed"
    }
  ],
  "agentCount": 4,
  "elapsed": 12345
}
```

---

## 数据库设计

### document_chunk 表（Spring AI 向量存储）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL | 主键 ID，自增 |
| file_name | VARCHAR(255) | 上传的文件名 |
| chunk_index | INTEGER | 分块在文档中的序号（从 0 开始） |
| chunk_text | TEXT | 分块的文本内容 |
| embedding | vector(1024) | 嵌入向量（1024 维，通义千问 text-embedding-v3） |
| created_at | TIMESTAMP | 记录创建时间 |

索引：`ivfflat (embedding vector_cosine_ops)` — 余弦距离向量索引

### document_chunk_langchain4j 表（LangChain4j 向量存储）

表结构与 `document_chunk` 相同，专用于 LangChain4j 的分块和向量化存储。

---

## 配置说明

主要配置项（`application.yml`）：

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `server.port` | 服务端口 | 8081 |
| `spring.datasource.url` | PostgreSQL 连接地址 | - |
| `langchain4j.embedding.model` | Embedding 模型名称 | text-embedding-v3 |
| `langchain4j.embedding.max-retries` | LangChain4j Embedding 内部重试次数 | 2 |
| `deepseek.model` | DeepSeek 聊天模型 | deepseek-flash |
| `deepseek.max-retries` | LangChain4j ChatModel 内部重试次数 | 2 |
| `vector-search.similarity-threshold` | 向量搜索相似度阈值 | 0.4 |
| `spring.servlet.multipart.max-file-size` | 上传文件大小限制 | 50MB |
| `spring.ai.retry.max-attempts` | Spring AI 模型调用最大尝试次数 | 5 |
| `holiday.api.base-url` | 节假日 API 基础地址 | https://timor.tech |

所有敏感配置均支持通过环境变量覆盖（如 `DEEPSEEK_API_KEY`、`OPENAI_API_KEY`、`LANGCHAIN4J_EMBEDDING_API_KEY` 等）。

---

## 快速启动

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
