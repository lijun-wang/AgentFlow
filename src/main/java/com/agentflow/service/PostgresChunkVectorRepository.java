package com.agentflow.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.agentflow.model.DocumentChunk;

/**
 * 基于 PostgreSQL + pgvector 的文档分块向量仓储。
 * <p>
 * 当 {@code vector-store.type=postgres}（默认）且 {@code database.available=true} 时注册。
 */
@Repository
@ConditionalOnExpression("'${vector-store.type:postgres}' == 'postgres' and '${database.available:false}' == 'true'")
public class PostgresChunkVectorRepository implements ChunkVectorRepository {

    private static final Logger log = LoggerFactory.getLogger(PostgresChunkVectorRepository.class);

    private static final Set<String> ALLOWED_TABLES = Set.of(
            "document_chunk",
            "document_chunk_langchain4j",
            "document_chunk_v3"
    );

    private final JdbcTemplate jdbcTemplate;

    @Value("${vector-search.similarity-threshold:0.7}")
    private double similarityThreshold;

    public PostgresChunkVectorRepository(@Qualifier("postgresJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveChunks(String storeName, String fileName, List<String> texts, List<float[]> embeddings) {
        String table = requireAllowedTable(storeName);
        log.info("开始保存文档分块到 PostgreSQL 表 {}，文件: {}，分块数: {}", table, fileName, texts.size());

        String sql = "INSERT INTO " + table +
                " (file_name, chunk_index, chunk_text, embedding) VALUES (?, ?, ?, CAST(? AS vector))";

        for (int i = 0; i < texts.size(); i++) {
            jdbcTemplate.update(sql, fileName, i, texts.get(i), toVectorString(embeddings.get(i)));
        }

        log.info("文档分块保存完成，表: {}，文件: {}，共 {} 条", table, fileName, texts.size());
    }

    @Override
    public List<DocumentChunk> search(String storeName, float[] queryEmbedding, int topK) {
        String table = requireAllowedTable(storeName);
        log.info("开始 PostgreSQL 向量搜索，表: {}，topK: {}，阈值: {}", table, topK, similarityThreshold);

        String vectorStr = toVectorString(queryEmbedding);
        String sql = "SELECT id, file_name, chunk_index, chunk_text, " +
                "embedding <=> CAST(? AS vector) AS cosine_distance, " +
                "1 - (embedding <=> CAST(? AS vector)) AS similarity, " +
                "created_at " +
                "FROM " + table + " " +
                "ORDER BY embedding <=> CAST(? AS vector) " +
                "LIMIT ?";

        List<DocumentChunk> allResults = jdbcTemplate.query(sql,
                new Object[]{vectorStr, vectorStr, vectorStr, topK},
                new DocumentChunkRowMapper());

        List<DocumentChunk> results = allResults.stream()
                .filter(chunk -> chunk.getSimilarity() >= similarityThreshold)
                .toList();

        log.info("PostgreSQL 向量搜索完成，表: {}，原始: {}，过滤后: {}", table, allResults.size(), results.size());
        return results;
    }

    private String requireAllowedTable(String storeName) {
        if (storeName == null || !ALLOWED_TABLES.contains(storeName)) {
            throw new IllegalArgumentException("不支持的 Postgres 表名: " + storeName);
        }
        return storeName;
    }

    private String toVectorString(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float v : vector) {
            joiner.add(String.valueOf(v));
        }
        return joiner.toString();
    }

    private static class DocumentChunkRowMapper implements RowMapper<DocumentChunk> {
        @Override
        public DocumentChunk mapRow(ResultSet rs, int rowNum) throws SQLException {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setId(rs.getLong("id"));
            chunk.setFileName(rs.getString("file_name"));
            chunk.setChunkIndex(rs.getInt("chunk_index"));
            chunk.setChunkText(rs.getString("chunk_text"));
            chunk.setCosineDistance(rs.getDouble("cosine_distance"));
            chunk.setSimilarity(rs.getDouble("similarity"));
            chunk.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            return chunk;
        }
    }
}
