package com.agentflow.controller;

import com.agentflow.service.KnowledgeGraphIngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文档控制器 V3：文件上传后抽取知识图谱（实体 + 关系）
 */
@Tag(name = "文档管理 V3（知识图谱）", description = "上传文档并抽取知识图谱，按文件名查询实体与关系")
@RestController
@RequestMapping("/api/documents/v3")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "database.available", havingValue = "true")
public class DocumentV3Controller {

    private static final Logger log = LoggerFactory.getLogger(DocumentV3Controller.class);

    private final KnowledgeGraphIngestService knowledgeGraphIngestService;

    public DocumentV3Controller(KnowledgeGraphIngestService knowledgeGraphIngestService) {
        this.knowledgeGraphIngestService = knowledgeGraphIngestService;
    }

    /**
     * 上传文件：提取文本 → 分块向量化 → 抽取三元组写入图谱
     */
    @Operation(
            summary = "上传文档并构建知识图谱",
            description = "复用标题分块与向量化，按块调用 DeepSeek 抽取三元组，写入 kg_entity / kg_relation。支持 TXT、PDF、DOCX。"
    )
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @Parameter(description = "要上传的文档文件", required = true)
            @RequestParam("file") MultipartFile file) {
        try {
            String fileName = file.getOriginalFilename();
            log.info("开始知识图谱文件上传: {}", fileName);

            Map<String, Object> ingest = knowledgeGraphIngestService.processAndStore(file);
            Object chunkCountObj = ingest.get("chunkCount");
            int chunkCount = chunkCountObj instanceof Number number ? number.intValue() : 0;
            if (chunkCount == 0) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "文件内容为空或无法提取文本"
                ));
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("message", "文件处理成功（知识图谱）");
            body.putAll(ingest);
            return ResponseEntity.ok(body);

        } catch (Exception e) {
            log.error("知识图谱文件处理失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "文件处理失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 按文件名查询该文件对应的实体与关系
     */
    @Operation(
            summary = "查询文件知识图谱",
            description = "按上传文件名返回该文件关联的实体列表与关系三元组。"
    )
    @GetMapping("/graph")
    public ResponseEntity<Map<String, Object>> graph(
            @Parameter(description = "上传时的文件名", required = true)
            @RequestParam("fileName") String fileName) {
        try {
            if (fileName == null || fileName.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "fileName 不能为空"
                ));
            }

            Map<String, Object> graph = knowledgeGraphIngestService.loadGraph(fileName.trim());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.putAll(graph);
            return ResponseEntity.ok(body);

        } catch (Exception e) {
            log.error("查询知识图谱失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "查询失败: " + e.getMessage()
            ));
        }
    }
}
