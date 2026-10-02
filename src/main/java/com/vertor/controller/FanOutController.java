package com.vertor.controller;

import com.vertor.service.FanOutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * LangGraph4j Fan-Out / Fan-In 并发多 Agent 工作流控制器
 *
 * 通过 POST /api/fanout/process 接收用户任务，
 * 驱动 Fan-Out 工作流（任务分解 → 多 Agent 并发执行 → 归总汇总）并返回结果
 */
@Tag(name = "Fan-Out 并发多 Agent", description = "基于 Fan-Out / Fan-In 模式的多 Agent 并发执行与归总接口")
@RestController
@RequestMapping("/api/fanout")
public class FanOutController {

    private static final Logger log = LoggerFactory.getLogger(FanOutController.class);

    private final FanOutService fanOutService;

    public FanOutController(FanOutService fanOutService) {
        this.fanOutService = fanOutService;
    }

    /**
     * 提交任务到 Fan-Out 并发工作流进行处理
     * 任务将被分解为多个子任务，由 4 位专家 Agent 并发执行，最后归总产出最终报告
     */
    @Operation(
            summary = "提交任务到并发多 Agent 工作流",
            description = "Fan-Out / Fan-In 模式：任务分解 → 4 位专家 Agent（调研分析师、创意策划、技术顾问、执行专家）并发执行各自子任务 → 归总汇总产出最终报告。展示多 Agent 并发编排能力。"
    )
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> process(@RequestBody Map<String, String> request) {
        try {
            String userTask = request.get("task");
            if (userTask == null || userTask.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "任务内容不能为空"
                ));
            }

            log.info("Fan-Out 并发工作流收到任务: {}", userTask);
            log.info("Fan-Out 将先分解任务，再由 4 位专家 Agent 并发执行，最后归总汇总");

            Map<String, Object> result = fanOutService.process(userTask);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Fan-Out 并发工作流处理失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "处理失败: " + e.getMessage()
            ));
        }
    }
}
