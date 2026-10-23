package com.agentflow.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import com.agentflow.model.DocumentChunk;

/**
 * 向量存储服务（Spring AI 文档链路）：委托 {@link ChunkVectorRepository}。
 * <p>
 * 后端由 {@code vector-store.type} 决定（postgres / milvus）。
 */
@Service
@ConditionalOnBean(ChunkVectorRepository.class)
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    private final ChunkVectorRepository chunkVectorRepository;

    @Value("${vector-store.milvus.collection-document-chunk:document_chunk}")
    private String milvusCollection;

    @Value("${vector-store.type:postgres}")
    private String vectorStoreType;

    public VectorStoreService(ChunkVectorRepository chunkVectorRepository) {
        this.chunkVectorRepository = chunkVectorRepository;
    }

    /**
     * 批量保存文档分块和向量
     */
    public void saveChunks(String fileName, List<String> texts, List<float[]> embeddings) {
        String storeName = resolveStoreName();
        log.info("保存文档分块，后端: {}，存储: {}，文件: {}，分块数: {}",
                vectorStoreType, storeName, fileName, texts.size());
        chunkVectorRepository.saveChunks(storeName, fileName, texts, embeddings);
    }

    /**
     * 向量相似度搜索
     */
    public List<DocumentChunk> search(float[] queryEmbedding, int topK) {
        String storeName = resolveStoreName();
        log.info("向量搜索，后端: {}，存储: {}，topK: {}", vectorStoreType, storeName, topK);
        return chunkVectorRepository.search(storeName, queryEmbedding, topK);
    }

    private String resolveStoreName() {
        // Postgres 使用固定表名；Milvus 可用配置覆盖 collection 名
        if ("milvus".equalsIgnoreCase(vectorStoreType)) {
            return milvusCollection;
        }
        return "document_chunk";
    }
}
