package com.agentflow.config;

import com.agentflow.service.ChatbotV2Assistant;
import com.agentflow.tool.CommandLineTool;
import com.agentflow.tool.GeocodingTool;
import com.agentflow.tool.HolidayTool;
import com.agentflow.tool.SmartToolProvider;
import com.agentflow.tool.WeatherTool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 聊天机器�?2.0 配置：基�?langchain4j 创建 DeepSeek 模型�?ChatModel �?AiService
 */
@Configuration
public class ChatbotV2Config {

    /**
     * 创建 DeepSeek 模型�?ChatModel Bean
     * 使用 OpenAI 兼容协议连接 DeepSeek API
     *
     * @param baseUrl  DeepSeek API 地址
     * @param apiKey   DeepSeek API 密钥
     * @param modelName 模型名称
     * @param maxRetries 调用失败时的内部重试次数（不含首次调用）
     * @return langchain4j ChatModel 实例
     */
    @Bean("chatbotV2ChatModel")
    public OpenAiChatModel chatbotV2ChatModel(
            @Value("${deepseek.base-url}") String baseUrl,
            @Value("${deepseek.api-key}") String apiKey,
            @Value("${deepseek.model}") String modelName,
            @Value("${deepseek.max-retries:2}") Integer maxRetries) {

        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.7)
                .timeout(Duration.ofSeconds(120))
                .maxRetries(maxRetries)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * 创建聊天机器�?2.0 �?AiService Bean
     * 集成 ChatModel、SmartToolProvider（按需加载工具）和对话记忆
     * SmartToolProvider 根据用户消息内容动态决定提供哪些工具，减少 token 消�?     *
     * @param chatModel     DeepSeek ChatModel 实例
     * @param weatherTool   天气查询工具实例
     * @param geocodingTool 经纬度查询工具实�?     * @param holidayTool   节假日查询工具实�?     * @param commandLineTool 本地命令行执行工具实�?     * @return ChatbotV2Assistant 代理实例
     */
    @Bean
    public ChatbotV2Assistant chatbotV2Assistant(
            @Qualifier("chatbotV2ChatModel") ChatModel chatModel,
            WeatherTool weatherTool,
            GeocodingTool geocodingTool,
            HolidayTool holidayTool,
            CommandLineTool commandLineTool) {

        return AiServices.builder(ChatbotV2Assistant.class)
                .chatModel(chatModel)
                .toolProvider(new SmartToolProvider(weatherTool, geocodingTool, holidayTool, commandLineTool))
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .build();
    }
}
