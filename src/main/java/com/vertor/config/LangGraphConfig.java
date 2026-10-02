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

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * LangGraph4j 工作流配置：构建知识助手状态图工作流
 *
 * 工作流结构：
 *   START → 问题分析 → 知识生成 → 回答格式化 → END
 *
 * 每个节点调用 DeepSeek 模型完成不同的处理步骤，
 * 通过 LangGraph4j 的 StateGraph 编排多步骤 AI 处理流程
 */
@Configuration
public class LangGraphConfig {

    private static final Logger log = LoggerFactory.getLogger(LangGraphConfig.class);

    /**
     * 创建 LangGraph4j 知识助手工作流
     *
     * 工作流包含三个节点：
     * 1. analyze（问题分析）：分析用户问题类型和关键概念
     * 2. generate（知识生成）：基于分析结果生成知识要点
     * 3. format（回答格式化）：将知识要点格式化为最终回答
     *
     * @param chatModel DeepSeek ChatModel 实例（复用聊天机器人 2.0 的配置）
     * @return 编译后的工作流图
     */
    @Bean
    public CompiledGraph<MessagesState<Object>> knowledgeWorkflow(
            @Qualifier("chatbotV2ChatModel") ChatModel chatModel) {

        try {
            // 使用 Jackson JSON 序列化器替代默认的 Java 序列化器，
            // 解决 langchain4j 消息类（UserMessage/AiMessage）未实现 Serializable 的问题
            var serializer = new LC4jJacksonStateSerializer<>(MessagesState::new);
            var graph = new MessagesStateGraph<>(serializer)
                    // 节点 1：问题分析 - 调用 LLM 分析用户问题
                    .addNode("analyze", node_async(state -> {
                        var messages = state.messages();
                        String userQuestion = extractLastUserMessage(messages);
                        log.info("LangGraph 节点 [问题分析] 开始处理: {}", userQuestion);

                        String prompt = """
                                请分析以下用户问题，判断问题类型（事实查询/概念解释/操作指南/观点分析），并提取关键概念。
                                用 2-3 句话简要回答。
                                
                                用户问题：%s
                                """.formatted(userQuestion);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String responseText = chatResponse.aiMessage().text();
                        log.info("LangGraph 节点 [问题分析] 完成，回答长度: {}", responseText.length());
                        return Map.<String, Object>of("messages", AiMessage.from(responseText));
                    }))
                    // 节点 2：知识生成 - 调用 LLM 基于分析结果生成知识
                    .addNode("generate", node_async(state -> {
                        var messages = state.messages();
                        String userQuestion = extractLastUserMessage(messages);
                        String analysis = extractMessageAt(messages, 1);
                        log.info("LangGraph 节点 [知识生成] 开始处理");

                        String prompt = """
                                基于以下分析结果，为用户提供详细的知识内容。要求：
                                1. 列出 3-5 个关键知识要点
                                2. 每个要点简洁明了
                                3. 内容准确、有条理
                                
                                用户问题：%s
                                分析结果：%s
                                """.formatted(userQuestion, analysis);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String responseText = chatResponse.aiMessage().text();
                        log.info("LangGraph 节点 [知识生成] 完成，回答长度: {}", responseText.length());
                        return Map.<String, Object>of("messages", AiMessage.from(responseText));
                    }))
                    // 节点 3：回答格式化 - 调用 LLM 将知识内容格式化为最终回答
                    .addNode("format", node_async(state -> {
                        var messages = state.messages();
                        String userQuestion = extractLastUserMessage(messages);
                        String knowledge = extractMessageAt(messages, 2);
                        log.info("LangGraph 节点 [回答格式化] 开始处理");

                        String prompt = """
                                请将以下知识内容整理成一个完整、友好的回答。要求：
                                1. 开头简要回应用户问题
                                2. 中间分点列出核心知识
                                3. 结尾给出简短总结
                                4. 语言自然流畅，不要使用 Markdown 格式
                                
                                用户问题：%s
                                知识内容：%s
                                """.formatted(userQuestion, knowledge);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String responseText = chatResponse.aiMessage().text();
                        log.info("LangGraph 节点 [回答格式化] 完成，回答长度: {}", responseText.length());
                        return Map.<String, Object>of("messages", AiMessage.from(responseText));
                    }))
                    // 定义边：START → analyze → generate → format → END
                    .addEdge(START, "analyze")
                    .addEdge("analyze", "generate")
                    .addEdge("generate", "format")
                    .addEdge("format", END);

            var compiledGraph = graph.compile();
            log.info("LangGraph4j 知识助手工作流构建成功: analyze → generate → format");
            return compiledGraph;

        } catch (GraphStateException e) {
            log.error("构建 LangGraph4j 工作流失败", e);
            throw new RuntimeException("构建工作流失败", e);
        }
    }

    /**
     * 从消息列表中提取最后一条用户消息内容
     */
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
     * 从消息列表中提取指定位置的消息文本（跳过初始用户消息）
     * 消息顺序：[0]=用户问题, [1]=analyze输出, [2]=generate输出, [3]=format输出
     */
    private String extractMessageAt(java.util.List<?> messages, int index) {
        if (index < messages.size()) {
            Object msg = messages.get(index);
            if (msg instanceof AiMessage am) {
                return am.text();
            }
            return msg.toString();
        }
        return "";
    }
}
