package com.agentflow.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LangGraph4j Fan-Out / Fan-In 并发�?Agent 工作流服�? *
 * 实现三阶段执行流程：
 * 1. 任务分解（task_planner）：调用 LLM 将用户任务拆解为 4 个子任务
 * 2. 并发执行（fan-out）：4 位专�?Agent 通过 CompletableFuture 并发处理各自子任�? * 3. 归总汇总（fan-in / aggregator）：调用 LLM 综合所�?Agent 结果生成最终报�? *
 * 区别于现有模式：
 * - SubagentService：Supervisor 路由到单个专家，串行执行
 * - MultiAgentService：多个专家依次串行发言（圆桌讨论）
 * - FanOutService：多�?Agent 真正并发执行，最后归总（Fan-Out / Fan-In�? */
@Service
public class FanOutService {

    private static final Logger log = LoggerFactory.getLogger(FanOutService.class);

    /** 专家 Agent 角色定义：名�?�?图标 */
    private static final Map<String, String> AGENT_ICONS = Map.of(
            "调研分析�?, "🔍",
            "创意策划", "💡",
            "技术顾�?, "⚙️",
            "执行专家", "🎯"
    );

    /** 专家 Agent 角色定义：名�?�?系统提示词模�?*/
    private static final Map<String, String> AGENT_PROMPTS = Map.of(
            "调研分析�?, """
                    你是一位资深调研分析师，擅长信息收集、背景调研和数据分析�?                    
                    你的子任务：%s
                    
                    用户原始任务背景�?s
                    
                    请深入调研分析，提供全面、准确的信息和数据支持。输出要求：
                    1. 关键信息和数据（附来源或依据�?                    2. 现状分析和趋势判�?                    3. 对标案例或参考信�?                    4. 核心发现总结
                    """,
            "创意策划", """
                    你是一位资深创意策划专家，擅长创新思维、方案设计和差异化策略�?                    
                    你的子任务：%s
                    
                    用户原始任务背景�?s
                    
                    请提供富有创意的解决方案。输出要求：
                    1. 核心创意和差异化思路
                    2. 具体方案设计（至�?2-3 个备选）
                    3. 创新亮点和竞争优�?                    4. 实施可行性评�?                    """,
            "技术顾�?, """
                    你是一位资深技术顾问，擅长技术架构、实现方案和技术风险评估�?                    
                    你的子任务：%s
                    
                    用户原始任务背景�?s
                    
                    请从技术角度给出专业分析。输出要求：
                    1. 技术可行性分�?                    2. 推荐技术方案及理由
                    3. 技术风险点和应对措�?                    4. 性能、成本、复杂度评估
                    """,
            "执行专家", """
                    你是一位资深执行专家，擅长项目落地、执行计划和时间节点规划�?                    
                    你的子任务：%s
                    
                    用户原始任务背景�?s
                    
                    请给出可落地的执行方案。输出要求：
                    1. 具体执行步骤（分阶段�?                    2. 时间节点和里程碑
                    3. 资源需求和分配建议
                    4. 关键风险和应急预�?                    """
    );

    private final ChatModel chatModel;

    public FanOutService(@Qualifier("chatbotV2ChatModel") ChatModel chatModel) {
        this.chatModel = chatModel;
        log.info("FanOutService 初始化完成，并发�?Agent 工作流已就绪");
    }

    /**
     * 处理用户任务，执�?Fan-Out / Fan-In 并发�?Agent 工作�?     *
     * @param userTask 用户输入的任务描�?     * @return 包含最终报告、各 Agent 结果和步骤执行结果的结构化响�?     */
    public Map<String, Object> process(String userTask) {
        log.info("Fan-Out 并发工作流开始处理任�? {}", userTask);
        long startTime = System.currentTimeMillis();

        try {
            List<Map<String, Object>> steps = new ArrayList<>();

            // ===== 阶段 1：任务分解（task_planner�?=====
            log.info("Fan-Out 阶段 1：任务分解开�?);
            String plannerOutput = executeTaskPlanner(userTask);
            steps.add(Map.of(
                    "step", "task_planner",
                    "label", "任务分解",
                    "icon", "📋",
                    "output", plannerOutput,
                    "status", "completed"
            ));
            log.info("Fan-Out 阶段 1 完成，分解结果长�? {}", plannerOutput.length());

            // ===== 阶段 2：解析子任务，并发执行多 Agent =====
            log.info("Fan-Out 阶段 2：解析子任务并并发执�?);
            List<Map<String, String>> subtasks = parseSubtasks(plannerOutput);
            log.info("Fan-Out 解析�?{} 个子任务", subtasks.size());

            // 使用 CompletableFuture 并发执行所�?Agent
            Map<String, String> agentResults = new ConcurrentHashMap<>();
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (Map<String, String> subtask : subtasks) {
                String agentName = subtask.get("agent");
                String subtaskDesc = subtask.get("task");

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    long agentStart = System.currentTimeMillis();
                    log.info("Fan-Out 并发 Agent [{}] 开始执行子任务: {}", agentName, subtaskDesc);

                    String promptTemplate = AGENT_PROMPTS.getOrDefault(agentName,
                            "你是专家。子任务�?s\n任务背景�?s\n请给出专业分析�?);
                    String prompt = promptTemplate.formatted(subtaskDesc, userTask);

                    var chatResponse = chatModel.chat(UserMessage.from(prompt));
                    String result = chatResponse.aiMessage().text();

                    long agentElapsed = System.currentTimeMillis() - agentStart;
                    agentResults.put(agentName, result);
                    log.info("Fan-Out 并发 Agent [{}] 执行完成，耗时 {}ms，结果长�? {}",
                            agentName, agentElapsed, result.length());
                });
                futures.add(future);
            }

            // 等待所�?Agent 并发执行完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            log.info("Fan-Out 阶段 2 完成：{} �?Agent 全部并发执行完毕", agentResults.size());

            // 记录每个 Agent 的步骤结�?            for (Map<String, String> subtask : subtasks) {
                String agentName = subtask.get("agent");
                String icon = AGENT_ICONS.getOrDefault(agentName, "🤖");
                String result = agentResults.getOrDefault(agentName, "执行超时或失�?);
                steps.add(Map.of(
                        "step", "agent_" + agentName,
                        "label", agentName,
                        "icon", icon,
                        "output", result,
                        "status", "completed",
                        "isConcurrent", true
                ));
            }

            // ===== 阶段 3：归总汇总（aggregator�?=====
            log.info("Fan-Out 阶段 3：归总汇总开�?);
            String aggregatorOutput = executeAggregator(userTask, subtasks, agentResults);
            steps.add(Map.of(
                    "step", "aggregator",
                    "label", "归总汇�?,
                    "icon", "📊",
                    "output", aggregatorOutput,
                    "status", "completed"
            ));
            log.info("Fan-Out 阶段 3 完成，归总报告长�? {}", aggregatorOutput.length());

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Fan-Out 并发工作流全部完成，�?{} 个步骤（�?{} 个并�?Agent），耗时 {}ms",
                    steps.size(), agentResults.size(), elapsed);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("answer", aggregatorOutput);
            result.put("steps", steps);
            result.put("agentCount", agentResults.size());
            result.put("elapsed", elapsed);
            return result;

        } catch (Exception e) {
            log.error("Fan-Out 并发工作流执行失�?, e);
            return Map.of(
                    "success", false,
                    "message", "工作流执行失�? " + e.getMessage()
            );
        }
    }

    /**
     * 阶段 1：执行任务分解（对应 LangGraph4j task_planner 节点�?     */
    private String executeTaskPlanner(String userTask) {
        String prompt = """
                你是一个任务分解专家。请将用户的任务拆解�?4 个子任务，分别交给以�?4 位专家并发执行：
                
                1. 🔍 调研分析师：负责信息收集、背景调研、数据分�?                2. 💡 创意策划：负责创意方案、创新思路、差异化策略
                3. ⚙️ 技术顾问：负责技术可行性、实现方案、技术风险评�?                4. 🎯 执行专家：负责落地计划、执行步骤、时间节点、资源分�?                
                用户任务�?s
                
                请严格按以下 JSON 格式输出（不要输出其他内容）�?                {
                  "subtasks": [
                    {"agent": "调研分析�?, "task": "具体子任务描�?},
                    {"agent": "创意策划", "task": "具体子任务描�?},
                    {"agent": "技术顾�?, "task": "具体子任务描�?},
                    {"agent": "执行专家", "task": "具体子任务描�?}
                  ]
                }
                """.formatted(userTask);

        var chatResponse = chatModel.chat(UserMessage.from(prompt));
        return chatResponse.aiMessage().text();
    }

    /**
     * 阶段 3：执行归总汇总（对应 LangGraph4j aggregator 节点�?     */
    private String executeAggregator(String userTask, List<Map<String, String>> subtasks,
                                     Map<String, String> agentResults) {
        // 构建�?Agent 结果汇总文�?        StringBuilder resultsText = new StringBuilder();
        for (Map<String, String> subtask : subtasks) {
            String agentName = subtask.get("agent");
            String result = agentResults.getOrDefault(agentName, "执行超时或失�?);
            String icon = AGENT_ICONS.getOrDefault(agentName, "🤖");
            resultsText.append("�?).append(icon).append(" ").append(agentName).append("】\n");
            resultsText.append("子任务：").append(subtask.get("task")).append("\n");
            resultsText.append("执行结果�?).append(result).append("\n\n");
        }

        String prompt = """
                你是一个高级分析师和报告撰写专家。以下是 4 位专家并发完成各自子任务后的结果汇总�?                
                用户原始任务�?s
                
                各专家子任务执行结果�?                %s
                
                请综合所有专家的输出，撰写一份完整的最终报告，要求�?                1. 📌 综合概述：用 2-3 句话总结整体结论
                2. 🔑 各专家要点提炼：每位专家的核心观点各�?1-2 句话概括
                3. 🔗 交叉分析：不同专家观点之间的关联、互补或矛盾
                4. �?综合建议：基于所有专家输出给出最终行动建�?                5. ⚠️ 风险提示：需要注意的潜在问题
                
                要求：结构清晰，专业严谨，具有可操作性�?                """.formatted(userTask, resultsText.toString());

        var chatResponse = chatModel.chat(UserMessage.from(prompt));
        return chatResponse.aiMessage().text();
    }

    /**
     * 从任务分解输出中解析子任务列�?     * 支持 JSON 格式和容错处�?     */
    private List<Map<String, String>> parseSubtasks(String plannerOutput) {
        List<Map<String, String>> subtasks = new ArrayList<>();

        // 默认 Agent 列表（当解析失败时使用）
        String[] defaultAgents = {"调研分析�?, "创意策划", "技术顾�?, "执行专家"};

        try {
            // 使用正则匹配 JSON 中的 agent �?task 字段
            Pattern pattern = Pattern.compile("\"agent\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"task\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(plannerOutput);

            while (matcher.find()) {
                String agent = matcher.group(1);
                String task = matcher.group(2);
                subtasks.add(Map.of("agent", agent, "task", task));
            }
        } catch (Exception e) {
            log.warn("解析任务分解结果失败，使用默认分�?, e);
        }

        // 如果解析失败，使用默认分�?        if (subtasks.size() < 4) {
            log.warn("解析出的子任务数量不�?4 个（实际 {} 个），使用默认分�?, subtasks.size());
            subtasks.clear();
            for (String agent : defaultAgents) {
                subtasks.add(Map.of("agent", agent, "task", "针对用户任务�? + agent + "角度进行专业分析"));
            }
        }

        return subtasks;
    }
}
