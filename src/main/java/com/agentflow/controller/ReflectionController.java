package com.agentflow.controller;

import com.agentflow.service.ReflectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * LangGraph4j Reflection（反思迭代）工作流控制器
 *
 * 通过 POST /api/reflection/process 接收用户请求�? * 驱动 LangGraph4j Reflection 工作流（初稿生成 �?反思评�?�?修正改进循环 �?最终输出）执行并返回结�? */
@Tag(name = "LangGraph4j Reflection 反思迭�?, description = "基于 LangGraph4j �?Reflection 自我反思与迭代优化工作�?Demo 接口")
@RestController
@RequestMapping("/api/reflection")
public class ReflectionController {

    private static final Logger log = LoggerFactory.getLogger(ReflectionController.class);

    private final ReflectionService reflectionService;

    public ReflectionController(ReflectionService reflectionService) {
        this.reflectionService = reflectionService;
    }

    /**
     * 提交请求�?LangGraph4j Reflection 反思迭代工作流进行处理
     * Generate 生成初稿，Reflect 评审，Revise 修正，循环迭代直到质量达�?     */
    @Operation(
            summary = "提交请求�?Reflection 反思迭代工作流",
            description = "基于 LangGraph4j 实现 Reflection 模式：初稿生�?�?反思评�?�?修正改进循环 �?最终输出。支持自我反思与迭代优化，展�?LLM 自我改进能力�?
    )
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> process(@RequestBody Map<String, String> request) {
        try {
            String userRequest = request.get("request");
            if (userRequest == null || userRequest.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "请求内容不能为空"
                ));
            }

            log.info("Reflection 工作流收到请�? {}", userRequest);

            Map<String, Object> result = reflectionService.process(userRequest);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Reflection 工作流处理失�?, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "处理失败: " + e.getMessage()
            ));
        }
    }
}
