package com.vertor.service;

import com.vertor.model.ChatbotMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 聊天机器人 2.0 服务：基于 langchain4j AiService + DeepSeek 模型进行多轮对话
 * 支持 Tool 调用（如天气查询），AiService 自动管理对话记忆和工具调用
 */
@Service
public class ChatbotV2Service {

    private static final Logger log = LoggerFactory.getLogger(ChatbotV2Service.class);

    private final ChatbotV2Assistant chatbotV2Assistant;

    public ChatbotV2Service(ChatbotV2Assistant chatbotV2Assistant) {
        this.chatbotV2Assistant = chatbotV2Assistant;
    }

    /**
     * 发送消息并获取回复（支持多轮对话，AiService 内部管理对话记忆）
     *
     * @param history 对话历史（用于日志记录，实际对话记忆由 AiService 管理）
     * @return 机器人回复内容
     */
    public String chat(List<ChatbotMessage> history) {
        // 从历史消息中提取最后一条用户消息
        String lastUserMessage = null;
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatbotMessage msg = history.get(i);
            if ("user".equals(msg.getRole())) {
                lastUserMessage = msg.getContent();
                break;
            }
        }

        if (lastUserMessage == null || lastUserMessage.isBlank()) {
            return "抱歉，未检测到有效的用户消息。";
        }

        log.info("调用 AiService 处理用户消息: {}", lastUserMessage);

        try {
            // AiService 会自动处理 Tool 调用（如天气查询），并将结果整合到回复中
            String answer = chatbotV2Assistant.chat(lastUserMessage);

            log.info("AiService 回复完成，回答长度: {}", answer.length());
            log.debug("AiService 回复内容: {}", answer);
            return answer;

        } catch (Exception e) {
            log.error("调用 AiService 失败", e);
            throw new RuntimeException("调用 AI 助手失败: " + e.getMessage(), e);
        }
    }
}
