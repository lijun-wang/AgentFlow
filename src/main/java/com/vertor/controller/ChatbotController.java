package com.vertor.controller;

import com.vertor.model.ChatbotMessage;
import com.vertor.service.ChatbotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 聊天机器人控制器：提供通用 DeepSeek 对话接口
 */
@Tag(name = "聊天机器人", description = "基于 DeepSeek 的通用对话接口")
@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    private static final Logger log = LoggerFactory.getLogger(ChatbotController.class);

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    /**
     * 发送消息并获取回复
     */
    @Operation(
            summary = "发送聊天消息",
            description = "发送用户消息，返回机器人回复。支持传入对话历史实现多轮对话。"
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

            log.info("收到聊天消息: {}", userMessage);

            // 解析对话历史
            List<Map<String, String>> historyRaw = (List<Map<String, String>>) request.get("history");
            List<ChatbotMessage> history = new java.util.ArrayList<>();

            // 将历史消息转为模型对象
            if (historyRaw != null) {
                for (Map<String, String> msg : historyRaw) {
                    history.add(new ChatbotMessage(msg.get("role"), msg.get("content")));
                }
            }

            // 添加当前用户消息
            history.add(new ChatbotMessage("user", userMessage));

            // 调用 DeepSeek
            String reply = chatbotService.chat(history);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "reply", reply
            ));

        } catch (Exception e) {
            log.error("聊天机器人处理失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "处理失败: " + e.getMessage()
            ));
        }
    }
}
