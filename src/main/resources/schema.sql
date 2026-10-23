-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create document_chunk table
CREATE TABLE IF NOT EXISTS document_chunk (
    id BIGSERIAL PRIMARY KEY,                      -- 主键ID，自增
    file_name VARCHAR(255) NOT NULL,                -- 上传的文件名
    chunk_index INTEGER NOT NULL,                   -- 分块在文档中的序号（从0开始）
    chunk_text TEXT NOT NULL,                       -- 分块的文本内容
    embedding vector(1024),                         -- 嵌入向量（1024维，由通义千问模型生成）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP  -- 记录创建时间
);

-- 字段描述
COMMENT ON TABLE document_chunk IS '文档分块表，存储上传文档的分块文本及其嵌入向量';
COMMENT ON COLUMN document_chunk.id IS '主键ID，自增';
COMMENT ON COLUMN document_chunk.file_name IS '上传的文件名';
COMMENT ON COLUMN document_chunk.chunk_index IS '分块在文档中的序号（从0开始）';
COMMENT ON COLUMN document_chunk.chunk_text IS '分块的文本内容';
COMMENT ON COLUMN document_chunk.embedding IS '嵌入向量（1024维，由通义千问模型生成）';
COMMENT ON COLUMN document_chunk.created_at IS '记录创建时间';

-- Create index for vector similarity search (cosine distance)
CREATE INDEX IF NOT EXISTS idx_document_chunk_embedding
    ON document_chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- Create document_chunk_langchain4j table (LangChain4j 专用，表结构与 document_chunk 相同)
CREATE TABLE IF NOT EXISTS document_chunk_langchain4j (
    id BIGSERIAL PRIMARY KEY,                      -- 主键ID，自增
    file_name VARCHAR(255) NOT NULL,                -- 上传的文件名
    chunk_index INTEGER NOT NULL,                   -- 分块在文档中的序号（从0开始）
    chunk_text TEXT NOT NULL,                       -- 分块的文本内容
    embedding vector(1024),                         -- 嵌入向量（1024维，由通义千问模型生成，通过 LangChain4j 调用）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP  -- 记录创建时间
);

-- 字段描述
COMMENT ON TABLE document_chunk_langchain4j IS '文档分块表（LangChain4j版），存储上传文档经 LangChain4j 分块和向量化的文本及嵌入向量';
COMMENT ON COLUMN document_chunk_langchain4j.id IS '主键ID，自增';
COMMENT ON COLUMN document_chunk_langchain4j.file_name IS '上传的文件名';
COMMENT ON COLUMN document_chunk_langchain4j.chunk_index IS '分块在文档中的序号（从0开始）';
COMMENT ON COLUMN document_chunk_langchain4j.chunk_text IS '分块的文本内容';
COMMENT ON COLUMN document_chunk_langchain4j.embedding IS '嵌入向量（1024维，由通义千问模型生成，通过 LangChain4j 调用）';
COMMENT ON COLUMN document_chunk_langchain4j.created_at IS '记录创建时间';

-- Create index for vector similarity search (cosine distance) - LangChain4j 表
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
