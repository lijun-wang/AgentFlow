package com.agentflow.service;

import com.agentflow.model.KgEntity;
import com.agentflow.model.KgRelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * 知识图谱存储：分块、实体、关系的 JDBC 读写
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "database.available", havingValue = "true")
public class KnowledgeGraphStore {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphStore.class);

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeGraphStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存 3.0 文档分块并返回生成的主键 ID 列表
     */
    public List<Long> saveChunks(String fileName, List<String> texts, List<float[]> embeddings) {
        log.info("开始保存知识图谱分块，文件: {}，分块数: {}", fileName, texts.size());

        // 注意：必须使用 CAST(? AS vector) 显式转换类型，避免 JDBC 对 :: 操作符的解析问题
        String sql = "INSERT INTO document_chunk_v3 (file_name, chunk_index, chunk_text, embedding) VALUES (?, ?, ?, CAST(? AS vector)) RETURNING id";
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            String vectorStr = toVectorString(embeddings.get(i));
            Long id = jdbcTemplate.queryForObject(sql, Long.class, fileName, i, texts.get(i), vectorStr);
            ids.add(id);
        }

        log.info("知识图谱分块保存完成，文件: {}，共 {} 条", fileName, ids.size());
        return ids;
    }

    /**
     * 按规范化名 + 类型 upsert 实体，返回实体 ID
     */
    public long upsertEntity(String name, String canonicalName, String type) {
        String sql = """
                INSERT INTO kg_entity (name, canonical_name, type)
                VALUES (?, ?, ?)
                ON CONFLICT (canonical_name, type)
                DO UPDATE SET name = EXCLUDED.name
                RETURNING id
                """;
        Long id = jdbcTemplate.queryForObject(sql, Long.class, name, canonicalName, type);
        if (id == null) {
            throw new IllegalStateException("upsert 实体未返回 ID: " + canonicalName);
        }
        return id;
    }

    /**
     * 写入关系；同一分块上的相同边忽略
     */
    public void saveRelation(long fromEntityId, String predicate, long toEntityId,
                             double confidence, Long chunkId, String fileName) {
        String sql = """
                INSERT INTO kg_relation (from_entity_id, predicate, to_entity_id, confidence, chunk_id, file_name)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (from_entity_id, predicate, to_entity_id, chunk_id)
                DO NOTHING
                """;
        jdbcTemplate.update(sql, fromEntityId, predicate, toEntityId, confidence, chunkId, fileName);
    }

    /**
     * 按文件名加载该文件触及的实体与全部关系
     */
    public GraphSnapshot loadGraph(String fileName) {
        String entitySql = """
                SELECT DISTINCT e.id, e.name, e.canonical_name, e.type
                FROM kg_entity e
                INNER JOIN kg_relation r ON (e.id = r.from_entity_id OR e.id = r.to_entity_id)
                WHERE r.file_name = ?
                ORDER BY e.type, e.name
                """;
        List<KgEntity> entities = jdbcTemplate.query(entitySql, new KgEntityRowMapper(), fileName);

        String relationSql = """
                SELECT r.id, r.from_entity_id, r.predicate, r.to_entity_id, r.confidence, r.chunk_id, r.file_name,
                       fe.name AS from_name, fe.type AS from_type,
                       te.name AS to_name, te.type AS to_type
                FROM kg_relation r
                JOIN kg_entity fe ON fe.id = r.from_entity_id
                JOIN kg_entity te ON te.id = r.to_entity_id
                WHERE r.file_name = ?
                ORDER BY r.id
                """;
        List<KgRelation> relations = jdbcTemplate.query(relationSql, new KgRelationRowMapper(), fileName);

        log.info("加载知识图谱，文件: {}，实体 {} 个，关系 {} 条", fileName, entities.size(), relations.size());
        return new GraphSnapshot(entities, relations);
    }

    private String toVectorString(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float v : vector) {
            joiner.add(String.valueOf(v));
        }
        return joiner.toString();
    }

    public record GraphSnapshot(List<KgEntity> entities, List<KgRelation> relations) {
    }

    private static class KgEntityRowMapper implements RowMapper<KgEntity> {
        @Override
        public KgEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            KgEntity entity = new KgEntity();
            entity.setId(rs.getLong("id"));
            entity.setName(rs.getString("name"));
            entity.setCanonicalName(rs.getString("canonical_name"));
            entity.setType(rs.getString("type"));
            return entity;
        }
    }

    private static class KgRelationRowMapper implements RowMapper<KgRelation> {
        @Override
        public KgRelation mapRow(ResultSet rs, int rowNum) throws SQLException {
            KgRelation relation = new KgRelation();
            relation.setId(rs.getLong("id"));
            relation.setFromEntityId(rs.getLong("from_entity_id"));
            relation.setFromName(rs.getString("from_name"));
            relation.setFromType(rs.getString("from_type"));
            relation.setPredicate(rs.getString("predicate"));
            relation.setToEntityId(rs.getLong("to_entity_id"));
            relation.setToName(rs.getString("to_name"));
            relation.setToType(rs.getString("to_type"));
            relation.setConfidence(rs.getDouble("confidence"));
            long chunkId = rs.getLong("chunk_id");
            relation.setChunkId(rs.wasNull() ? null : chunkId);
            relation.setFileName(rs.getString("file_name"));
            return relation;
        }
    }
}
