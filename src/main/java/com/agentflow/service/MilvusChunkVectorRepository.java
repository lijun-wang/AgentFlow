package com.agentflow.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.agentflow.model.DocumentChunk;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;

/**
 * 基于 Milvus 的文档分块向量仓储。
 * <p>
 * 当 {@code vector-store.type=milvus} 时注册。
 */
@Repository
@ConditionalOnProperty(name = "vector-store.type", havingValue = "milvus")
public class MilvusChunkVectorRepository implements ChunkVectorRepository {

    private static final Logger log = LoggerFactory.getLogger(MilvusChunkVectorRepository.class);
    private static final Gson GSON = new Gson();

    private final MilvusClientV2 milvusClient;

    @Value("${vector-search.similarity-threshold:0.7}")
    private double similarityThreshold;

    public MilvusChunkVectorRepository(MilvusClientV2 milvusClient) {
        this.milvusClient = milvusClient;
    }

    @Override
    public void saveChunks(String storeName, String fileName, List<String> texts, List<float[]> embeddings) {
        log.info("开始保存文档分块到 Milvus collection {}，文件: {}，分块数: {}", storeName, fileName, texts.size());

        long now = Instant.now().toEpochMilli();
        List<JsonObject> rows = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            JsonObject row = new JsonObject();
            row.addProperty("file_name", fileName);
            row.addProperty("chunk_index", i);
            row.addProperty("chunk_text", truncate(texts.get(i), 65535));
            row.add("embedding", GSON.toJsonTree(toFloatList(embeddings.get(i))));
            row.addProperty("created_at", now);
            rows.add(row);
        }

        milvusClient.insert(InsertReq.builder()
                .collectionName(storeName)
                .data(rows)
                .build());

        log.info("文档分块保存完成，collection: {}，文件: {}，共 {} 条", storeName, fileName, texts.size());
    }

    @Override
    public List<DocumentChunk> search(String storeName, float[] queryEmbedding, int topK) {
        log.info("开始 Milvus 向量搜索，collection: {}，topK: {}，阈值: {}", storeName, topK, similarityThreshold);

        SearchResp searchResp = milvusClient.search(SearchReq.builder()
                .collectionName(storeName)
                .data(Collections.singletonList(new FloatVec(queryEmbedding)))
                .annsField("embedding")
                .metricType(IndexParam.MetricType.COSINE)
                .limit(topK)
                .outputFields(Arrays.asList("file_name", "chunk_index", "chunk_text", "created_at"))
                .build());

        List<DocumentChunk> results = new ArrayList<>();
        List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();
        if (searchResults == null || searchResults.isEmpty()) {
            return results;
        }

        for (SearchResp.SearchResult hit : searchResults.get(0)) {
            float score = hit.getScore() != null ? hit.getScore() : 0f;
            // COSINE 度量下 score 为相似度（越大越相似）
            double similarity = score;
            if (similarity < similarityThreshold) {
                continue;
            }

            DocumentChunk chunk = new DocumentChunk();
            Object id = hit.getId();
            if (id instanceof Number number) {
                chunk.setId(number.longValue());
            }
            Map<String, Object> entity = hit.getEntity();
            if (entity != null) {
                Object fileName = entity.get("file_name");
                Object chunkIndex = entity.get("chunk_index");
                Object chunkText = entity.get("chunk_text");
                Object createdAt = entity.get("created_at");
                if (fileName != null) {
                    chunk.setFileName(String.valueOf(fileName));
                }
                if (chunkIndex instanceof Number number) {
                    chunk.setChunkIndex(number.intValue());
                }
                if (chunkText != null) {
                    chunk.setChunkText(String.valueOf(chunkText));
                }
                if (createdAt instanceof Number number) {
                    chunk.setCreatedAt(LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(number.longValue()), ZoneId.systemDefault()));
                }
            }
            chunk.setSimilarity(similarity);
            chunk.setCosineDistance(1.0 - similarity);
            results.add(chunk);
        }

        log.info("Milvus 向量搜索完成，collection: {}，过滤后: {} 条", storeName, results.size());
        return results;
    }

    private static List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
