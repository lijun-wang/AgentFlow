package com.agentflow.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.langchain4j.serializer.jackson.LC4jJacksonStateSerializer;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * LangGraph4j 报销流程工作流配置：模拟企业报销审批状态图
 *
 * 工作流结构（线性流程，审批节点�?LLM 模拟决策）：
 *   START �?提交申请 �?部门审批 �?财务审核 �?完成报销 �?END
 *
 * 每个审批节点调用 DeepSeek 模型模拟审批决策�? * 审批结果�?通过"�?驳回"，驳回时流程提前终止
 */
@Configuration
public class ReimbursementConfig {

    private static final Logger log = LoggerFactory.getLogger(ReimbursementConfig.class);

    /**
     * 创建 LangGraph4j 报销流程工作�?     *
     * 工作流包含四个节点：
     * 1. submit（提交申请）：解析报销信息，提取金额、事由等关键信息
     * 2. dept_approval（部门审批）：模拟部门主管审批决�?     * 3. finance_review（财务审核）：模拟财务部门审核决�?     * 4. complete（完成报销）：生成报销完成确认通知
     *
     * @param chatModel DeepSeek ChatModel 实例（复用聊天机器人 2.0 的配置）
     * @return 编译后的报销流程工作流图
     */
    @Bean
    public CompiledGraph<MessagesState<Object>> reimbursementWorkflow(
            @Qualifier("chatbotV2ChatModel") ChatModel chatModel) {

        try {
            var serializer = new LC4jJacksonStateSerializer<>(MessagesState::new);
            var graph = new MessagesStateGraph<>(serializer)
                    // 节点 1：提交申�?- 解析报销信息
                    .addNode("submit", node_async(state -> {
                        var messages = state.messages();
                        String userRequest = extractLastUserMessage(messages);
                        log.info("报销流程节点 [提交申请] 开始处�? {}", userRequest);

                        String prompt = """
                                你是一个企业报销系统的申请受理模块。请解析以下报销申请信息，提取并确认以下关键信息�?                                1. 报销金额
                                2. 报销事由
                                3. 所属部�?                                4. 申请人信�?
                                请以正式的申请受理格式回复，确认收到的报销信息完整。如果信息不完整，请指出缺少的内容�?
                                报销申请�?s
                                """.formatted(userRequest);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String responseText = chatResponse.aiMessage().text();
                        log.info("报销流程节点 [提交申请] 完成，回答长�? {}", responseText.length());
                        return Map.<String, Object>of("messages", AiMessage.from(responseText));
                    }))
                    // 节点 2：部门审�?- LLM 模拟部门主管审批决策
                    .addNode("dept_approval", node_async(state -> {
                        var messages = state.messages();
                        String userRequest = extractLastUserMessage(messages);
                        String submitResult = extractMessageAt(messages, 1);
                        log.info("报销流程节点 [部门审批] 开始处�?);

                        String prompt = """
                                你是一个企业部门主管，需要审批以下报销申请�?
                                报销申请信息�?                                %s

                                申请受理确认�?                                %s

                                请根据以下规则进行审批：
                                - 如果报销金额超过 10000 元，有较大概率驳回（但不是绝对的�?                                - 如果报销事由模糊不清，可能驳�?                                - 如果报销合理且金额适中，通常通过

                                请按以下格式回复�?                                【审批结果：通过】或【审批结果：驳回�?                                审批意见：（简要说明审批理由，2-3句话�?                                """.formatted(userRequest, submitResult);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String responseText = chatResponse.aiMessage().text();
                        log.info("报销流程节点 [部门审批] 完成，回答长�? {}", responseText.length());
                        return Map.<String, Object>of("messages", AiMessage.from(responseText));
                    }))
                    // 节点 3：财务审�?- LLM 模拟财务部门审核决策
                    .addNode("finance_review", node_async(state -> {
                        var messages = state.messages();
                        String userRequest = extractLastUserMessage(messages);
                        String deptApproval = extractMessageAt(messages, 2);
                        log.info("报销流程节点 [财务审核] 开始处�?);

                        String prompt = """
                                你是企业财务部门的审核人员，需要审核以下报销申请�?
                                原始报销申请�?                                %s

                                部门审批结果�?                                %s

                                请从财务合规角度进行审核，关注：
                                - 报销金额是否在合理范围内
                                - 报销事由是否符合财务规定
                                - 票据是否齐全（模拟判断）

                                请按以下格式回复�?                                【审核结果：通过】或【审核结果：驳回�?                                审核意见：（简要说明审核理由，2-3句话�?                                """.formatted(userRequest, deptApproval);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String responseText = chatResponse.aiMessage().text();
                        log.info("报销流程节点 [财务审核] 完成，回答长�? {}", responseText.length());
                        return Map.<String, Object>of("messages", AiMessage.from(responseText));
                    }))
                    // 节点 4：完成报销 - 生成报销完成确认
                    .addNode("complete", node_async(state -> {
                        var messages = state.messages();
                        String userRequest = extractLastUserMessage(messages);
                        String financeReview = extractMessageAt(messages, 3);
                        log.info("报销流程节点 [完成报销] 开始处�?);

                        String prompt = """
                                报销申请已通过所有审批环节，请生成一份正式的报销完成确认通知�?
                                原始报销申请�?s
                                最终审核结果：%s

                                请包含以下信息：
                                1. 报销单号（随机生成一个）
                                2. 报销金额
                                3. 预计到账时间
                                4. 温馨提示（如保留好相关票据等�?
                                语言正式、友好，格式清晰�?                                """.formatted(userRequest, financeReview);

                        var chatResponse = chatModel.chat(UserMessage.from(prompt));
                        String responseText = chatResponse.aiMessage().text();
                        log.info("报销流程节点 [完成报销] 完成，回答长�? {}", responseText.length());
                        return Map.<String, Object>of("messages", AiMessage.from(responseText));
                    }))
                    // 定义边：START �?submit �?dept_approval �?finance_review �?complete �?END
                    .addEdge(START, "submit")
                    .addEdge("submit", "dept_approval")
                    .addEdge("dept_approval", "finance_review")
                    .addEdge("finance_review", "complete")
                    .addEdge("complete", END);

            var compiledGraph = graph.compile();
            log.info("LangGraph4j 报销流程工作流构建成�? submit �?dept_approval �?finance_review �?complete");
            return compiledGraph;

        } catch (GraphStateException e) {
            log.error("构建 LangGraph4j 报销流程工作流失�?, e);
            throw new RuntimeException("构建报销流程工作流失�?, e);
        }
    }

    /**
     * 从消息列表中提取最后一条用户消息内�?     */
    private String extractLastUserMessage(java.util.List<?> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Object msg = messages.get(i);
            if (msg instanceof UserMessage um) {
                return um.singleText();
            }
        }
        return messages.isEmpty() ? "" : messages.get(messages.size() - 1).toString();
    }

    /**
     * 从消息列表中提取指定位置的消息文�?     * 消息顺序：[0]=用户申请, [1]=submit输出, [2]=dept_approval输出, [3]=finance_review输出, [4]=complete输出
     */
    private String extractMessageAt(java.util.List<?> messages, int index) {
        if (index < messages.size()) {
            Object msg = messages.get(index);
            if (msg instanceof AiMessage am) {
                return am.text();
            }
            return msg.toString();
        }
        return "";
    }
}
