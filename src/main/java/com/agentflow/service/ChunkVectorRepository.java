package com.agentflow.service;

import java.util.List;

import com.agentflow.model.DocumentChunk;

/**
 * 文档分块向量仓储：抽象 Postgres(pgvector) 与 Milvus 两种后端。
 * <p>
 * 由 {@code vector-store.type} 决定具体实现（postgres / milvus）。
 */
public interface ChunkVectorRepository {

    /**
     * 批量保存文档分块与向量。
     *
     * @param storeName 存储名：Postgres 为表名，Milvus 为 collection 名
     * @param fileName  文件名
     * @param texts     分块文本
     * @param embeddings 对应向量
     */
    void saveChunks(String storeName, String fileName, List<String> texts, List<float[]> embeddings);

    /**
     * 向量相似度搜索，返回最相似的 topK 条（实现方负责相似度阈值过滤）。
     *
     * @param storeName      存储名：Postgres 为表名，Milvus 为 collection 名
     * @param queryEmbedding 查询向量
     * @param topK           返回数量上限
     * @return 相似分块列表（按相似度降序）
     */
    List<DocumentChunk> search(String storeName, float[] queryEmbedding, int topK);
}
