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
 * LangGraph4j Reflection（反思迭代）工作流服务
 *
 * 每次请求通过 CompiledGraph.stream() 驱动 Reflection 工作流执行，
 * Generate 节点生成初稿，Reflect 节点评审，Revise 节点修正，
 * 通过反思-修正循环不断优化输出质量，
 * 收集每个节点的输出作为步骤结果返回给前端展示。
 */
@Service
public class ReflectionService {

    private static final Logger log = LoggerFactory.getLogger(ReflectionService.class);

    /** 节点名称到中文标签的映射 */
    private static final Map<String, String> STEP_LABELS = Map.of(
            "generate", "初稿生成",
            "reflect", "反思评审",
            "revise", "修正改进",
            "final_output", "最终输出"
    );

    /** 节点名称到图标的映射 */
    private static final Map<String, String> STEP_ICONS = Map.of(
            "generate", "✍️",
            "reflect", "🔍",
            "revise", "🔧",
            "final_output", "🎯"
    );

    private final CompiledGraph<MessagesState<Object>> reflectionWorkflow;

    public ReflectionService(
            @Qualifier("reflectionWorkflow") CompiledGraph<MessagesState<Object>> reflectionWorkflow) {
        this.reflectionWorkflow = reflectionWorkflow;
        log.info("ReflectionService 初始化完成，Reflection 反思迭代工作流已就绪");
    }

    /**
     * 处理用户请求，执行 LangGraph4j Reflection 反思迭代工作流
     *
     * @param userRequest 用户输入的请求信息
     * @return 包含最终回答、各步骤执行结果的结构化响应
     */
    public Map<String, Object> process(String userRequest) {
        log.info("Reflection 工作流开始处理: {}", userRequest);
        long startTime = System.currentTimeMillis();

        try {
            // 构建初始状态：将用户请求作为 UserMessage 放入消息列表
            Map<String, Object> inputs = Map.of(
                    "messages", UserMessage.from(userRequest)
            );

            // 执行工作流，收集每个节点的输出
            List<Map<String, Object>> steps = new ArrayList<>();
            int reflectCount = 0;
            int reviseCount = 0;
            boolean needsImprovement = false;

            for (var step : reflectionWorkflow.stream(inputs)) {
                String nodeName = step.node();
                String stepLabel = STEP_LABELS.getOrDefault(nodeName, nodeName);
                String stepIcon = STEP_ICONS.getOrDefault(nodeName, "⚙️");

                // 从节点输出状态中提取最新消息作为该步骤的输出
                String stepOutput = extractLastAiMessage(step.state());

                // 统计反思和修正次数（用于展示循环信息）
                if ("reflect".equals(nodeName)) {
                    reflectCount++;
                    needsImprovement = stepOutput.contains("需要改进");
                }
                if ("revise".equals(nodeName)) {
                    reviseCount++;
                }

                steps.add(Map.of(
                        "step", nodeName,
                        "label", stepLabel,
                        "icon", stepIcon,
                        "output", stepOutput,
                        "status", "completed",
                        "iteration", Math.max(reflectCount, reviseCount)
                ));

                log.info("Reflection 节点 [{}] 执行完成（第 {} 轮反思）", stepLabel, reflectCount);
            }

            // 最终回答来自 final_output 节点的输出
            String finalAnswer = steps.isEmpty() ? "工作流未产生输出"
                    : (String) steps.get(steps.size() - 1).get("output");

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Reflection 工作流处理完成，反思 {} 轮，修正 {} 轮，共 {} 个步骤，耗时 {}ms",
                    reflectCount, reviseCount, steps.size(), elapsed);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("answer", finalAnswer);
            result.put("steps", steps);
            result.put("reflectIterations", reflectCount);
            result.put("reviseIterations", reviseCount);
            result.put("needsImprovement", needsImprovement);
            return result;

        } catch (Exception e) {
            log.error("Reflection 工作流执行失败", e);
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
