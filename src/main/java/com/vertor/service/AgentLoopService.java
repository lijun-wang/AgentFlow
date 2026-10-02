package com.vertor.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * LangGraph4j Agent Loop 工作流服务：执行智能体循环调用工具的流程
 *
 * 每次请求通过 CompiledGraph.stream() 驱动 Agent Loop 工作流执行，
 * Agent 节点调用 LLM 推理并决定是否使用工具，Tools 节点执行工具后回到 Agent，
 * 直到 LLM 给出最终答案，收集每个步骤的输出返回给前端展示。
 */
@Service
public class AgentLoopService {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopService.class);

    /** 节点名称到中文标签的映射 */
    private static final Map<String, String> STEP_LABELS = Map.of(
            "agent", "智能体推理",
            "tools", "工具执行"
    );

    /** 节点名称到图标的映射 */
    private static final Map<String, String> STEP_ICONS = Map.of(
            "agent", "🧠",
            "tools", "🔧"
    );

    private final CompiledGraph<MessagesState<Object>> agentLoopWorkflow;

    public AgentLoopService(CompiledGraph<MessagesState<Object>> agentLoopWorkflow) {
        this.agentLoopWorkflow = agentLoopWorkflow;
        log.info("AgentLoopService 初始化完成，Agent Loop 工作流已就绪");
    }

    /**
     * 处理用户请求，执行 LangGraph4j Agent Loop 工作流
     *
     * @param userRequest 用户输入的请求信息
     * @return 包含最终回答、迭代次数和各步骤执行结果的结构化响应
     */
    public Map<String, Object> process(String userRequest) {
        log.info("Agent Loop 工作流开始处理: {}", userRequest);
        long startTime = System.currentTimeMillis();

        try {
            // 构建初始状态：将用户请求作为 UserMessage 放入消息列表
            Map<String, Object> inputs = Map.of(
                    "messages", UserMessage.from(userRequest)
            );

            // 执行工作流，收集每个节点的输出
            List<Map<String, Object>> steps = new ArrayList<>();
            int iteration = 0;
            String finalAnswer = "";

            for (var step : agentLoopWorkflow.stream(inputs)) {
                String nodeName = step.node();
                String stepLabel = STEP_LABELS.getOrDefault(nodeName, nodeName);
                String stepIcon = STEP_ICONS.getOrDefault(nodeName, "⚙️");

                // 每进入 agent 节点算一次迭代
                if ("agent".equals(nodeName)) {
                    iteration++;
                }

                // 从节点输出状态中提取信息
                String stepOutput = extractStepOutput(step.state(), nodeName);

                // 判断步骤类型
                String stepType = "agent".equals(nodeName) ? "reasoning" : "tool_execution";

                // 提取工具调用信息（如果有）
                List<Map<String, String>> toolCalls = extractToolCalls(step.state(), nodeName);

                steps.add(Map.of(
                        "step", nodeName,
                        "label", stepLabel,
                        "icon", stepIcon,
                        "output", stepOutput,
                        "type", stepType,
                        "iteration", iteration,
                        "toolCalls", toolCalls,
                        "status", "completed"
                ));

                // 如果是最后一个 agent 节点且没有工具调用，提取最终答案
                if ("agent".equals(nodeName) && !hasToolCalls(step.state())) {
                    finalAnswer = stepOutput;
                }

                log.info("Agent Loop 节点 [{}] 第 {} 次迭代执行完成", stepLabel, iteration);
            }

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Agent Loop 工作流处理完成，共 {} 次迭代，{} 个步骤，耗时 {}ms",
                    iteration, steps.size(), elapsed);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("answer", finalAnswer);
            result.put("steps", steps);
            result.put("iterations", iteration);
            return result;

        } catch (Exception e) {
            log.error("Agent Loop 工作流执行失败", e);
            return Map.of(
                    "success", false,
                    "message", "工作流执行失败: " + e.getMessage()
            );
        }
    }

    /**
     * 从节点状态中提取输出文本
     */
    private String extractStepOutput(MessagesState<Object> state, String nodeName) {
        try {
            var messages = state.messages();
            if (messages.isEmpty()) {
                return "";
            }

            // 对于 agent 节点，提取最后一条 AiMessage
            if ("agent".equals(nodeName)) {
                for (int i = messages.size() - 1; i >= 0; i--) {
                    Object msg = messages.get(i);
                    if (msg instanceof AiMessage am) {
                        boolean hasToolCalls = am.toolExecutionRequests() != null
                                && !am.toolExecutionRequests().isEmpty();
                        if (hasToolCalls) {
                            // 如果有工具调用，返回工具调用描述
                            StringBuilder sb = new StringBuilder();
                            sb.append("决定调用工具：");
                            for (var req : am.toolExecutionRequests()) {
                                sb.append(req.name()).append("(").append(req.arguments()).append(") ");
                            }
                            return sb.toString();
                        } else {
                            // 否则返回最终答案
                            return am.text();
                        }
                    }
                }
            }

            // 对于 tools 节点，提取工具执行结果
            if ("tools".equals(nodeName)) {
                StringBuilder sb = new StringBuilder();
                for (Object msg : messages) {
                    if (msg instanceof ToolExecutionResultMessage term) {
                        if (!sb.isEmpty()) {
                            sb.append("\n\n");
                        }
                        sb.append("工具执行结果：\n");
                        sb.append(term.text());
                    }
                }
                return sb.toString();
            }

        } catch (Exception e) {
            log.warn("提取节点输出失败", e);
        }
        return "";
    }

    /**
     * 从状态中提取工具调用信息
     */
    private List<Map<String, String>> extractToolCalls(MessagesState<Object> state, String nodeName) {
        List<Map<String, String>> toolCalls = new ArrayList<>();

        if (!"agent".equals(nodeName)) {
            return toolCalls;
        }

        try {
            var messages = state.messages();
            for (int i = messages.size() - 1; i >= 0; i--) {
                Object msg = messages.get(i);
                if (msg instanceof AiMessage am) {
                    if (am.toolExecutionRequests() != null && !am.toolExecutionRequests().isEmpty()) {
                        for (var req : am.toolExecutionRequests()) {
                            toolCalls.add(Map.of(
                                    "name", req.name(),
                                    "arguments", req.arguments()
                            ));
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("提取工具调用信息失败", e);
        }

        return toolCalls;
    }

    /**
     * 检查状态中最后一条 AiMessage 是否有工具调用
     */
    private boolean hasToolCalls(MessagesState<Object> state) {
        try {
            var messages = state.messages();
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i) instanceof AiMessage am) {
                    return am.toolExecutionRequests() != null
                            && !am.toolExecutionRequests().isEmpty();
                }
            }
        } catch (Exception e) {
            log.warn("检查工具调用失败", e);
        }
        return false;
    }
}
