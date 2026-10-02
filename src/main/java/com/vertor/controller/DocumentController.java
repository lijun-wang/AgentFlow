package com.vertor.controller;

import com.vertor.model.DocumentChunk;
import com.vertor.service.ChatService;
import com.vertor.service.EmbeddingService;
import com.vertor.service.TextExtractionService;
import com.vertor.service.VectorStoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.IntStream;

/**
 * 文档控制器：处理文件上传和向量搜索
 * <p>
 * 仅在 PostgreSQL 数据库可用时注册（通过 database.available 属性控制）
 */
@Tag(name = "文档管理", description = "文件上传与向量相似度搜索接口")
@RestController
@RequestMapping("/api/documents")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "database.available", havingValue = "true")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final TextExtractionService textExtractionService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final ChatService chatService;

    public DocumentController(TextExtractionService textExtractionService,
                              EmbeddingService embeddingService,
                              VectorStoreService vectorStoreService,
                              ChatService chatService) {
        this.textExtractionService = textExtractionService;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.chatService = chatService;
    }

    /**
     * 上传文件：提取文本 → 生成向量 → 存入数据库
     */
    @Operation(
            summary = "上传文档",
            description = "上传文档文件，自动提取文本、分块、生成嵌入向量并存入数据库。支持 TXT、PDF、DOCX 格式。"
    )
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @Parameter(description = "要上传的文档文件", required = true)
            @RequestParam("file") MultipartFile file) {
        try {
            String fileName = file.getOriginalFilename();
            log.info("开始处理文件上传: {}", fileName);

            // 1. 提取文本并分块
            List<String> chunks = textExtractionService.extractAndChunk(file);
            if (chunks.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "文件内容为空或无法提取文本"
                ));
            }

            // 2. 生成嵌入向量
            List<float[]> embeddings = embeddingService.embedTexts(chunks);

            // 3. 存储到数据库
            vectorStoreService.saveChunks(fileName, chunks, embeddings);

            log.info("文件处理完成: {}，共 {} 个分块", fileName, chunks.size());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "文件处理成功",
                    "fileName", fileName,
                    "chunkCount", chunks.size()
            ));

        } catch (Exception e) {
            log.error("文件处理失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "文件处理失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 智能问答：根据问题检索相关文档内容并返回
     */
    @Operation(
            summary = "智能问答",
            description = "根据用户提问，通过向量检索找到最相关的文档分块，将内容返回展示。"
    )
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @RequestBody Map<String, String> request) {
        try {
            String question = request.get("question");
            if (question == null || question.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "问题内容不能为空"
                ));
            }

            log.info("开始智能问答，问题: {}", question);

            // 1. 将问题转为向量
            float[] queryEmbedding = embeddingService.embedText(question);

            // 2. 检索最相关的文档分块
            int topK = 10;
            List<DocumentChunk> results = vectorStoreService.search(queryEmbedding, topK);

            if (results.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "answer", "未找到与您问题相关的内容，请尝试上传更多文档。",
                        "references", List.of()
                ));
            }

            // 3. 将检索到的内容拼接为上下文
            StringBuilder contextBuilder = new StringBuilder();
            for (int i = 0; i < results.size(); i++) {
                DocumentChunk chunk = results.get(i);
                if (i > 0) {
                    contextBuilder.append("\n\n---\n\n");
                }
                contextBuilder.append(chunk.getChunkText());
            }

            // 4. 调用 DeepSeek 对检索内容进行总结
            String answer = chatService.summarize(question, contextBuilder.toString());

            // 5. 构建引用来源
            List<Map<String, Object>> references = IntStream.range(0, results.size())
                    .mapToObj(i -> {
                        DocumentChunk chunk = results.get(i);
                        Map<String, Object> ref = new LinkedHashMap<>();
                        ref.put("fileName", chunk.getFileName());
                        ref.put("chunkIndex", chunk.getChunkIndex());
                        ref.put("similarity", chunk.getSimilarity());
                        ref.put("text", chunk.getChunkText());
                        return ref;
                    })
                    .toList();

            log.info("智能问答完成，检索到 {} 条相关内容，DeepSeek 总结完成", results.size());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "answer", answer,
                    "references", references
            ));

        } catch (Exception e) {
            log.error("智能问答失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "问答失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 向量相似度搜索
     */
    @Operation(
            summary = "向量搜索",
            description = "根据查询文本的语义，在已上传的文档分块中检索最相似的结果，返回余弦距离和相似度。"
    )
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @Parameter(description = "搜索查询文本", required = true)
            @RequestParam("query") String query,
            @Parameter(description = "返回结果数量，默认5")
            @RequestParam(value = "topK", defaultValue = "5") int topK) {
        try {
            log.info("开始搜索，查询: {}，topK: {}", query, topK);

            // 1. 将查询文本转为向量
            float[] queryEmbedding = embeddingService.embedText(query);

            // 2. 执行向量搜索
            List<DocumentChunk> results = vectorStoreService.search(queryEmbedding, topK);

            // 3. 构建响应
            List<Map<String, Object>> responseResults = IntStream.range(0, results.size())
                    .mapToObj(i -> {
                        DocumentChunk chunk = results.get(i);
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("rank", i + 1);                          // 排名序号
                        item.put("fileName", chunk.getFileName());           // 文件名
                        item.put("chunkIndex", chunk.getChunkIndex());       // 分块序号
                        item.put("text", chunk.getChunkText());              // 分块文本内容
                        item.put("cosineDistance", chunk.getCosineDistance()); // 余弦距离，范围[0,2]，值越小越相似
                        item.put("similarity", chunk.getSimilarity());       // 相似度，范围[-1,1]，值越大越相似
                        item.put("createdAt", chunk.getCreatedAt() != null ? chunk.getCreatedAt().toString() : null); // 上传时间
                        return item;
                    })
                    .toList();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "query", query,
                    "results", responseResults
            ));

        } catch (Exception e) {
            log.error("搜索失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "搜索失败: " + e.getMessage()
            ));
        }
    }
}
