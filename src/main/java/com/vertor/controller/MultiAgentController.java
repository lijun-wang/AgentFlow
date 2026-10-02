package com.vertor.controller;

import com.vertor.service.MultiAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * LangGraph4j Multi Agent 圆桌讨论控制器：提供多专家协作讨论接口
 *
 * 通过 POST /api/multiagent/process 接收用户话题，
 * 驱动 LangGraph4j Multi Agent 工作流（话题分析 → 产品经理 → 架构师 → 开发者 → 测试工程师 → 综合共识）执行并返回结果
 */
@Tag(name = "LangGraph4j Multi Agent 圆桌讨论", description = "基于 LangGraph4j 的多专家圆桌讨论模式 Demo 接口")
@RestController
@RequestMapping("/api/multiagent")
public class MultiAgentController {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentController.class);

    private final MultiAgentService multiAgentService;

    public MultiAgentController(MultiAgentService multiAgentService) {
        this.multiAgentService = multiAgentService;
    }

    /**
     * 提交话题到 Multi Agent 圆桌讨论工作流进行处理
     * 四个专家角色将依次发表观点，最后形成综合共识
     */
    @Operation(
            summary = "提交话题到圆桌讨论工作流",
            description = "基于 LangGraph4j 多专家协作模式：话题分析 → 产品经理视角 → 架构师视角 → 开发者视角 → 测试工程师视角 → 综合共识。展示多角色从不同角度分析同一话题的能力。"
    )
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> process(@RequestBody Map<String, String> request) {
        try {
            String topic = request.get("topic");
            if (topic == null || topic.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "话题内容不能为空"
                ));
            }

            log.info("Multi Agent 圆桌讨论收到话题: {}", topic);
            log.info("将有 4 位专家（产品经理、架构师、开发者、测试工程师）依次发表观点");

            Map<String, Object> result = multiAgentService.process(topic);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Multi Agent 圆桌讨论处理失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "处理失败: " + e.getMessage()
            ));
        }
    }
}
