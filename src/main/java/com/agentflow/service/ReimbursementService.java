package com.agentflow.service;

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
 * LangGraph4j 报销流程工作流服务：执行报销审批多步骤处理流�? *
 * 每次请求通过 CompiledGraph.stream() 驱动报销工作流执行，
 * 收集每个节点的输出作为步骤结果返回给前端展示�? * 审批节点（部门审批、财务审核）可能�?LLM 判定为驳回，
 * 此时流程提前终止，不再执行后续节点�? */
@Service
public class ReimbursementService {

    private static final Logger log = LoggerFactory.getLogger(ReimbursementService.class);

    /** 节点名称到中文标签的映射 */
    private static final Map<String, String> STEP_LABELS = Map.of(
            "submit", "提交申请",
            "dept_approval", "部门审批",
            "finance_review", "财务审核",
            "complete", "完成报销"
    );

    /** 需要检查审批结果的节点名称 */
    private static final Set<String> APPROVAL_NODES = Set.of("dept_approval", "finance_review");

    private final CompiledGraph<MessagesState<Object>> reimbursementWorkflow;

    public ReimbursementService(CompiledGraph<MessagesState<Object>> reimbursementWorkflow) {
        this.reimbursementWorkflow = reimbursementWorkflow;
        log.info("ReimbursementService 初始化完成，报销流程工作流已就绪");
    }

    /**
     * 处理报销申请，执�?LangGraph4j 报销工作�?     *
     * @param reimbursementRequest 用户输入的报销申请信息
     * @return 包含最终结果和各步骤执行信息的结构化响�?     */
    public Map<String, Object> process(String reimbursementRequest) {
        log.info("报销流程工作流开始处�? {}", reimbursementRequest);
        long startTime = System.currentTimeMillis();

        try {
            // 构建初始状态：将报销申请作为 UserMessage 放入消息列表
            Map<String, Object> inputs = Map.of(
                    "messages", UserMessage.from(reimbursementRequest)
            );

            // 执行工作流，收集每个节点的输�?            List<Map<String, Object>> steps = new ArrayList<>();
            boolean rejected = false;

            for (var step : reimbursementWorkflow.stream(inputs)) {
                String nodeName = step.node();
                String stepLabel = STEP_LABELS.getOrDefault(nodeName, nodeName);

                // 从节点输出状态中提取最新消息作为该步骤的输�?                String stepOutput = extractLastAiMessage(step.state());

                // 检查审批节点是否驳�?                String status = "completed";
                if (APPROVAL_NODES.contains(nodeName) && isRejected(stepOutput)) {
                    status = "rejected";
                    rejected = true;
                    log.info("报销流程节点 [{}] 审批驳回，流程终�?, stepLabel);
                }

                steps.add(Map.of(
                        "step", nodeName,
                        "label", stepLabel,
                        "output", stepOutput,
                        "status", status
                ));

                log.info("报销流程节点 [{}] 执行完成，状�? {}", stepLabel, status);

                // 如果当前节点驳回，不再继续处理后续节�?                if (rejected) {
                    break;
                }
            }

            // 最终结果来自最后一个已执行节点的输�?            String finalResult = steps.isEmpty() ? "工作流未产生输出"
                    : (String) steps.get(steps.size() - 1).get("output");

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("报销流程工作流处理完成，�?{} 个步骤，耗时 {}ms，是否驳�? {}", steps.size(), elapsed, rejected);

            return Map.of(
                    "success", true,
                    "result", finalResult,
                    "steps", steps,
                    "rejected", rejected
            );

        } catch (Exception e) {
            log.error("报销流程工作流执行失�?, e);
            return Map.of(
                    "success", false,
                    "message", "工作流执行失�? " + e.getMessage()
            );
        }
    }

    /**
     * 判断审批结果是否为驳�?     * 检查输出文本中是否包含"驳回"关键�?     */
    private boolean isRejected(String output) {
        if (output == null || output.isEmpty()) {
            return false;
        }
        return output.contains("驳回");
    }

    /**
     * �?MessagesState 中提取最后一�?AiMessage 的文本内�?     */
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
