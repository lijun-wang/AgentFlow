package com.vertor.model;

/**
 * 聊天机器人消息
 */
public class ChatbotMessage {

    /** 消息角色：user（用户发送的消息） / assistant（AI 助手的回复） / system（系统提示词，用于设定 AI 行为） */
    private String role;

    /** 消息内容 */
    private String content;

    public ChatbotMessage() {
    }

    public ChatbotMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
