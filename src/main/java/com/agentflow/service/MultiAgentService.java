package com.agentflow.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * LangGraph4j Multi Agent 圆桌讨论工作流服�? *
 * 每次请求通过 CompiledGraph.stream() 驱动 Multi Agent 工作流执行，
 * 多个专家角色依次对同一话题发表观点，最后综合形成共识，
 * 收集每个节点的输出作为步骤结果返回给前端展示�? */
@Service
public class MultiAgentService {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentService.class);

    /** 节点名称到中文标签的映射 */
    private static final Map<String, String> STEP_LABELS = Map.of(
            "topic_analysis", "话题分析",
            "product_expert", "产品经理",
            "architect_expert", "架构�?,
            "developer_expert", "开发�?,
            "tester_expert", "测试工程�?,
            "consensus", "综合共识"
    );

    /** 节点名称到专家图标的映射 */
    private static final Map<String, String> STEP_ICONS = Map.of(
            "topic_analysis", "🔍",
            "product_expert", "📊",
            "architect_expert", "🏗�?,
            "developer_expert", "💻",
            "tester_expert", "🧪",
            "consensus", "🤝"
    );

    /** 专家节点集合（不含话题分析和综合共识�?*/
    private static final Set<String> EXPERT_NODES = Set.of(
            "product_expert", "architect_expert", "developer_expert", "tester_expert"
    );

    private final CompiledGraph<MessagesState<Object>> multiAgentWorkflow;

    public MultiAgentService(CompiledGraph<MessagesState<Object>> multiAgentWorkflow) {
        this.multiAgentWorkflow = multiAgentWorkflow;
        log.info("MultiAgentService 初始化完成，圆桌讨论工作流已就绪");
    }

    /**
     * 处理用户话题，执�?LangGraph4j Multi Agent 圆桌讨论工作�?     *
     * @param userTopic 用户输入的话�?问题
     * @return 包含最终共识、各专家观点和步骤执行结果的结构化响�?     */
    public Map<String, Object> process(String userTopic) {
        log.info("Multi Agent 圆桌讨论开始处理话�? {}", userTopic);
        long startTime = System.currentTimeMillis();

        try {
            // 构建初始状态：将用户话题作�?UserMessage 放入消息列表
            Map<String, Object> inputs = Map.of(
                    "messages", UserMessage.from(userTopic)
            );

            // 执行工作流，收集每个节点的输�?            List<Map<String, Object>> steps = new ArrayList<>();
            List<String> expertOpinions = new ArrayList<>();

            for (var step : multiAgentWorkflow.stream(inputs)) {
                String nodeName = step.node();
                String stepLabel = STEP_LABELS.getOrDefault(nodeName, nodeName);
                String stepIcon = STEP_ICONS.getOrDefault(nodeName, "⚙️");

                // 从节点输出状态中提取最新消息作为该步骤的输�?                String stepOutput = extractLastAiMessage(step.state());

                // 如果是专家节点，收集其观�?                if (EXPERT_NODES.contains(nodeName)) {
                    expertOpinions.add(stepLabel + "�? + stepOutput);
                }

                steps.add(Map.of(
                        "step", nodeName,
                        "label", stepLabel,
                        "icon", stepIcon,
                        "output", stepOutput,
                        "status", "completed",
                        "isExpert", EXPERT_NODES.contains(nodeName)
                ));

                log.info("Multi Agent 节点 [{}] 执行完成", stepLabel);
            }

            // 最终共识来自最后一个节点（consensus）的输出
            String finalConsensus = steps.isEmpty() ? "工作流未产生输出"
                    : (String) steps.get(steps.size() - 1).get("output");

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Multi Agent 圆桌讨论完成，共 {} 个步骤，耗时 {}ms", steps.size(), elapsed);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("consensus", finalConsensus);
            result.put("steps", steps);
            result.put("expertCount", expertOpinions.size());
            result.put("expertOpinions", expertOpinions);
            return result;

        } catch (Exception e) {
            log.error("Multi Agent 圆桌讨论执行失败", e);
            return Map.of(
                    "success", false,
                    "message", "工作流执行失�? " + e.getMessage()
            );
        }
    }

    /**
     * �?MessagesState 中提取最后一�?AiMessage 的文本内�?     */
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
