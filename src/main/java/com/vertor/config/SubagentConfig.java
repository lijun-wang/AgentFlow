package com.vertor.config;

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
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * LangGraph4j Subagent 多 Agent 协作工作流配置：Supervisor 路由模式
 *
 * 工作流结构（先改写查询，再由 Supervisor 根据请求类型动态路由到专家子 Agent）：
 *   START → query_rewrite（DeepSeek 查询改写）
 *          → supervisor（任务分析与路由）
 *          → coding / writing / qa（专家子 Agent）
 *          → summary（汇总格式化）
 *          → END
 *
 * query_rewrite 节点调用 DeepSeek 将用户问题改写为更适合检索的形式，
 * Supervisor 节点调用 LLM 分析请求类型并输出路由标记，
 * 通过 addConditionalEdges 动态路由到对应的专家子 Agent，
 * 展示 LangGraph4j 条件边与多 Agent 协作能力
 */
@Configuration
public class SubagentConfig {

    private static final Logger log = LoggerFactory.getLogger(SubagentConfig.class);

    /**
     * 创建 LangGraph4j Subagent 多 Agent 协作工作流
     *
     * 工作流包含六个节点：
     * 1. query_rewrite（查询改写）：使用 DeepSeek 将用户问题改写为更适合检索的形式
     * 2. supervisor（Supervisor 路由）：分析请求类型，决定路由到哪个专家子 Agent
     * 3. coding（编程助手）：处理编程、代码、技术问题
     * 4. writing（写作助手）：处理文章、文案、翻译等写作任务
     * 5. qa（通用问答）：处理一般性知识问答
     * 6. summary（汇总格式化）：将专家回答整理为最终输出
     *
     * @param chatModel DeepSeek ChatModel 实例（复用聊天机器人 2.0 的配置）
     * @return 编译后的 Subagent 工作流图
     */
    @Bean
    public CompiledGraph<MessagesState<Object>> subagentWorkflow(
            @Qualifier("chatbotV2ChatModel") ChatModel chatModel) {

        try {
            var serializer = new LC4jJacksonStateSerializer<>(MessagesState::new);
            var graph = new MessagesStateGraph<>(serializer)
                    // 节点 1：查询改写 - 使用 DeepSeek 将用户问题改写为更适合检索的形式
                    .addNode("query_rewrite", node_async(state -> {
                        var messages = state.messages();
                        String userRequest = extractLastUserMessage(messages);
                        log.info("Subagent 节点 [查询改写] 开始改写用户问题: {}", userRequest);

                        String rewritePrompt = """
                                你是一个查询改写助手。将用户的问题改写为更适合检索和理解的形式。
                                要求：
                                1. 保留核心语义，去除口语化表达和无关词汇
                                2. 补充隐含的关键概念，使查询更完整
                                3. 如果问题涉及多个方面，拆分为并列的关键词或短语
                                4. 只输出改写后的文本，不要解释，不要加引号

                                用户问题：%s
                                """.formatted(userRequest);

                        var chatResponse = chatModel.chat(UserMessage.from(rewritePrompt));
                        String rewrittenQuery = chatResponse.aiMessage().text();
                        log.info("Subagent 节点 [查询改写] 完成，改写结果: '{}' -> '{}'", userRequest, rewrittenQuery);
                        return Map.<String, Object>of("messages", AiMessage.from(rewrittenQuery));
                    }))
                    // 节点 2：Supervisor - 分析请求并决定路由
                    .addNode("supervisor", node_async(state -> {
                        var messages = state.messages();
                        String userRequest = extractLastUserMessage(messages);
                        // 获取查询改写结果（最近一条 AiMessage）
                        String rewrittenQuery = extractLastAiMessage(messages);
                        log.info("Subagent 节点 [Supervisor] 开始分析请求，原始问题: {}，改写后: {}", userRequest, rewrittenQuery);

                        String prompt = """
                                你是一个任务调度 Supervisor，负责分析用户请求并决定分配给哪个专家子 Agent 处理。

                                可用的专家子 Agent：
                                1. coding - 编程助手：擅长编程、代码、算法、技术问题、Bug 修复、架构设计
                                2. writing - 写作助手：擅长写文章、文案、翻译、润色、创意写作、邮件撰写
                                3. qa - 通用问答：擅长知识问答、概念解释、生活常识、一般性问题

                                请分析以下用户请求（已经过查询改写优化），选择最合适的专家子 Agent，并按以下格式回复：
                                【路由：coding】或【路由：writing】或【路由：qa】
                                路由理由：（简要说明选择理由，1-2句话）

                                用户请求（改写后）：%s
                                """.formatted(rewrittenQuery);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String responseText = chatResponse.aiMessage().text();
                        log.info("Subagent 节点 [Supervisor] 完成路由决策，回答长度: {}", responseText.length());
                        return Map.<String, Object>of("messages", AiMessage.from(responseText));
                    }))
                    // 节点 2：编程助手子 Agent
                    .addNode("coding", node_async(state -> {
                        var messages = state.messages();
                        String userRequest = extractLastUserMessage(messages);
                        String supervisorAnalysis = extractLastAiMessage(messages);
                        log.info("Subagent 节点 [编程助手] 开始处理");

                        String prompt = """
                                你是一个资深编程专家，精通多种编程语言和技术框架。请根据 Supervisor 的分析和用户的原始请求，提供专业、详细的技术回答。

                                Supervisor 的分析：
                                %s

                                用户原始请求：
                                %s

                                请提供：
                                1. 问题分析
                                2. 解决方案（包含代码示例，如适用）
                                3. 最佳实践建议
                                4. 注意事项
                                """.formatted(supervisorAnalysis, userRequest);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String responseText = chatResponse.aiMessage().text();
                        log.info("Subagent 节点 [编程助手] 完成，回答长度: {}", responseText.length());
                        return Map.<String, Object>of("messages", AiMessage.from(responseText));
                    }))
                    // 节点 3：写作助手子 Agent
                    .addNode("writing", node_async(state -> {
                        var messages = state.messages();
                        String userRequest = extractLastUserMessage(messages);
                        String supervisorAnalysis = extractLastAiMessage(messages);
                        log.info("Subagent 节点 [写作助手] 开始处理");

                        String prompt = """
                                你是一个专业写作专家，擅长各类文体写作、文案润色、翻译和创意表达。请根据 Supervisor 的分析和用户的原始请求，提供高质量的写作输出。

                                Supervisor 的分析：
                                %s

                                用户原始请求：
                                %s

                                请提供：
                                1. 对用户需求的理解
                                2. 专业的写作输出
                                3. 写作技巧说明（如有必要）
                                4. 改进建议
                                """.formatted(supervisorAnalysis, userRequest);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String responseText = chatResponse.aiMessage().text();
                        log.info("Subagent 节点 [写作助手] 完成，回答长度: {}", responseText.length());
                        return Map.<String, Object>of("messages", AiMessage.from(responseText));
                    }))
                    // 节点 4：通用问答子 Agent
                    .addNode("qa", node_async(state -> {
                        var messages = state.messages();
                        String userRequest = extractLastUserMessage(messages);
                        String supervisorAnalysis = extractLastAiMessage(messages);
                        log.info("Subagent 节点 [通用问答] 开始处理");

                        String prompt = """
                                你是一个知识渊博的问答专家，能够准确回答各类问题。请根据 Supervisor 的分析和用户的原始请求，提供清晰、准确的回答。

                                Supervisor 的分析：
                                %s

                                用户原始请求：
                                %s

                                请提供：
                                1. 直接回答用户问题
                                2. 相关背景知识补充
                                3. 延伸信息（如有价值）
                                """.formatted(supervisorAnalysis, userRequest);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String responseText = chatResponse.aiMessage().text();
                        log.info("Subagent 节点 [通用问答] 完成，回答长度: {}", responseText.length());
                        return Map.<String, Object>of("messages", AiMessage.from(responseText));
                    }))
                    // 节点 5：汇总格式化
                    .addNode("summary", node_async(state -> {
                        var messages = state.messages();
                        String userRequest = extractLastUserMessage(messages);
                        String expertResponse = extractLastAiMessage(messages);
                        log.info("Subagent 节点 [汇总格式化] 开始处理");

                        String prompt = """
                                请对以下专家回答进行汇总和格式化，生成一个友好、完整的最终回答。

                                用户原始问题：%s

                                专家回答：
                                %s

                                要求：
                                1. 保留专家回答的核心内容和专业性
                                2. 用友好的语气重新组织
                                3. 结构清晰，重点突出
                                4. 不要使用 Markdown 格式，使用纯文本
                                """.formatted(userRequest, expertResponse);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String responseText = chatResponse.aiMessage().text();
                        log.info("Subagent 节点 [汇总格式化] 完成，回答长度: {}", responseText.length());
                        return Map.<String, Object>of("messages", AiMessage.from(responseText));
                    }))
                    // 定义边
                    .addEdge(START, "query_rewrite")
                    // 查询改写后进入 Supervisor 路由
                    .addEdge("query_rewrite", "supervisor")
                    // Supervisor 通过条件边动态路由到专家子 Agent
                    .addConditionalEdges("supervisor",
                            edge_async((MessagesState<Object> state) -> {
                                String route = parseRouteFromSupervisor(state);
                                log.info("Subagent 条件边路由决策结果: {}", route);
                                return route;
                            }),
                            Map.of(
                                    "coding", "coding",
                                    "writing", "writing",
                                    "qa", "qa"
                            ))
                    // 所有专家子 Agent 汇聚到 summary 节点
                    .addEdge("coding", "summary")
                    .addEdge("writing", "summary")
                    .addEdge("qa", "summary")
                    .addEdge("summary", END);

            var compiledGraph = graph.compile();
            log.info("LangGraph4j Subagent 多 Agent 协作工作流构建成功: query_rewrite → supervisor → [coding|writing|qa] → summary");
            return compiledGraph;

        } catch (GraphStateException e) {
            log.error("构建 LangGraph4j Subagent 工作流失败", e);
            throw new RuntimeException("构建 Subagent 工作流失败", e);
        }
    }

    /**
     * 从 Supervisor 的输出中解析路由目标
     * 解析"【路由：xxx】"格式的路由标记
     */
    private static String parseRouteFromSupervisor(MessagesState<Object> state) {
        try {
            List<?> messages = state.messages();
            for (int i = messages.size() - 1; i >= 0; i--) {
                Object msg = messages.get(i);
                if (msg instanceof AiMessage am) {
                    String text = am.text();
                    if (text.contains("coding")) return "coding";
                    if (text.contains("writing")) return "writing";
                    if (text.contains("qa")) return "qa";
                }
            }
        } catch (Exception e) {
            log.warn("解析 Supervisor 路由失败，使用默认路由 qa", e);
        }
        return "qa"; // 默认路由到通用问答
    }

    /**
     * 从消息列表中提取最后一条用户消息内容
     */
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
     * 从消息列表中提取最后一条 AiMessage 的文本内容
     */
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
