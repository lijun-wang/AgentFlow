package com.agentflow.service;

import com.agentflow.model.DocumentChunk;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * LangChain4j 向量存储服务：分块与向量化后，委托 {@link ChunkVectorRepository} 持久化/检索。
 * <p>
 * 后端由 {@code vector-store.type} 决定（postgres / milvus）。
 */
@Service
@ConditionalOnBean(ChunkVectorRepository.class)
public class Langchain4jVectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(Langchain4jVectorStoreService.class);

    private final ChunkVectorRepository chunkVectorRepository;
    private final EmbeddingModel embeddingModel;
    private final DocumentSplitter documentSplitter;

    @Value("${vector-store.milvus.collection-langchain4j:document_chunk_langchain4j}")
    private String milvusCollection;

    @Value("${vector-store.type:postgres}")
    private String vectorStoreType;

    public Langchain4jVectorStoreService(ChunkVectorRepository chunkVectorRepository,
                                          @Qualifier("langchain4jEmbeddingModel") EmbeddingModel embeddingModel,
                                          @Qualifier("langchain4jTextSplitter") DocumentSplitter documentSplitter) {
        this.chunkVectorRepository = chunkVectorRepository;
        this.embeddingModel = embeddingModel;
        this.documentSplitter = documentSplitter;
    }

    /**
     * 上传文件并处理：提取文本 → LangChain4j 分块 → 生成向量 → 存入后端
     */
    public int processAndStore(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        log.info("开始 LangChain4j 处理文件: {}，向量后端: {}", fileName, vectorStoreType);

        String fullText = extractFullText(file);
        if (fullText == null || fullText.isBlank()) {
            return 0;
        }

        Document document = Document.from(fullText);
        List<TextSegment> segments = documentSplitter.split(document);
        log.info("LangChain4j 分块完成，文件: {}，共 {} 个分块", fileName, segments.size());

        if (segments.isEmpty()) {
            return 0;
        }

        List<String> chunks = new ArrayList<>();
        for (TextSegment seg : segments) {
            chunks.add(seg.text());
        }

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

        List<float[]> vectors = new ArrayList<>(embeddings.size());
        for (Embedding embedding : embeddings) {
            vectors.add(embedding.vector());
        }
        chunkVectorRepository.saveChunks(resolveStoreName(), fileName, chunks, vectors);

        return chunks.size();
    }

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

    private String extractPdfText(MultipartFile file) throws IOException {
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(file.getBytes())) {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            return stripper.getText(document);
        }
    }

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
     * 向量相似度搜索
     */
    public List<DocumentChunk> search(float[] queryEmbedding, int topK) {
        String storeName = resolveStoreName();
        log.info("LangChain4j 向量搜索，后端: {}，存储: {}，topK: {}", vectorStoreType, storeName, topK);
        return chunkVectorRepository.search(storeName, queryEmbedding, topK);
    }

    /**
     * 使用 LangChain4j EmbeddingModel 将查询文本转为向量
     */
    public float[] embedQuery(String query) {
        Response<Embedding> response = embeddingModel.embed(query);
        return response.content().vector();
    }

    private String resolveStoreName() {
        if ("milvus".equalsIgnoreCase(vectorStoreType)) {
            return milvusCollection;
        }
        return "document_chunk_langchain4j";
    }
}
