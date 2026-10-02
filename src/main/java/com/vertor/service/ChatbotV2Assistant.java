package com.vertor.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 聊天机器人 2.0 AI 服务接口：由 langchain4j AiServices 动态代理实现
 * 支持 Tool 调用（如天气查询）和多轮对话
 */
public interface ChatbotV2Assistant {

    /**
     * 处理用户消息并返回回复
     *
     * @param userMessage 用户发送的消息
     * @return 机器人回复内容
     */
    @SystemMessage("你是一个智能助手，可以回答各种问题。请用简洁、准确、友好的方式回答用户的问题。当用户询问天气时，请使用天气查询工具获取实时天气信息。当用户询问某年的节假日或放假安排时，请使用节假日查询工具获取该年的法定节假日信息。当用户要求执行本地命令、运行脚本或查看系统信息时，请使用命令行执行工具。")
    String chat(@UserMessage String userMessage);
}
