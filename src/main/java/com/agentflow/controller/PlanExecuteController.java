package com.agentflow.controller;

import com.agentflow.service.PlanExecuteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * LangGraph4j Plan-and-Execute 工作流控制器
 *
 * 通过 POST /api/planexecute/process 接收用户请求�? * 驱动 LangGraph4j Plan-and-Execute 工作流（规划 �?执行 �?评估 �?综合输出）执行并返回结果
 */
@Tag(name = "LangGraph4j Plan-and-Execute 工作�?, description = "基于 LangGraph4j �?Plan-and-Execute 动态规划工作流 Demo 接口")
@RestController
@RequestMapping("/api/planexecute")
public class PlanExecuteController {

    private static final Logger log = LoggerFactory.getLogger(PlanExecuteController.class);

    private final PlanExecuteService planExecuteService;

    public PlanExecuteController(PlanExecuteService planExecuteService) {
        this.planExecuteService = planExecuteService;
    }

    /**
     * 提交请求�?LangGraph4j Plan-and-Execute 工作流进行处�?     * Planner 制定计划，Executor 执行，Replanner 评估，最终综合输�?     */
    @Operation(
            summary = "提交请求�?Plan-and-Execute 工作�?,
            description = "基于 LangGraph4j 实现 Plan-and-Execute 模式：Planner 制定计划 �?Executor 执行 �?Replanner 评估调整 �?综合输出。支持动态规划与条件路由，展示智能任务分解能力�?
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

            log.info("Plan-Execute 工作流收到请�? {}", userRequest);

            Map<String, Object> result = planExecuteService.process(userRequest);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Plan-Execute 工作流处理失�?, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "处理失败: " + e.getMessage()
            ));
        }
    }
}
