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
 * LangGraph4j Multi Agent 圆桌讨论工作流配置：多专家协作模�? *
 * 工作流结构（多个专家角色依次对同一话题发表观点，最后综合形成共识）�? *   START �?topic_analysis（话题分析）
 *          �?product_expert（产品经理视角）
 *          �?architect_expert（架构师视角�? *          �?developer_expert（开发者视角）
 *          �?tester_expert（测试工程师视角�? *          �?consensus（综合共识）
 *          �?END
 *
 * 区别�?Supervisor 路由模式（只选一个专家处理）�? * 本模式让多个专家从各自专业角度分析同一话题，展示多视角协作与聚合能�? */
@Configuration
public class MultiAgentConfig {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentConfig.class);

    /**
     * 创建 LangGraph4j Multi Agent 圆桌讨论工作�?     *
     * 工作流包含六个节点：
     * 1. topic_analysis（话题分析）：提取话题关键要素，为各专家提供分析框架
     * 2. product_expert（产品经理）：从产品需求、用户体验、业务价值角度分�?     * 3. architect_expert（架构师）：从技术架构、系统设计、可扩展性角度分�?     * 4. developer_expert（开发者）：从实现方案、代码质量、开发效率角度分�?     * 5. tester_expert（测试工程师）：从质量保障、测试策略、风险识别角度分�?     * 6. consensus（综合共识）：汇总各专家观点，形成统一建议和行动方�?     *
     * @param chatModel DeepSeek ChatModel 实例（复用聊天机器人 2.0 的配置）
     * @return 编译后的 Multi Agent 圆桌讨论工作流图
     */
    @Bean
    public CompiledGraph<MessagesState<Object>> multiAgentWorkflow(
            @Qualifier("chatbotV2ChatModel") ChatModel chatModel) {

        try {
            var serializer = new LC4jJacksonStateSerializer<>(MessagesState::new);
            var graph = new MessagesStateGraph<>(serializer)
                    // 节点 1：话题分�?- 提取关键要素，构建分析框�?                    .addNode("topic_analysis", node_async(state -> {
                        var messages = state.messages();
                        String userTopic = extractLastUserMessage(messages);
                        log.info("Multi Agent 节点 [话题分析] 开始分析话�? {}", userTopic);

                        String prompt = """
                                你是一个话题分析专家。请分析以下话题/问题，提取关键要素和分析维度�?
                                话题�?s

                                请按以下格式输出�?                                1. 核心问题：（一句话概括�?                                2. 关键要素：（列出 3-5 个关键点�?                                3. 分析维度：（产品、技术、实现、质量）

                                要求：简洁明了，为后续各专家讨论提供框架�?                                """.formatted(userTopic);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String responseText = chatResponse.aiMessage().text();
                        log.info("Multi Agent 节点 [话题分析] 完成，分析长�? {}", responseText.length());
                        return Map.<String, Object>of("messages", AiMessage.from(responseText));
                    }))
                    // 节点 2：产品经理视�?                    .addNode("product_expert", node_async(state -> {
                        var messages = state.messages();
                        String userTopic = extractLastUserMessage(messages);
                        String topicAnalysis = extractLastAiMessage(messages);
                        log.info("Multi Agent 节点 [产品经理] 开始分�?);

                        String prompt = """
                                你是一位资深产品经理，擅长从用户需求、业务价值、用户体验角度分析问题�?
                                话题�?s

                                话题分析框架�?                                %s

                                请从产品经理的角度发表你的观点，包括�?                                1. 用户需求分析：目标用户是谁？核心痛点是什么？
                                2. 业务价值评估：能带来什么商业价值？
                                3. 用户体验建议：如何提升用户体验？
                                4. 优先级建议：哪些功能应该优先做？

                                要求：观点明确，有理有据�?00-300字�?                                """.formatted(userTopic, topicAnalysis);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String responseText = chatResponse.aiMessage().text();
                        log.info("Multi Agent 节点 [产品经理] 完成，观点长�? {}", responseText.length());
                        return Map.<String, Object>of("messages", AiMessage.from(responseText));
                    }))
                    // 节点 3：架构师视角
                    .addNode("architect_expert", node_async(state -> {
                        var messages = state.messages();
                        String userTopic = extractLastUserMessage(messages);
                        String topicAnalysis = extractNthLastAiMessage(messages, 2);
                        String productView = extractLastAiMessage(messages);
                        log.info("Multi Agent 节点 [架构师] 开始分�?);

                        String prompt = """
                                你是一位资深软件架构师，擅长系统设计、技术选型、架构规划�?
                                话题�?s

                                话题分析框架�?                                %s

                                产品经理的观点：
                                %s

                                请从架构师的角度发表你的观点，包括：
                                1. 技术架构建议：推荐什么技术方案？为什么？
                                2. 系统设计要点：核心模块如何划分？
                                3. 可扩展性考虑：如何保证系统可扩展�?                                4. 对产品经理观点的回应：技术可行性如何？有无补充�?
                                要求：观点明确，技术导向，200-300字�?                                """.formatted(userTopic, topicAnalysis, productView);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String responseText = chatResponse.aiMessage().text();
                        log.info("Multi Agent 节点 [架构师] 完成，观点长�? {}", responseText.length());
                        return Map.<String, Object>of("messages", AiMessage.from(responseText));
                    }))
                    // 节点 4：开发者视�?                    .addNode("developer_expert", node_async(state -> {
                        var messages = state.messages();
                        String userTopic = extractLastUserMessage(messages);
                        String architectView = extractLastAiMessage(messages);
                        log.info("Multi Agent 节点 [开发者] 开始分�?);

                        String prompt = """
                                你是一位资深软件开发者，擅长代码实现、性能优化、工程实践�?
                                话题�?s

                                架构师的观点�?                                %s

                                请从开发者的角度发表你的观点，包括：
                                1. 实现方案：具体怎么实现？关键代码思路�?                                2. 开发效率：如何快速交付？
                                3. 代码质量：如何保证代码可维护�?                                4. 对架构师观点的回应：实现上有无挑战？建议�?
                                要求：观点务实，注重落地�?00-300字�?                                """.formatted(userTopic, architectView);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String responseText = chatResponse.aiMessage().text();
                        log.info("Multi Agent 节点 [开发者] 完成，观点长�? {}", responseText.length());
                        return Map.<String, Object>of("messages", AiMessage.from(responseText));
                    }))
                    // 节点 5：测试工程师视角
                    .addNode("tester_expert", node_async(state -> {
                        var messages = state.messages();
                        String userTopic = extractLastUserMessage(messages);
                        String developerView = extractLastAiMessage(messages);
                        log.info("Multi Agent 节点 [测试工程师] 开始分�?);

                        String prompt = """
                                你是一位资深测试工程师，擅长质量保障、测试策略、风险识别�?
                                话题�?s

                                开发者的观点�?                                %s

                                请从测试工程师的角度发表你的观点，包括：
                                1. 测试策略：需要哪些类型的测试�?                                2. 质量风险：可能有哪些质量风险�?                                3. 验收标准：如何定�?完成"�?                                4. 对开发者观点的回应：测试上有无挑战？建议？

                                要求：观点严谨，关注质量�?00-300字�?                                """.formatted(userTopic, developerView);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String responseText = chatResponse.aiMessage().text();
                        log.info("Multi Agent 节点 [测试工程师] 完成，观点长�? {}", responseText.length());
                        return Map.<String, Object>of("messages", AiMessage.from(responseText));
                    }))
                    // 节点 6：综合共�?- 汇总各专家观点形成统一建议
                    .addNode("consensus", node_async(state -> {
                        var messages = state.messages();
                        String userTopic = extractLastUserMessage(messages);
                        String testerView = extractLastAiMessage(messages);
                        log.info("Multi Agent 节点 [综合共识] 开始汇�?);

                        String prompt = """
                                你是圆桌讨论的主持人。请根据以下各专家的观点，综合形成最终共识和建议�?
                                话题�?s

                                各专家观点摘要：
                                ...
                                %s

                                请输出最终共识报告，包括�?                                1. 核心结论：（2-3句话总结�?                                2. 各方共识：专家们一致同意的要点
                                3. 行动建议：具体下一步怎么�?                                4. 风险提示：需要注意的问题

                                要求：条理清晰，可操作性强�?00-400字�?                                """.formatted(userTopic, testerView);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String responseText = chatResponse.aiMessage().text();
                        log.info("Multi Agent 节点 [综合共识] 完成，共识长�? {}", responseText.length());
                        return Map.<String, Object>of("messages", AiMessage.from(responseText));
                    }))
                    // 定义边：线性顺序执�?                    .addEdge(START, "topic_analysis")
                    .addEdge("topic_analysis", "product_expert")
                    .addEdge("product_expert", "architect_expert")
                    .addEdge("architect_expert", "developer_expert")
                    .addEdge("developer_expert", "tester_expert")
                    .addEdge("tester_expert", "consensus")
                    .addEdge("consensus", END);

            var compiledGraph = graph.compile();
            log.info("LangGraph4j Multi Agent 圆桌讨论工作流构建成�? topic_analysis �?product �?architect �?developer �?tester �?consensus");
            return compiledGraph;

        } catch (GraphStateException e) {
            log.error("构建 LangGraph4j Multi Agent 工作流失�?, e);
            throw new RuntimeException("构建 Multi Agent 工作流失�?, e);
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

    /**
     * 从消息列表中提取倒数�?N �?AiMessage 的文本内�?     *
     * @param messages 消息列表
     * @param n 倒数第几条（1 表示最后一条）
     * @return AiMessage 文本内容
     */
    private String extractNthLastAiMessage(List<?> messages, int n) {
        int count = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Object msg = messages.get(i);
            if (msg instanceof AiMessage am) {
                count++;
                if (count == n) {
                    return am.text();
                }
            }
        }
        return "";
    }
}
