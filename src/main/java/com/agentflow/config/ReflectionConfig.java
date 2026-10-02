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
 * LangGraph4j Reflection（反思迭代）工作流配�? *
 * 工作流结构（生成 �?反�?�?修正循环，直到质量达标）�? *   START �?generate（初稿生成）
 *          �?reflect（反思评审）
 *          �?条件路由：质量达�?�?final_output / 需要改�?�?revise
 *          �?revise（修正改进）�?reflect（循环反思）
 *          �?final_output（最终输出）
 *          �?END
 *
 * generate 节点根据用户请求生成初始回答草稿�? * reflect 节点对草稿进行批判性评审并给出改进意见�? * revise 节点根据评审意见修正草稿�? * 通过条件边实现反�?修正循环，直到输出质量达标，
 * 展示 LangGraph4j 自我反思与迭代优化能力
 */
@Configuration
public class ReflectionConfig {

    private static final Logger log = LoggerFactory.getLogger(ReflectionConfig.class);

    /**
     * 创建 LangGraph4j Reflection 反思迭代工作流
     *
     * 工作流包含四个节点：
     * 1. generate（初稿生成）：根据用户请求生成初始回答草�?     * 2. reflect（反思评审）：对草稿进行批判性评审，指出不足并给出改进建�?     * 3. revise（修正改进）：根据评审意见修正草�?     * 4. final_output（最终输出）：打磨最终回�?     *
     * @param chatModel DeepSeek ChatModel 实例（复用聊天机器人 2.0 的配置）
     * @return 编译后的 Reflection 工作流图
     */
    @Bean
    public CompiledGraph<MessagesState<Object>> reflectionWorkflow(
            @Qualifier("chatbotV2ChatModel") ChatModel chatModel) {

        try {
            var serializer = new LC4jJacksonStateSerializer<>(MessagesState::new);
            var graph = new MessagesStateGraph<>(serializer)
                    // 节点 1：Generate - 根据用户请求生成初始回答草稿
                    .addNode("generate", node_async(state -> {
                        var messages = state.messages();
                        String userRequest = extractLastUserMessage(messages);
                        log.info("Reflection 节点 [初稿生成] 开始处�? {}", userRequest);

                        String prompt = """
                                你是一个内容创作专家。请针对用户的请求撰写一份高质量的回答�?
                                要求�?                                1. 内容准确、结构清晰、逻辑严谨
                                2. 覆盖用户请求的核心要点，不遗漏关键信�?                                3. 使用简洁易懂的表达方式
                                4. 适当使用分点列举，增强可读�?                                5. 不要使用 Markdown 格式，使用纯文本

                                用户请求�?s
                                """.formatted(userRequest);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String draft = chatResponse.aiMessage().text();
                        log.info("Reflection 节点 [初稿生成] 完成，草稿长�? {}", draft.length());
                        return Map.<String, Object>of("messages", AiMessage.from(draft));
                    }))
                    // 节点 2：Reflect - 对草稿进行批判性评�?                    .addNode("reflect", node_async(state -> {
                        var messages = state.messages();
                        String userRequest = extractLastUserMessage(messages);
                        String currentDraft = extractLastAiMessage(messages);
                        log.info("Reflection 节点 [反思评审] 开始评审，草稿长度: {}", currentDraft.length());

                        String prompt = """
                                你是一个严格的内容质量评审专家。请对以下回答草稿进行批判性评审�?
                                用户原始请求�?s

                                当前回答草稿�?                                %s

                                请从以下维度进行评审�?                                1. 完整性：是否覆盖了用户请求的所有要点？
                                2. 准确性：内容是否准确无误�?                                3. 清晰度：表达是否清晰易懂�?                                4. 深度：分析是否足够深入？
                                5. 实用性：建议是否具体可操作？

                                评审输出格式�?                                【优点�?                                - 列出草稿做得好的方面

                                【不足�?                                - 列出需要改进的具体问题

                                【改进建议�?                                - 给出具体、可操作的改进建�?
                                【质量判定�?                                如果草稿质量已经足够好，请输出：【质量达标�?                                如果仍需改进，请输出：【需要改进�?                                """.formatted(userRequest, currentDraft);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String reflection = chatResponse.aiMessage().text();
                        log.info("Reflection 节点 [反思评审] 完成，评审长�? {}", reflection.length());
                        return Map.<String, Object>of("messages", AiMessage.from(reflection));
                    }))
                    // 节点 3：Revise - 根据评审意见修正草稿
                    .addNode("revise", node_async(state -> {
                        var messages = state.messages();
                        String userRequest = extractLastUserMessage(messages);
                        String reflection = extractLastAiMessage(messages);
                        log.info("Reflection 节点 [修正改进] 开始修�?);

                        String prompt = """
                                你是一个内容改进专家。请根据评审意见对回答草稿进行修正和改进�?
                                用户原始请求�?s

                                评审意见�?                                %s

                                修正要求�?                                1. 针对评审中提出的每个不足逐一改进
                                2. 采纳合理的改进建�?                                3. 保留草稿中的优点，不要丢失已有的好内�?                                4. 确保修正后的内容比原草稿有明显提�?                                5. 不要使用 Markdown 格式，使用纯文本

                                请输出修正后的完整回答：
                                """.formatted(userRequest, reflection);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String revised = chatResponse.aiMessage().text();
                        log.info("Reflection 节点 [修正改进] 完成，修正后长度: {}", revised.length());
                        return Map.<String, Object>of("messages", AiMessage.from(revised));
                    }))
                    // 节点 4：Final Output - 打磨最终回�?                    .addNode("final_output", node_async(state -> {
                        var messages = state.messages();
                        String userRequest = extractLastUserMessage(messages);
                        String currentDraft = extractLastAiMessage(messages);
                        log.info("Reflection 节点 [最终输出] 开始生成最终回�?);

                        String prompt = """
                                请对以下经过多轮反思修正的回答进行最终打磨，输出一个完整、清晰、友好的最终回答�?
                                用户原始请求�?s

                                当前回答（已经过反思迭代优化）�?                                %s

                                要求�?                                1. 确保回答完整且逻辑清晰
                                2. 语言流畅自然，语气友�?                                3. 不要使用 Markdown 格式，使用纯文本
                                4. 直接输出最终回答，不要添加额外说明
                                """.formatted(userRequest, currentDraft);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String finalAnswer = chatResponse.aiMessage().text();
                        log.info("Reflection 节点 [最终输出] 完成，最终回答长�? {}", finalAnswer.length());
                        return Map.<String, Object>of("messages", AiMessage.from(finalAnswer));
                    }))
                    // 定义�?                    .addEdge(START, "generate")
                    .addEdge("generate", "reflect")
                    // Reflect 通过条件边动态路由：质量达标→最终输出，需要改进→修正
                    .addConditionalEdges("reflect",
                            edge_async((MessagesState<Object> state) -> {
                                String route = parseReflectRoute(state);
                                log.info("Reflection 条件边路由决策结�? {}", route);
                                return route;
                            }),
                            Map.of(
                                    "revise", "revise",
                                    "done", "final_output"
                            ))
                    // Revise 修正后回�?Reflect 继续评审（形成反思循环）
                    .addEdge("revise", "reflect")
                    .addEdge("final_output", END);

            var compiledGraph = graph.compile();
            log.info("LangGraph4j Reflection 工作流构建成�? generate �?reflect �?[revise|done] �?revise �?reflect（循环）�?final_output");
            return compiledGraph;

        } catch (GraphStateException e) {
            log.error("构建 LangGraph4j Reflection 工作流失�?, e);
            throw new RuntimeException("构建 Reflection 工作流失�?, e);
        }
    }

    /**
     * �?Reflect 节点的输出中解析路由目标
     * 如果包含"需要改�?则进入修正循环，否则完成
     */
    private static String parseReflectRoute(MessagesState<Object> state) {
        try {
            var messages = state.messages();
            for (int i = messages.size() - 1; i >= 0; i--) {
                Object msg = messages.get(i);
                if (msg instanceof AiMessage am) {
                    String text = am.text();
                    if (text.contains("需要改�?)) {
                        return "revise";
                    }
                    if (text.contains("质量达标")) {
                        return "done";
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析 Reflect 路由失败，使用默认路�?done", e);
        }
        return "done"; // 默认达标，避免无限循�?    }

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
