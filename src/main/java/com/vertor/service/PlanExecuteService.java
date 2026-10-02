package com.vertor.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * LangGraph4j Plan-and-Execute 工作流服务
 *
 * 每次请求通过 CompiledGraph.stream() 驱动 Plan-and-Execute 工作流执行，
 * Planner 节点制定计划，Executor 节点执行计划，Replanner 节点评估结果，
 * 收集每个节点的输出作为步骤结果返回给前端展示。
 */
@Service
public class PlanExecuteService {

    private static final Logger log = LoggerFactory.getLogger(PlanExecuteService.class);

    /** 节点名称到中文标签的映射 */
    private static final Map<String, String> STEP_LABELS = Map.of(
            "planner", "制定计划",
            "executor", "执行计划",
            "replanner", "评估调整",
            "final_output", "综合输出"
    );

    /** 节点名称到图标的映射 */
    private static final Map<String, String> STEP_ICONS = Map.of(
            "planner", "📋",
            "executor", "⚡",
            "replanner", "🔍",
            "final_output", "🎯"
    );

    private final CompiledGraph<MessagesState<Object>> planExecuteWorkflow;

    public PlanExecuteService(
            @Qualifier("planExecuteWorkflow") CompiledGraph<MessagesState<Object>> planExecuteWorkflow) {
        this.planExecuteWorkflow = planExecuteWorkflow;
        log.info("PlanExecuteService 初始化完成，Plan-and-Execute 工作流已就绪");
    }

    /**
     * 处理用户请求，执行 LangGraph4j Plan-and-Execute 工作流
     *
     * @param userRequest 用户输入的请求信息
     * @return 包含最终回答、各步骤执行结果的结构化响应
     */
    public Map<String, Object> process(String userRequest) {
        log.info("Plan-Execute 工作流开始处理: {}", userRequest);
        long startTime = System.currentTimeMillis();

        try {
            // 构建初始状态：将用户请求作为 UserMessage 放入消息列表
            Map<String, Object> inputs = Map.of(
                    "messages", UserMessage.from(userRequest)
            );

            // 执行工作流，收集每个节点的输出
            List<Map<String, Object>> steps = new ArrayList<>();
            int executorCount = 0;
            boolean needsReplan = false;

            for (var step : planExecuteWorkflow.stream(inputs)) {
                String nodeName = step.node();
                String stepLabel = STEP_LABELS.getOrDefault(nodeName, nodeName);
                String stepIcon = STEP_ICONS.getOrDefault(nodeName, "⚙️");

                // 从节点输出状态中提取最新消息作为该步骤的输出
                String stepOutput = extractLastAiMessage(step.state());

                // 统计 executor 执行次数（用于展示循环信息）
                if ("executor".equals(nodeName)) {
                    executorCount++;
                }

                // 检测 replanner 是否需要继续执行
                if ("replanner".equals(nodeName) && stepOutput.contains("需要补充")) {
                    needsReplan = true;
                } else if ("replanner".equals(nodeName)) {
                    needsReplan = false;
                }

                steps.add(Map.of(
                        "step", nodeName,
                        "label", stepLabel,
                        "icon", stepIcon,
                        "output", stepOutput,
                        "status", "completed",
                        "iteration", executorCount
                ));

                log.info("Plan-Execute 节点 [{}] 执行完成（第 {} 轮）", stepLabel, executorCount);
            }

            // 最终回答来自 final_output 节点的输出
            String finalAnswer = steps.isEmpty() ? "工作流未产生输出"
                    : (String) steps.get(steps.size() - 1).get("output");

            // 提取计划内容（planner 节点的输出）
            String plan = "";
            for (var s : steps) {
                if ("planner".equals(s.get("step"))) {
                    plan = (String) s.get("output");
                    break;
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Plan-Execute 工作流处理完成，Executor 执行 {} 轮，共 {} 个步骤，耗时 {}ms",
                    executorCount, steps.size(), elapsed);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("answer", finalAnswer);
            result.put("steps", steps);
            result.put("plan", plan);
            result.put("executorIterations", executorCount);
            result.put("needsReplan", needsReplan);
            return result;

        } catch (Exception e) {
            log.error("Plan-Execute 工作流执行失败", e);
            return Map.of(
                    "success", false,
                    "message", "工作流执行失败: " + e.getMessage()
            );
        }
    }

    /**
     * 从 MessagesState 中提取最后一条 AiMessage 的文本内容
     */
    private String extractLastAiMessage(MessagesState<Object> state) {
        try {
            var messages = state.messages();
            for (int i = messages.size() - 1; i >= 0; i--) {
                Object msg = messages.get(i);
                if (msg instanceof AiMessage am) {
                    return am.text();
                }
            }
        } catch (Exception e) {
            log.warn("提取 AiMessage 失败", e);
        }
        return "";
    }
}
