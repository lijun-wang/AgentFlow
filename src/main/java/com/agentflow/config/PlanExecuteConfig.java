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

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * LangGraph4j Plan-and-Execute 工作流配�? *
 * 工作流结构（先规划后执行，支持动态调整计划）�? *   START �?planner（制定计划）
 *          �?executor（执行计划）
 *          �?replanner（评估结果，决定是否需要调整计划）
 *          �?条件路由：继续执�?/ 生成最终输�? *          �?final_output（综合所有结果生成最终回答）
 *          �?END
 *
 * planner 节点将用户请求分解为可执行的步骤计划�? * executor 节点逐步执行计划�? * replanner 节点评估执行结果并决定是否需要重新规划，
 * 展示 LangGraph4j 动态规划与条件路由能力
 */
@Configuration
public class PlanExecuteConfig {

    private static final Logger log = LoggerFactory.getLogger(PlanExecuteConfig.class);

    /**
     * 创建 LangGraph4j Plan-and-Execute 工作�?     *
     * 工作流包含四个节点：
     * 1. planner（规划器）：将用户请求分解为步骤计划
     * 2. executor（执行器）：逐步执行计划
     * 3. replanner（重规划器）：评估结果，决定是否需要调�?     * 4. final_output（最终输出）：综合所有结果生成最终回�?     *
     * @param chatModel DeepSeek ChatModel 实例（复用聊天机器人 2.0 的配置）
     * @return 编译后的 Plan-and-Execute 工作流图
     */
    @Bean
    public CompiledGraph<MessagesState<Object>> planExecuteWorkflow(
            @Qualifier("chatbotV2ChatModel") ChatModel chatModel) {

        try {
            var serializer = new LC4jJacksonStateSerializer<>(MessagesState::new);
            var graph = new MessagesStateGraph<>(serializer)
                    // 节点 1：Planner - 将用户请求分解为步骤计划
                    .addNode("planner", node_async(state -> {
                        var messages = state.messages();
                        String userRequest = extractLastUserMessage(messages);
                        log.info("Plan-Execute 节点 [Planner] 开始制定计�? {}", userRequest);

                        String prompt = """
                                你是一个任务规划专家。请将用户的请求分解为具体的、可执行的步骤计划�?
                                要求�?                                1. 分析用户请求的核心目�?                                2. 将任务分解为 3-5 个清晰的执行步骤
                                3. 每个步骤应该具体、可操作
                                4. 步骤之间应该有逻辑顺序
                                5. 按以下格式输出：

                                【计划�?                                1. 步骤一：具体描�?                                2. 步骤二：具体描述
                                3. 步骤三：具体描述
                                ...

                                用户请求�?s
                                """.formatted(userRequest);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String planText = chatResponse.aiMessage().text();
                        log.info("Plan-Execute 节点 [Planner] 完成计划制定，计划长�? {}", planText.length());
                        return Map.<String, Object>of("messages", AiMessage.from(planText));
                    }))
                    // 节点 2：Executor - 执行计划
                    .addNode("executor", node_async(state -> {
                        var messages = state.messages();
                        String userRequest = extractLastUserMessage(messages);
                        String plan = extractLastAiMessage(messages);
                        log.info("Plan-Execute 节点 [Executor] 开始执行计�?);

                        String prompt = """
                                你是一个任务执行专家。请根据以下计划逐步执行任务�?
                                用户原始请求�?s

                                执行计划�?                                %s

                                请按照计划中的每个步骤依次执行，并为每个步骤提供详细的执行结果�?                                按以下格式输出：

                                【执行结果�?                                步骤 1 执行结果�?..
                                步骤 2 执行结果�?..
                                步骤 3 执行结果�?..
                                ...
                                """.formatted(userRequest, plan);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String executionResult = chatResponse.aiMessage().text();
                        log.info("Plan-Execute 节点 [Executor] 完成执行，结果长�? {}", executionResult.length());
                        return Map.<String, Object>of("messages", AiMessage.from(executionResult));
                    }))
                    // 节点 3：Replanner - 评估结果并决定是否需要重新规�?                    .addNode("replanner", node_async(state -> {
                        var messages = state.messages();
                        String userRequest = extractLastUserMessage(messages);
                        String executionResult = extractLastAiMessage(messages);
                        log.info("Plan-Execute 节点 [Replanner] 开始评估执行结�?);

                        String prompt = """
                                你是一个任务评估专家。请评估以下执行结果是否充分回答了用户的请求�?
                                用户原始请求�?s

                                执行结果�?                                %s

                                请判断：
                                1. 执行结果是否完整回答了用户请求？
                                2. 是否需要补充额外的步骤�?                                3. 是否需要调整现有步骤？

                                如果执行结果已经充分回答了用户请求，请输出：
                                【完成�?
                                如果需要补充或调整，请输出�?                                【需要补充�?                                补充步骤�?                                1. ...
                                2. ...
                                """.formatted(userRequest, executionResult);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String replanResult = chatResponse.aiMessage().text();
                        log.info("Plan-Execute 节点 [Replanner] 完成评估，结果长�? {}", replanResult.length());
                        return Map.<String, Object>of("messages", AiMessage.from(replanResult));
                    }))
                    // 节点 4：Final Output - 综合所有结果生成最终回�?                    .addNode("final_output", node_async(state -> {
                        var messages = state.messages();
                        String userRequest = extractLastUserMessage(messages);
                        String replanResult = extractLastAiMessage(messages);
                        log.info("Plan-Execute 节点 [Final Output] 开始生成最终回�?);

                        String prompt = """
                                请综合以上所有执行结果和评估意见，为用户请求生成一个完整、清晰、友好的最终回答�?
                                用户原始请求�?s

                                要求�?                                1. 整合所有执行步骤的结果
                                2. 确保回答完整且逻辑清晰
                                3. 使用友好的语�?                                4. 不要使用 Markdown 格式，使用纯文本
                                """.formatted(userRequest);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String finalAnswer = chatResponse.aiMessage().text();
                        log.info("Plan-Execute 节点 [Final Output] 完成，回答长�? {}", finalAnswer.length());
                        return Map.<String, Object>of("messages", AiMessage.from(finalAnswer));
                    }))
                    // 定义�?                    .addEdge(START, "planner")
                    .addEdge("planner", "executor")
                    .addEdge("executor", "replanner")
                    // Replanner 通过条件边动态路由：继续执行或生成最终输�?                    .addConditionalEdges("replanner",
                            edge_async((MessagesState<Object> state) -> {
                                String route = parseReplanRoute(state);
                                log.info("Plan-Execute 条件边路由决策结�? {}", route);
                                return route;
                            }),
                            Map.of(
                                    "continue", "executor",
                                    "done", "final_output"
                            ))
                    .addEdge("final_output", END);

            var compiledGraph = graph.compile();
            log.info("LangGraph4j Plan-and-Execute 工作流构建成�? planner �?executor �?replanner �?[continue|done] �?final_output");
            return compiledGraph;

        } catch (GraphStateException e) {
            log.error("构建 LangGraph4j Plan-and-Execute 工作流失�?, e);
            throw new RuntimeException("构建 Plan-and-Execute 工作流失�?, e);
        }
    }

    /**
     * �?Replanner 的输出中解析路由目标
     * 如果包含"需要补�?则继续执行，否则完成
     */
    private static String parseReplanRoute(MessagesState<Object> state) {
        try {
            var messages = state.messages();
            for (int i = messages.size() - 1; i >= 0; i--) {
                Object msg = messages.get(i);
                if (msg instanceof AiMessage am) {
                    String text = am.text();
                    if (text.contains("需要补�?)) {
                        return "continue";
                    }
                    if (text.contains("完成")) {
                        return "done";
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析 Replanner 路由失败，使用默认路�?done", e);
        }
        return "done"; // 默认完成
    }

    /**
     * 从消息列表中提取最后一条用户消息内�?     */
    private String extractLastUserMessage(java.util.List<?> messages) {
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
    private String extractLastAiMessage(java.util.List<?> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Object msg = messages.get(i);
            if (msg instanceof AiMessage am) {
                return am.text();
            }
        }
        return "";
    }
}
