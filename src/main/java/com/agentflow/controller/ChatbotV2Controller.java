package com.agentflow.controller;

import com.agentflow.model.ChatbotMessage;
import com.agentflow.service.ChatbotV2Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 聊天机器�?2.0 控制器：基于 langchain4j + DeepSeek 模型提供对话接口
 */
@Tag(name = "聊天机器�?2.0", description = "基于 langchain4j + DeepSeek 模型的对话接�?)
@RestController
@RequestMapping("/api/chatbot/v2")
public class ChatbotV2Controller {

    private static final Logger log = LoggerFactory.getLogger(ChatbotV2Controller.class);

    private final ChatbotV2Service chatbotV2Service;

    public ChatbotV2Controller(ChatbotV2Service chatbotV2Service) {
        this.chatbotV2Service = chatbotV2Service;
    }

    /**
     * 发送消息并获取回复
     */
    @Operation(
            summary = "发送聊天消息（2.0�?,
            description = "基于 langchain4j + DeepSeek 模型，发送用户消息并返回机器人回复，支持多轮对话�?
    )
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> send(@RequestBody Map<String, Object> request) {
        try {
            String userMessage = (String) request.get("message");
            if (userMessage == null || userMessage.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "消息内容不能为空"
                ));
            }

            log.info("聊天机器�?2.0 收到消息: {}", userMessage);

            // 解析对话历史
            List<Map<String, String>> historyRaw = (List<Map<String, String>>) request.get("history");
            List<ChatbotMessage> history = new ArrayList<>();

            if (historyRaw != null) {
                for (Map<String, String> msg : historyRaw) {
                    history.add(new ChatbotMessage(msg.get("role"), msg.get("content")));
                }
            }

            // 添加当前用户消息
            history.add(new ChatbotMessage("user", userMessage));

            // 调用 DeepSeek 模型
            String reply = chatbotV2Service.chat(history);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "reply", reply
            ));

        } catch (Exception e) {
            log.error("聊天机器�?2.0 处理失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "处理失败: " + e.getMessage()
            ));
        }
    }
}
