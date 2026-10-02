package com.vertor.controller;

import com.vertor.service.SubagentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * LangGraph4j Subagent 多 Agent 协作控制器：提供 Supervisor 路由模式的智能分发接口
 *
 * 通过 POST /api/subagent/process 接收用户请求，
 * 驱动 LangGraph4j Subagent 工作流（查询改写 → Supervisor 路由 → 专家子 Agent → 汇总格式化）执行并返回结果
 */
@Tag(name = "LangGraph4j Subagent 多 Agent 协作", description = "基于 LangGraph4j 条件边的 Supervisor 多 Agent 路由 Demo 接口")
@RestController
@RequestMapping("/api/subagent")
public class SubagentController {

    private static final Logger log = LoggerFactory.getLogger(SubagentController.class);

    private final SubagentService subagentService;

    public SubagentController(SubagentService subagentService) {
        this.subagentService = subagentService;
    }

    /**
     * 提交请求到 LangGraph4j Subagent 工作流进行处理
     * Supervisor 将分析请求类型并路由到对应专家子 Agent
     */
    @Operation(
            summary = "提交请求到多 Agent 协作工作流",
            description = "基于 LangGraph4j 条件边实现 Supervisor 路由模式：查询改写 → Supervisor 分析请求类型 → 动态路由到编程助手/写作助手/通用问答专家子 Agent → 汇总格式化输出。展示多 Agent 协作与条件边能力。"
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

            log.info("Subagent 工作流收到请求: {}", userRequest);
            log.info("Subagent 工作流将先使用 DeepSeek 改写查询，再进行路由分发");

            Map<String, Object> result = subagentService.process(userRequest);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Subagent 工作流处理失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "处理失败: " + e.getMessage()
            ));
        }
    }
}
