package com.vertor.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * LangGraph4j Subagent 多 Agent 协作工作流服务
 *
 * 每次请求通过 CompiledGraph.stream() 驱动 Subagent 工作流执行，
 * Supervisor 节点分析请求类型并路由到对应专家子 Agent，
 * 收集每个节点的输出作为步骤结果返回给前端展示。
 */
@Service
public class SubagentService {

    private static final Logger log = LoggerFactory.getLogger(SubagentService.class);

    /** 节点名称到中文标签的映射 */
    private static final Map<String, String> STEP_LABELS = Map.of(
            "query_rewrite", "查询改写",
            "supervisor", "Supervisor 路由",
            "coding", "编程助手",
            "writing", "写作助手",
            "qa", "通用问答",
            "summary", "汇总格式化"
    );

    /** 节点名称到专家图标的映射 */
    private static final Map<String, String> STEP_ICONS = Map.of(
            "query_rewrite", "✏️",
            "supervisor", "🧠",
            "coding", "💻",
            "writing", "✍️",
            "qa", "📚",
            "summary", "📋"
    );

    /** 专家子 Agent 节点集合 */
    private static final Set<String> EXPERT_NODES = Set.of("coding", "writing", "qa");

    private final CompiledGraph<MessagesState<Object>> subagentWorkflow;

    public SubagentService(CompiledGraph<MessagesState<Object>> subagentWorkflow) {
        this.subagentWorkflow = subagentWorkflow;
        log.info("SubagentService 初始化完成，多 Agent 协作工作流已就绪");
    }

    /**
     * 处理用户请求，执行 LangGraph4j Subagent 工作流
     *
     * @param userRequest 用户输入的请求信息
     * @return 包含最终回答、路由信息和各步骤执行结果的结构化响应
     */
    public Map<String, Object> process(String userRequest) {
        log.info("Subagent 工作流开始处理: {}", userRequest);
        long startTime = System.currentTimeMillis();

        try {
            // 构建初始状态：将用户请求作为 UserMessage 放入消息列表
            Map<String, Object> inputs = Map.of(
                    "messages", UserMessage.from(userRequest)
            );

            // 执行工作流，收集每个节点的输出
            List<Map<String, Object>> steps = new ArrayList<>();
            String routedTo = "";
            String routedLabel = "";
            String routedIcon = "";

            for (var step : subagentWorkflow.stream(inputs)) {
                String nodeName = step.node();
                String stepLabel = STEP_LABELS.getOrDefault(nodeName, nodeName);
                String stepIcon = STEP_ICONS.getOrDefault(nodeName, "⚙️");

                // 从节点输出状态中提取最新消息作为该步骤的输出
                String stepOutput = extractLastAiMessage(step.state());

                // 检测路由目标（Supervisor 节点之后的第一个专家节点）
                if (EXPERT_NODES.contains(nodeName)) {
                    routedTo = nodeName;
                    routedLabel = stepLabel;
                    routedIcon = stepIcon;
                }

                steps.add(Map.of(
                        "step", nodeName,
                        "label", stepLabel,
                        "icon", stepIcon,
                        "output", stepOutput,
                        "status", "completed"
                ));

                log.info("Subagent 节点 [{}] 执行完成", stepLabel);
            }

            // 最终回答来自最后一个节点（summary）的输出
            String finalAnswer = steps.isEmpty() ? "工作流未产生输出"
                    : (String) steps.get(steps.size() - 1).get("output");

            // 提取 Supervisor 的路由分析（supervisor 节点的输出）
            String supervisorAnalysis = "";
            for (var s : steps) {
                if ("supervisor".equals(s.get("step"))) {
                    supervisorAnalysis = (String) s.get("output");
                    break;
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Subagent 工作流处理完成，路由到: {}，共 {} 个步骤，耗时 {}ms", routedTo, steps.size(), elapsed);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("answer", finalAnswer);
            result.put("steps", steps);
            result.put("routedTo", routedTo);
            result.put("routedLabel", routedLabel);
            result.put("routedIcon", routedIcon);
            result.put("supervisorAnalysis", supervisorAnalysis);
            return result;

        } catch (Exception e) {
            log.error("Subagent 工作流执行失败", e);
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
