package com.agentflow.service;

import com.agentflow.model.ChatbotMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 聊天机器人服务：调用 DeepSeek Chat API 进行多轮对话
 */
@Service
public class ChatbotService {

    private static final Logger log = LoggerFactory.getLogger(ChatbotService.class);

    private final RestClient deepSeekRestClient;

    @Value("${deepseek.model:deepseek-chat}")
    private String model;

    public ChatbotService(@Qualifier("deepSeekRestClient") RestClient deepSeekRestClient) {
        this.deepSeekRestClient = deepSeekRestClient;
    }

    /**
     * 发送消息并获取回复（支持多轮对话历史）
     *
     * @param history 对话历史（包�?user �?assistant 消息�?     * @return 机器人回复内�?     */
    public String chat(List<ChatbotMessage> history) {
        log.info("调用 DeepSeek 聊天，消息数: {}", history.size());

        // 构建 system prompt
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", "你是一个智能助手，可以回答各种问题。请用简洁、准确、友好的方式回答用户的问题�?
        ));

        // 添加对话历史
        for (ChatbotMessage msg : history) {
            messages.add(Map.of(
                    "role", msg.getRole(),
                    "content", msg.getContent()
            ));
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", messages,
                    "temperature", 0.7
            );

            log.debug("DeepSeek 请求�? {}", requestBody);

            Map<String, Object> response = deepSeekRestClient.post()
                    .uri("/v1/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String answer = (String) message.get("content");
                    log.info("DeepSeek 回复完成，回答长�? {}", answer.length());
                    log.debug("DeepSeek 回复内容: {}", answer);
                    return answer;
                }
            }

            log.warn("DeepSeek 返回空结�?);
            return "抱歉，无法生成回答�?;

        } catch (Exception e) {
            log.error("调用 DeepSeek 失败", e);
            throw new RuntimeException("调用 DeepSeek 失败: " + e.getMessage(), e);
        }
    }
}
