package com.agentflow.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.langchain4j.serializer.jackson.LC4jJacksonStateSerializer;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * LangGraph4j Fan-Out / Fan-In 并发�?Agent 工作流配�? *
 * 工作流结构（任务分解 �?�?Agent 并发执行 �?归总汇总）�? *   START �?task_planner（任务分解，拆分为多个子任务�? *          �?【并发执行】多个专�?Agent 同时处理各自子任务（Java CompletableFuture�? *          �?aggregator（归总汇总所�?Agent 结果�? *          �?END
 *
 * 区别于现有模式：
 * - Subagent（Supervisor 路由）：只选一个专家处理，单线�? * - MultiAgent（圆桌讨论）：多个专家依次串行发言
 * - FanOut（并发执行）：多�?Agent 同时并发执行不同子任务，最后归�? *
 * task_planner 节点通过 LangGraph4j 图执行，
 * 并发 Agent 执行�?Service 层通过 CompletableFuture 实现真正并发�? * aggregator 节点通过 LangGraph4j 图执行，
 * 展示 Fan-Out / Fan-In 并行编排模式
 */
@Configuration
public class FanOutConfig {

    private static final Logger log = LoggerFactory.getLogger(FanOutConfig.class);

    /**
     * 创建 LangGraph4j Fan-Out / Fan-In 并发工作�?     *
     * 工作流包含两�?LangGraph4j 节点�?     * 1. task_planner（任务分解）：将用户任务拆解为多个子任务，分配给不同专家
     * 2. aggregator（归总汇总）：将所有并�?Agent 的输出综合为最终报�?     *
     * 中间的并�?Agent 执行�?Service 层通过 CompletableFuture 实现
     *
     * @param chatModel DeepSeek ChatModel 实例（复用聊天机器人 2.0 的配置）
     * @return 编译后的 Fan-Out 工作流图
     */
    @Bean
    public CompiledGraph<MessagesState<Object>> fanOutWorkflow(
            @Qualifier("chatbotV2ChatModel") ChatModel chatModel) {

        try {
            var serializer = new LC4jJacksonStateSerializer<>(MessagesState::new);
            var graph = new MessagesStateGraph<>(serializer)
                    // 节点 1：任务分�?- 将用户任务拆解为多个子任�?                    .addNode("task_planner", node_async(state -> {
                        var messages = state.messages();
                        String userTask = extractLastUserMessage(messages);
                        log.info("FanOut 节点 [任务分解] 开始分解任�? {}", userTask);

                        String prompt = """
                                你是一个任务分解专家。请将用户的任务拆解�?4 个子任务，分别交给以�?4 位专家并发执行：

                                1. 🔍 调研分析师：负责信息收集、背景调研、数据分�?                                2. 💡 创意策划：负责创意方案、创新思路、差异化策略
                                3. ⚙️ 技术顾问：负责技术可行性、实现方案、技术风险评�?                                4. 🎯 执行专家：负责落地计划、执行步骤、时间节点、资源分�?
                                用户任务�?s

                                请严格按以下 JSON 格式输出（不要输出其他内容）�?                                {
                                  "subtasks": [
                                    {"agent": "调研分析�?, "task": "具体子任务描�?},
                                    {"agent": "创意策划", "task": "具体子任务描�?},
                                    {"agent": "技术顾�?, "task": "具体子任务描�?},
                                    {"agent": "执行专家", "task": "具体子任务描�?}
                                  ]
                                }
                                """.formatted(userTask);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String responseText = chatResponse.aiMessage().text();
                        log.info("FanOut 节点 [任务分解] 完成，分解结果长�? {}", responseText.length());
                        return Map.<String, Object>of("messages", AiMessage.from(responseText));
                    }))
                    // 节点 2：归总汇�?- 综合所有并�?Agent 的结�?                    .addNode("aggregator", node_async(state -> {
                        var messages = state.messages();
                        String userTask = extractLastUserMessage(messages);
                        // 获取并发 Agent 的汇总结果（最后一�?AiMessage�?                        String parallelResults = extractLastAiMessage(messages);
                        log.info("FanOut 节点 [归总汇总] 开始归总，结果长度: {}", parallelResults.length());

                        String prompt = """
                                你是一个高级分析师和报告撰写专家。以下是 4 位专家并发完成各自子任务后的结果汇总�?
                                用户原始任务�?s

                                各专家子任务执行结果�?                                %s

                                请综合所有专家的输出，撰写一份完整的最终报告，要求�?                                1. 综合概述：用 2-3 句话总结整体结论
                                2. 各专家要点提炼：每位专家的核心观点各�?1-2 句话概括
                                3. 交叉分析：不同专家观点之间的关联、互补或矛盾
                                4. 综合建议：基于所有专家输出给出最终行动建�?                                5. 风险提示：需要注意的潜在问题

                                要求：结构清晰，专业严谨，具有可操作性�?                                """.formatted(userTask, parallelResults);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String responseText = chatResponse.aiMessage().text();
                        log.info("FanOut 节点 [归总汇总] 完成，报告长�? {}", responseText.length());
                        return Map.<String, Object>of("messages", AiMessage.from(responseText));
                    }))
                    // 定义边：task_planner �?aggregator（中间并发现�?Service 层处理）
                    .addEdge(START, "task_planner")
                    .addEdge("task_planner", "aggregator")
                    .addEdge("aggregator", END);

            var compiledGraph = graph.compile();
            log.info("LangGraph4j Fan-Out 并发工作流构建成�? task_planner �?[并发 Agents] �?aggregator");
            return compiledGraph;

        } catch (GraphStateException e) {
            log.error("构建 LangGraph4j Fan-Out 工作流失�?, e);
            throw new RuntimeException("构建 Fan-Out 工作流失�?, e);
        }
    }

    /**
     * 从消息列表中提取最后一条用户消息内�?     */
    private String extractLastUserMessage(List<?> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Object msg = messages.get(i);
            if (msg instanceof UserMessage um) {
                return um.singleText();
            }
        }
        return messages.isEmpty() ? "" : messages.get(messages.size() - 1).toString();
    }

    /**
     * 从消息列表中提取最后一�?AiMessage 的文本内�?     */
    private String extractLastAiMessage(List<?> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Object msg = messages.get(i);
            if (msg instanceof AiMessage am) {
                return am.text();
            }
        }
        return "";
    }
}
