package com.vertor.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * LangGraph4j 工作流服务：执行知识助手多步骤处理流程
 *
 * 每次请求通过 CompiledGraph.stream() 驱动工作流执行，
 * 收集每个节点的输出作为步骤结果返回给前端展示
 */
@Service
public class LangGraphService {

    private static final Logger log = LoggerFactory.getLogger(LangGraphService.class);

    /** 节点名称到中文标签的映射 */
    private static final Map<String, String> STEP_LABELS = Map.of(
            "analyze", "问题分析",
            "generate", "知识生成",
            "format", "回答格式化"
    );

    private final CompiledGraph<MessagesState<Object>> knowledgeWorkflow;

    public LangGraphService(CompiledGraph<MessagesState<Object>> knowledgeWorkflow) {
        this.knowledgeWorkflow = knowledgeWorkflow;
        log.info("LangGraphService 初始化完成，工作流已就绪");
    }

    /**
     * 处理用户问题，执行 LangGraph4j 工作流
     *
     * @param userQuestion 用户输入的问题
     * @return 包含最终回答和各步骤执行结果的结构化响应
     */
    public Map<String, Object> process(String userQuestion) {
        log.info("LangGraph4j 工作流开始处理问题: {}", userQuestion);
        long startTime = System.currentTimeMillis();

        try {
            // 构建初始状态：将用户问题作为 UserMessage 放入消息列表
            Map<String, Object> inputs = Map.of(
                    "messages", UserMessage.from(userQuestion)
            );

            // 执行工作流，收集每个节点的输出
            List<Map<String, Object>> steps = new ArrayList<>();

            for (var step : knowledgeWorkflow.stream(inputs)) {
                String nodeName = step.node();
                String stepLabel = STEP_LABELS.getOrDefault(nodeName, nodeName);

                // 从节点输出状态中提取最新消息作为该步骤的输出
                String stepOutput = extractLastAiMessage(step.state());

                steps.add(Map.of(
                        "step", nodeName,
                        "label", stepLabel,
                        "output", stepOutput,
                        "status", "completed"
                ));

                log.info("LangGraph4j 节点 [{}] 执行完成", stepLabel);
            }

            // 最终回答来自最后一个节点（format）的输出
            String finalAnswer = steps.isEmpty() ? "工作流未产生输出"
                    : (String) steps.get(steps.size() - 1).get("output");

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("LangGraph4j 工作流处理完成，共 {} 个步骤，耗时 {}ms", steps.size(), elapsed);

            return Map.of(
                    "success", true,
                    "answer", finalAnswer,
                    "steps", steps
            );

        } catch (Exception e) {
            log.error("LangGraph4j 工作流执行失败", e);
            return Map.of(
                    "success", false,
                    "message", "工作流执行失败: " + e.getMessage()
            );
        }
    }

    /**
     * 从 MessagesState 中提取最后一条 AiMessage 的文本内容
     */
    private String extractLastAiMessage(MessagesState<Object> state) {
        try {
            var messages = state.messages();
            for (int i = messages.size() - 1; i >= 0; i--) {
                Object msg = messages.get(i);
                if (msg instanceof AiMessage am) {
                    return am.text();
                }
            }
        } catch (Exception e) {
            log.warn("提取 AiMessage 失败", e);
        }
        return "";
    }
}
