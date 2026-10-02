package com.vertor.controller;

import com.vertor.service.LangGraphService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * LangGraph4j 工作流控制器：提供基于状态图工作流的知识助手接口
 *
 * 通过 POST /api/langgraph/process 接收用户问题，
 * 驱动 LangGraph4j 工作流（问题分析 → 知识生成 → 回答格式化）执行并返回结果
 */
@Tag(name = "LangGraph4j 工作流", description = "基于 LangGraph4j 状态图工作流的知识助手接口")
@RestController
@RequestMapping("/api/langgraph")
public class LangGraphController {

    private static final Logger log = LoggerFactory.getLogger(LangGraphController.class);

    private final LangGraphService langGraphService;

    public LangGraphController(LangGraphService langGraphService) {
        this.langGraphService = langGraphService;
    }

    /**
     * 提交问题到 LangGraph4j 工作流进行处理
     */
    @Operation(
            summary = "提交问题到工作流",
            description = "基于 LangGraph4j 状态图工作流，依次执行问题分析、知识生成、回答格式化三个节点，返回最终回答及各步骤中间结果。"
    )
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> process(@RequestBody Map<String, String> request) {
        try {
            String question = request.get("question");
            if (question == null || question.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "问题内容不能为空"
                ));
            }

            log.info("LangGraph4j 工作流收到问题: {}", question);

            Map<String, Object> result = langGraphService.process(question);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("LangGraph4j 工作流处理失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "处理失败: " + e.getMessage()
            ));
        }
    }
}
