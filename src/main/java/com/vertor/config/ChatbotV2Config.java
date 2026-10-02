package com.vertor.config;

import com.vertor.service.ChatbotV2Assistant;
import com.vertor.tool.CommandLineTool;
import com.vertor.tool.GeocodingTool;
import com.vertor.tool.HolidayTool;
import com.vertor.tool.SmartToolProvider;
import com.vertor.tool.WeatherTool;
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
 * 聊天机器人 2.0 配置：基于 langchain4j 创建 DeepSeek 模型的 ChatModel 和 AiService
 */
@Configuration
public class ChatbotV2Config {

    /**
     * 创建 DeepSeek 模型的 ChatModel Bean
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
     * 创建聊天机器人 2.0 的 AiService Bean
     * 集成 ChatModel、SmartToolProvider（按需加载工具）和对话记忆
     * SmartToolProvider 根据用户消息内容动态决定提供哪些工具，减少 token 消耗
     *
     * @param chatModel     DeepSeek ChatModel 实例
     * @param weatherTool   天气查询工具实例
     * @param geocodingTool 经纬度查询工具实例
     * @param holidayTool   节假日查询工具实例
     * @param commandLineTool 本地命令行执行工具实例
     * @return ChatbotV2Assistant 代理实例
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
