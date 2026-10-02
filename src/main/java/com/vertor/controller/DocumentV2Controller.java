package com.vertor.controller;

import com.vertor.model.DocumentChunk;
import com.vertor.service.ChatService;
import com.vertor.service.Langchain4jVectorStoreService;
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
 * 文档控制器 V2：使用 LangChain4j 进行文件切割、向量化存储和检索
 * <p>
 * 仅在 PostgreSQL 数据库可用时注册（通过 database.available 属性控制）
 */
@Tag(name = "文档管理 V2（LangChain4j）", description = "基于 LangChain4j 的文件上传、向量化存储与相似度检索接口")
@RestController
@RequestMapping("/api/documents/v2")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "database.available", havingValue = "true")
public class DocumentV2Controller {

    private static final Logger log = LoggerFactory.getLogger(DocumentV2Controller.class);

    private final Langchain4jVectorStoreService langchain4jVectorStoreService;
    private final ChatService chatService;

    public DocumentV2Controller(Langchain4jVectorStoreService langchain4jVectorStoreService,
                                 ChatService chatService) {
        this.langchain4jVectorStoreService = langchain4jVectorStoreService;
        this.chatService = chatService;
    }

    /**
     * 上传文件：使用 LangChain4j 提取文本 → 分块 → 生成向量 → 存入 document_chunk_langchain4j 表
     */
    @Operation(
            summary = "上传文档（LangChain4j）",
            description = "使用 LangChain4j 进行文件切割和向量化存储，存入 document_chunk_langchain4j 表。支持 TXT、PDF、DOCX 格式。"
    )
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @Parameter(description = "要上传的文档文件", required = true)
            @RequestParam("file") MultipartFile file) {
        try {
            String fileName = file.getOriginalFilename();
            log.info("开始 LangChain4j 处理文件上传: {}", fileName);

            // 使用 LangChain4j 进行分块、向量化和存储
            int chunkCount = langchain4jVectorStoreService.processAndStore(file);

            if (chunkCount == 0) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "文件内容为空或无法提取文本"
                ));
            }

            log.info("LangChain4j 文件处理完成: {}，共 {} 个分块", fileName, chunkCount);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "文件处理成功（LangChain4j）",
                    "fileName", fileName,
                    "chunkCount", chunkCount
            ));

        } catch (Exception e) {
            log.error("LangChain4j 文件处理失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "文件处理失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 智能问答 V2：使用 LangChain4j 向量检索 + DeepSeek 总结回答
     */
    @Operation(
            summary = "智能问答（LangChain4j）",
            description = "根据用户提问，通过 LangChain4j 向量检索找到最相关的文档分块，再由 DeepSeek 总结回答。"
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

            log.info("开始 LangChain4j 智能问答，问题: {}", question);

            // 1. 使用 DeepSeek 将用户问题改写为更适合检索的形式
            String rewrittenQuestion = chatService.rewriteQuery(question);
            log.info("查询改写结果: '{}' -> '{}'", question, rewrittenQuestion);

            // 2. 将改写后的查询结果按空格截断，依次检索每个关键词，最后合并结果
            String[] queryTerms = rewrittenQuestion.trim().split("\\s+");
            log.info("改写后查询按空格截断，共 {} 个检索词: {}", queryTerms.length, Arrays.toString(queryTerms));

            int topK = 10;
            // 使用 LinkedHashMap 按 id 去重，保留首次出现的结果（相似度最高）
            Map<Long, DocumentChunk> mergedMap = new LinkedHashMap<>();

            for (String term : queryTerms) {
                if (term.isBlank()) {
                    continue;
                }
                log.info("检索关键词: '{}'", term);
                float[] termEmbedding = langchain4jVectorStoreService.embedQuery(term);
                List<DocumentChunk> termResults = langchain4jVectorStoreService.search(termEmbedding, topK);
                for (DocumentChunk chunk : termResults) {
                    mergedMap.putIfAbsent(chunk.getId(), chunk);
                }
            }

            // 按相似度降序排序，取前10条
            List<DocumentChunk> results = mergedMap.values().stream()
                    .sorted((a, b) -> {
                        double simA = a.getSimilarity() != null ? a.getSimilarity() : -1.0;
                        double simB = b.getSimilarity() != null ? b.getSimilarity() : -1.0;
                        return Double.compare(simB, simA);
                    })
                    .limit(10)
                    .toList();
            log.info("合并检索结果：去重前共检索 {} 次，去重后共 {} 条，取相似度前 {} 条", queryTerms.length, mergedMap.size(), results.size());

            if (results.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "answer", "未找到与您问题相关的内容，请尝试上传更多文档。",
                        "rewrittenQuestion", rewrittenQuestion,
                        "references", List.of()
                ));
            }

            // 4. 将检索到的内容拼接为上下文
            StringBuilder contextBuilder = new StringBuilder();
            for (int i = 0; i < results.size(); i++) {
                DocumentChunk chunk = results.get(i);
                if (i > 0) {
                    contextBuilder.append("\n\n---\n\n");
                }
                contextBuilder.append(chunk.getChunkText());
            }

            // 5. 调用 DeepSeek 对检索内容进行总结
            String answer = chatService.summarize(question, contextBuilder.toString());

            // 6. 构建引用来源
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

            log.info("LangChain4j 智能问答完成，检索到 {} 条相关内容，DeepSeek 总结完成", results.size());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "answer", answer,
                    "rewrittenQuestion", rewrittenQuestion,
                    "references", references
            ));

        } catch (Exception e) {
            log.error("LangChain4j 智能问答失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "问答失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 向量相似度搜索（LangChain4j）：从 document_chunk_langchain4j 表检索
     */
    @Operation(
            summary = "向量搜索（LangChain4j）",
            description = "使用 LangChain4j 生成的向量，在 document_chunk_langchain4j 表中进行语义相似度搜索。"
    )
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @Parameter(description = "搜索查询文本", required = true)
            @RequestParam("query") String query,
            @Parameter(description = "返回结果数量，默认5")
            @RequestParam(value = "topK", defaultValue = "10") int topK) {
        try {
            log.info("开始 LangChain4j 搜索，查询: {}，topK: {}", query, topK);

            // 1. 使用 LangChain4j EmbeddingModel 将查询文本转为向量
            float[] queryEmbedding = langchain4jVectorStoreService.embedQuery(query);

            // 2. 从 document_chunk_langchain4j 表执行向量搜索
            List<DocumentChunk> results = langchain4jVectorStoreService.search(queryEmbedding, topK);

            // 3. 构建响应
            List<Map<String, Object>> responseResults = IntStream.range(0, results.size())
                    .mapToObj(i -> {
                        DocumentChunk chunk = results.get(i);
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("rank", i + 1);
                        item.put("fileName", chunk.getFileName());
                        item.put("chunkIndex", chunk.getChunkIndex());
                        item.put("text", chunk.getChunkText());
                        item.put("cosineDistance", chunk.getCosineDistance());
                        item.put("similarity", chunk.getSimilarity());
                        item.put("createdAt", chunk.getCreatedAt() != null ? chunk.getCreatedAt().toString() : null);
                        return item;
                    })
                    .toList();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "query", query,
                    "results", responseResults
            ));

        } catch (Exception e) {
            log.error("LangChain4j 搜索失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "搜索失败: " + e.getMessage()
            ));
        }
    }
}
