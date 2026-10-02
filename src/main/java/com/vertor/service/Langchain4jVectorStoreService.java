package com.vertor.service;

import com.vertor.model.DocumentChunk;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * LangChain4j 向量存储服务：使用 LangChain4j 进行文件切割、向量化，并存储到 document_chunk_langchain4j 表
 * <p>
 * 仅在 PostgreSQL 数据库可用时注册（通过 database.available 属性控制）
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "database.available", havingValue = "true")
public class Langchain4jVectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(Langchain4jVectorStoreService.class);

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final DocumentSplitter documentSplitter;

    /** 相似度阈值，仅返回相似度 >= 该值的结果 */
    @Value("${vector-search.similarity-threshold:0.7}")
    private double similarityThreshold;

    public Langchain4jVectorStoreService(JdbcTemplate jdbcTemplate,
                                          @Qualifier("langchain4jEmbeddingModel") EmbeddingModel embeddingModel,
                                          @Qualifier("langchain4jTextSplitter") DocumentSplitter documentSplitter) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
        this.documentSplitter = documentSplitter;
    }

    /**
     * 上传文件并处理：提取文本 → LangChain4j 分块 → 生成向量 → 存入 document_chunk_langchain4j 表
     *
     * @param file 上传的文件
     * @return 分块数量
     */
    public int processAndStore(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        log.info("开始 LangChain4j 处理文件: {}", fileName);

        // 1. 提取完整文本
        String fullText = extractFullText(file);
        if (fullText == null || fullText.isBlank()) {
            return 0;
        }

        // 2. 使用 LangChain4j DocumentSplitter 进行递归分块
        Document document = Document.from(fullText);
        List<TextSegment> segments = documentSplitter.split(document);
        log.info("LangChain4j 分块完成，文件: {}，共 {} 个分块", fileName, segments.size());

        if (segments.isEmpty()) {
            return 0;
        }

        // 3. 提取分块文本
        List<String> chunks = new ArrayList<>();
        for (TextSegment seg : segments) {
            chunks.add(seg.text());
        }

        // 4. 使用 LangChain4j EmbeddingModel 分批生成向量（API 限制每批最多 10 条）
        int batchSize = 10;
        List<Embedding> embeddings = new ArrayList<>();
        for (int i = 0; i < segments.size(); i += batchSize) {
            int end = Math.min(i + batchSize, segments.size());
            List<TextSegment> batch = segments.subList(i, end);
            Response<List<Embedding>> response = embeddingModel.embedAll(batch);
            embeddings.addAll(response.content());
            log.info("批次向量生成完成: {}/{}", end, segments.size());
        }
        log.info("LangChain4j 向量生成完成，共 {} 个向量", embeddings.size());

        // 5. 存储到 document_chunk_langchain4j 表
        saveChunks(fileName, chunks, embeddings);

        return chunks.size();
    }

    /**
     * 从文件中提取完整文本（不经过分块处理，分块交给 LangChain4j 处理）
     */
    private String extractFullText(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

        return switch (extension) {
            case "txt" -> new String(file.getBytes(), StandardCharsets.UTF_8);
            case "pdf" -> extractPdfText(file);
            case "docx", "doc" -> extractDocxText(file);
            default -> throw new IllegalArgumentException("不支持的文件格式: " + extension);
        };
    }

    /**
     * 提取 PDF 文件全文
     */
    private String extractPdfText(MultipartFile file) throws IOException {
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(file.getBytes())) {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * 提取 DOCX 文件全文
     */
    private String extractDocxText(MultipartFile file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = file.getInputStream();
             org.apache.poi.xwpf.usermodel.XWPFDocument document = new org.apache.poi.xwpf.usermodel.XWPFDocument(is)) {
            for (org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText().trim();
                if (!text.isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(text);
                }
            }
        }
        return sb.toString();
    }

    /**
     * 批量保存文档分块和向量到 document_chunk_langchain4j 表
     */
    private void saveChunks(String fileName, List<String> texts, List<Embedding> embeddings) {
        log.info("开始保存文档分块到 LangChain4j 表，文件: {}，分块数: {}", fileName, texts.size());

        String sql = "INSERT INTO document_chunk_langchain4j (file_name, chunk_index, chunk_text, embedding) VALUES (?, ?, ?, ?)";

        for (int i = 0; i < texts.size(); i++) {
            String vectorStr = toVectorString(embeddings.get(i));
            jdbcTemplate.update(sql, fileName, i, texts.get(i), vectorStr);
        }

        log.info("文档分块保存完成，文件: {}，共 {} 条记录", fileName, texts.size());
    }

    /**
     * 向量相似度搜索：从 document_chunk_langchain4j 表检索最相似的 topK 个结果
     *
     * @param queryEmbedding 查询向量
     * @param topK           返回结果数量
     * @return 相似的文档分块列表（按相似度排序，过滤低于阈值的结果）
     */
    public List<DocumentChunk> search(float[] queryEmbedding, int topK) {
        log.info("开始 LangChain4j 向量搜索，topK: {}，相似度阈值: {}", topK, similarityThreshold);

        String vectorStr = toVectorString(queryEmbedding);
        // 使用余弦距离操作符 <=> 进行相似度搜索
        // cosine_distance: 余弦距离，范围[0,2]，值越小表示向量越相似
        // similarity: 相似度 = 1 - 余弦距离，范围[-1,1]，值越大表示越相似
        // 注意：必须使用 CAST(? AS vector) 显式转换类型，否则 JDBC 会将 String 参数绑定为
        // character varying，而 pgvector 的 <=> 操作符不支持隐式转换，会报
        // "operator does not exist: vector <=> character varying" 错误
        // 阈值过滤在 Java 层完成
        String sql = "SELECT id, file_name, chunk_index, chunk_text, " +
                "embedding <=> CAST(? AS vector) AS cosine_distance, " +
                "1 - (embedding <=> CAST(? AS vector)) AS similarity, " +
                "created_at " +
                "FROM document_chunk_langchain4j " +
                "ORDER BY embedding <=> CAST(? AS vector) " +
                "LIMIT ?";

        log.info("执行 SQL: {}，参数数量: 4", sql);

        List<DocumentChunk> allResults = jdbcTemplate.query(sql,
                new Object[]{vectorStr, vectorStr, vectorStr, topK},
                new DocumentChunkRowMapper());

        // 在 Java 层过滤：仅保留相似度 >= 阈值的结果
        List<DocumentChunk> results = allResults.stream()
                .filter(chunk -> chunk.getSimilarity() >= similarityThreshold)
                .toList();

        log.info("LangChain4j 向量搜索完成，原始结果: {} 条，过滤后: {} 条（相似度阈值: {}）",
                allResults.size(), results.size(), similarityThreshold);
        return results;
    }

    /**
     * 使用 LangChain4j EmbeddingModel 将查询文本转为向量
     *
     * @param query 查询文本
     * @return 查询向量
     */
    public float[] embedQuery(String query) {
        Response<Embedding> response = embeddingModel.embed(query);
        return response.content().vector();
    }

    /**
     * 将 LangChain4j Embedding 转为 pgvector 字符串格式
     */
    private String toVectorString(Embedding embedding) {
        return toVectorString(embedding.vector());
    }

    /**
     * 将 float[] 转为 pgvector 字符串格式，例如 "[0.1,0.2,0.3]"
     */
    private String toVectorString(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float v : vector) {
            joiner.add(String.valueOf(v));
        }
        return joiner.toString();
    }

    /**
     * 查询结果映射器
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
