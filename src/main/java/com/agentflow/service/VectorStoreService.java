package com.agentflow.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.StringJoiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import com.agentflow.model.DocumentChunk;

/**
 * 向量存储服务：使�?JdbcTemplate 存储和检�?pgvector 向量
 * <p>
 * 仅在 PostgreSQL 数据库可用时注册（通过 database.available 属性控制）
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "database.available", havingValue = "true")
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    private final JdbcTemplate jdbcTemplate;

    /** 相似度阈值，仅返回相似度 >= 该值的结果 */
    @Value("${vector-search.similarity-threshold:0.7}")
    private double similarityThreshold;

    public VectorStoreService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 批量保存文档分块和向量到数据�?
     */
    public void saveChunks(String fileName, List<String> texts, List<float[]> embeddings) {
        log.info("开始保存文档分块，文件: {}，分块数: {}", fileName, texts.size());

        String sql = "INSERT INTO document_chunk (file_name, chunk_index, chunk_text, embedding) VALUES (?, ?, ?, ?::vector)";

        for (int i = 0; i < texts.size(); i++) {
            String vectorStr = toVectorString(embeddings.get(i));
            jdbcTemplate.update(sql, fileName, i, texts.get(i), vectorStr);
        }

        log.info("文档分块保存完成，文�? {}，共 {} 条记�?, fileName, texts.size());
    }

    /**
     * 向量相似度搜索：返回最相似�?topK 个结果，仅返回相似度 >= 阈值的结果
     *
     * @param queryEmbedding 查询向量
     * @param topK           返回结果数量
     * @return 相似的文档分块列表（按相似度排序，过滤低于阈值的结果�?
     */
    public List<DocumentChunk> search(float[] queryEmbedding, int topK) {
        log.info("开始向量搜索，topK: {}，相似度阈�? {}", topK, similarityThreshold);

        String vectorStr = toVectorString(queryEmbedding);
        // 使用余弦距离操作�?<=> 进行相似度搜�?
        // cosine_distance: 余弦距离，范围[0,2]，值越小表示向量越相似
        // similarity: 相似�?= 1 - 余弦距离，范围[-1,1]，值越大表示越相似
        // 注意：必须使�?CAST(? AS vector) 显式转换类型，否�?JDBC 会将 String 参数绑定�?
        // character varying，�?pgvector �?<=> 操作符不支持隐式转换，会�?
        // "operator does not exist: vector <=> character varying" 错误
        // 阈值过滤在 Java 层完�?
        String sql = "SELECT id, file_name, chunk_index, chunk_text, " +
                "embedding <=> CAST(? AS vector) AS cosine_distance, " +
                "1 - (embedding <=> CAST(? AS vector)) AS similarity, " +
                "created_at " +
                "FROM document_chunk " +
                "ORDER BY embedding <=> CAST(? AS vector) " +
                "LIMIT ?";

        log.info("执行 SQL: {}，参数数�? 4", sql);

        List<DocumentChunk> allResults = jdbcTemplate.query(sql,
                new Object[]{vectorStr, vectorStr, vectorStr, topK},
                new DocumentChunkRowMapper());

        // �?Java 层过滤：仅保留相似度 >= 阈值的结果
        List<DocumentChunk> results = allResults.stream()
                .filter(chunk -> chunk.getSimilarity() >= similarityThreshold)
                .toList();

        log.info("向量搜索完成，原始结�? {} 条，过滤�? {} 条（相似度阈�? {}�?,
                allResults.size(), results.size(), similarityThreshold);
        return results;
    }

    /**
     * �?float[] 转为 pgvector 字符串格式，例如 "[0.1,0.2,0.3]"
     */
    private String toVectorString(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float v : vector) {
            joiner.add(String.valueOf(v));
        }
        return joiner.toString();
    }

    /**
     * 查询结果映射�?
     */
    private static class DocumentChunkRowMapper implements RowMapper<DocumentChunk> {
        @Override
        public DocumentChunk mapRow(ResultSet rs, int rowNum) throws SQLException {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setId(rs.getLong("id"));
            chunk.setFileName(rs.getString("file_name"));
            chunk.setChunkIndex(rs.getInt("chunk_index"));
            chunk.setChunkText(rs.getString("chunk_text"));
            // 余弦距离：值越小越相似
            chunk.setCosineDistance(rs.getDouble("cosine_distance"));
            // 相似度：值越大越相似
            chunk.setSimilarity(rs.getDouble("similarity"));
            chunk.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            return chunk;
        }
    }
}
