package com.agentflow.config;

import com.agentflow.tool.GeocodingTool;
import com.agentflow.tool.HolidayTool;
import com.agentflow.tool.WeatherTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * LangGraph4j Agent Loop 工作流配置：展示智能体循环调用工具的模式
 *
 * 工作流结构（Agent 循环调用工具直到获得最终答案）�? *   START �?agent（LLM 推理并决定是否调用工具）
 *          �?条件边（检查是否有工具调用�? *          �?tools（执行工具）�?回到 agent（循环）
 *          �?�?�?END（无工具调用时结束）
 *
 * 展示 LangGraph4j 的条件边与循环能力，实现 ReAct 模式的智能体
 */
@Configuration
public class AgentLoopConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopConfig.class);

    /**
     * 创建 LangGraph4j Agent Loop 工作�?     *
     * 工作流包含两个节点：
     * 1. agent（智能体推理）：调用 LLM 推理，决定是否使用工�?     * 2. tools（工具执行）：执�?LLM 请求的工具，将结果返回给 agent
     *
     * @param chatModel     DeepSeek ChatModel 实例
     * @param weatherTool   天气查询工具
     * @param geocodingTool 经纬度查询工�?     * @param holidayTool   节假日查询工�?     * @return 编译后的 Agent Loop 工作流图
     */
    @Bean
    public CompiledGraph<MessagesState<Object>> agentLoopWorkflow(
            @Qualifier("chatbotV2ChatModel") ChatModel chatModel,
            WeatherTool weatherTool,
            GeocodingTool geocodingTool,
            HolidayTool holidayTool) {

        try {
            // 提取工具规格，用于传递给 LLM（每个工具单独提取后合并�?            List<ToolSpecification> toolSpecs = new ArrayList<>();
            toolSpecs.addAll(ToolSpecifications.toolSpecificationsFrom(weatherTool));
            toolSpecs.addAll(ToolSpecifications.toolSpecificationsFrom(geocodingTool));
            toolSpecs.addAll(ToolSpecifications.toolSpecificationsFrom(holidayTool));

            var serializer = new LC4jJacksonStateSerializer<>(MessagesState::new);
            var graph = new MessagesStateGraph<>(serializer)
                    // 节点 1：Agent 推理 - 调用 LLM 决定是否使用工具
                    .addNode("agent", node_async(state -> {
                        var messages = state.messages();
                        log.info("Agent Loop 节点 [Agent] 开始推理，当前消息�? {}", messages.size());

                        // 构建系统提示词，描述可用工具
                        String systemPrompt = """
                                你是一个智能助手，可以使用以下工具来回答用户问题：

                                1. queryWeather(city: String) - 查询指定城市的天气信�?                                2. queryCityCoordinates(city: String) - 查询指定城市的经纬度坐标
                                3. queryHoliday(year: int) - 查询指定年份的中国法定节假日

                                使用规则�?                                - 如果需要查询实时信息（天气、节假日等），请调用相应工具
                                - 可以组合使用多个工具（如先查经纬度再查天气）
                                - 如果不需要工具就能回答，直接给出答案
                                - 每次只调用必要的工具

                                用户问题�?                                """;

                        // 构建消息列表，添加系统提�?                        List<ChatMessage> allMessages = new ArrayList<>();
                        allMessages.add(UserMessage.from(systemPrompt));
                        for (Object msg : messages) {
                            if (msg instanceof ChatMessage cm) {
                                allMessages.add(cm);
                            }
                        }

                        // 调用 LLM，通过 ChatRequest 传入工具规格
                        ChatRequest chatRequest = ChatRequest.builder()
                                .messages(allMessages)
                                .toolSpecifications(toolSpecs)
                                .build();
                        var chatResponse = chatModel.chat(chatRequest);
                        AiMessage aiMessage = chatResponse.aiMessage();

                        // 检查是否有工具调用
                        boolean hasToolCalls = aiMessage.toolExecutionRequests() != null
                                && !aiMessage.toolExecutionRequests().isEmpty();

                        if (hasToolCalls) {
                            log.info("Agent Loop 节点 [Agent] 决定调用工具，工具调用数: {}", aiMessage.toolExecutionRequests().size());
                        } else {
                            log.info("Agent Loop 节点 [Agent] 给出最终答案，回答长度: {}", aiMessage.text().length());
                        }

                        return Map.<String, Object>of("messages", aiMessage);
                    }))
                    // 节点 2：工具执�?- 执行 LLM 请求的工�?                    .addNode("tools", node_async(state -> {
                        var messages = state.messages();
                        log.info("Agent Loop 节点 [Tools] 开始执行工�?);

                        // 从最后一�?AiMessage 中提取工具调用请�?                        AiMessage lastAiMessage = null;
                        for (int i = messages.size() - 1; i >= 0; i--) {
                            if (messages.get(i) instanceof AiMessage am) {
                                lastAiMessage = am;
                                break;
                            }
                        }

                        if (lastAiMessage == null || lastAiMessage.toolExecutionRequests() == null
                                || lastAiMessage.toolExecutionRequests().isEmpty()) {
                            log.warn("Agent Loop 节点 [Tools] 未找到工具调用请�?);
                            return Map.<String, Object>of();
                        }

                        // 执行每个工具调用
                        List<ToolExecutionResultMessage> toolResults = new ArrayList<>();
                        for (var request : lastAiMessage.toolExecutionRequests()) {
                            String toolName = request.name();
                            String arguments = request.arguments();
                            log.info("Agent Loop 节点 [Tools] 执行工具: {}，参�? {}", toolName, arguments);

                            String result = executeTool(toolName, arguments, weatherTool, geocodingTool, holidayTool);
                            log.info("Agent Loop 节点 [Tools] 工具 {} 执行完成，结果长�? {}", toolName, result.length());

                            toolResults.add(ToolExecutionResultMessage.from(request, result));
                        }

                        // 返回工具执行结果消息列表
                        return Map.<String, Object>of("messages", toolResults);
                    }))
                    // 定义�?                    .addEdge(START, "agent")
                    // Agent 后通过条件边判断：有工具调用则进入 tools，否则结�?                    .addConditionalEdges("agent",
                            edge_async((MessagesState<Object> state) -> {
                                var messages = state.messages();
                                // 检查最后一�?AiMessage 是否有工具调�?                                for (int i = messages.size() - 1; i >= 0; i--) {
                                    if (messages.get(i) instanceof AiMessage am) {
                                        boolean hasToolCalls = am.toolExecutionRequests() != null
                                                && !am.toolExecutionRequests().isEmpty();
                                        log.info("Agent Loop 条件边判断：{}工具调用", hasToolCalls ? "�? : "�?);
                                        return hasToolCalls ? "continue" : "end";
                                    }
                                }
                                return "end";
                            }),
                            Map.of(
                                    "continue", "tools",
                                    "end", END
                            ))
                    // Tools 执行完后回到 Agent（形成循环）
                    .addEdge("tools", "agent");

            var compiledGraph = graph.compile();
            log.info("LangGraph4j Agent Loop 工作流构建成�? agent �?[条件边] �?tools �?agent（循环）�?END");
            return compiledGraph;

        } catch (GraphStateException e) {
            log.error("构建 LangGraph4j Agent Loop 工作流失�?, e);
            throw new RuntimeException("构建 Agent Loop 工作流失�?, e);
        }
    }

    /**
     * 执行指定的工�?     *
     * @param toolName      工具名称
     * @param arguments     工具参数（JSON 字符串）
     * @param weatherTool   天气工具
     * @param geocodingTool 经纬度工�?     * @param holidayTool   节假日工�?     * @return 工具执行结果
     */
    private String executeTool(String toolName, String arguments,
                               WeatherTool weatherTool,
                               GeocodingTool geocodingTool,
                               HolidayTool holidayTool) {
        try {
            // 解析 JSON 参数
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode args = mapper.readTree(arguments);

            return switch (toolName) {
                case "queryWeather" -> {
                    String city = args.path("city").asText("");
                    yield weatherTool.queryWeather(city);
                }
                case "queryCityCoordinates" -> {
                    String city = args.path("city").asText("");
                    yield geocodingTool.queryCityCoordinates(city);
                }
                case "queryHoliday" -> {
                    int year = args.path("year").asInt(2026);
                    yield holidayTool.queryHoliday(year);
                }
                default -> "未知工具: " + toolName;
            };
        } catch (Exception e) {
            log.error("执行工具 {} 失败", toolName, e);
            return "工具执行失败: " + e.getMessage();
        }
    }
}
