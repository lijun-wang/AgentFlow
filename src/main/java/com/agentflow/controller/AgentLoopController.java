package com.agentflow.controller;

import com.agentflow.service.AgentLoopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * LangGraph4j Agent Loop 工作流控制器：提供智能体循环调用工具的演示接�? *
 * 通过 POST /api/agentloop/process 接收用户请求�? * 驱动 LangGraph4j Agent Loop 工作流（Agent 推理 �?工具执行 �?循环）执行并返回结果
 */
@Tag(name = "LangGraph4j Agent Loop 智能体循�?, description = "基于 LangGraph4j �?ReAct 模式智能体循环调用工�?Demo 接口")
@RestController
@RequestMapping("/api/agentloop")
public class AgentLoopController {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopController.class);

    private final AgentLoopService agentLoopService;

    public AgentLoopController(AgentLoopService agentLoopService) {
        this.agentLoopService = agentLoopService;
    }

    /**
     * 提交请求�?Agent Loop 工作流进行处�?     */
    @Operation(
            summary = "提交请求�?Agent Loop 工作�?,
            description = "基于 LangGraph4j Agent Loop 模式，智能体循环调用工具（天气查询、经纬度查询、节假日查询）直到获得最终答案，返回执行步骤及最终回答�?
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

            log.info("Agent Loop 工作流收到问�? {}", question);

            Map<String, Object> result = agentLoopService.process(question);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Agent Loop 工作流处理失�?, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "处理失败: " + e.getMessage()
            ));
        }
    }
}
