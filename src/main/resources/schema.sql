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
