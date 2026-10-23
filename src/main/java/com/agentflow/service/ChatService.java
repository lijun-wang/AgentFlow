package com.agentflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 聊天服务：调用 DeepSeek Chat API 对检索内容进行总结
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final RestClient deepSeekRestClient;

    @Value("${deepseek.model:deepseek-chat}")
    private String model;

    public ChatService(@Qualifier("deepSeekRestClient") RestClient deepSeekRestClient) {
        this.deepSeekRestClient = deepSeekRestClient;
    }

    /**
     * 使用 DeepSeek 将用户问题改写为最多三个与原问题最相似的并列关键词
     * <p>
     * 目的：用户原始问题可能口语化、模糊或包含无关词汇，改写后能提升检索召回率。
     *
     * @param question 用户原始问题
     * @return 改写后的关键词文本（空格分隔，最多 3 个）
     */
    public String rewriteQuery(String question) {
        log.info("调用 DeepSeek 改写查询: {}", question);

        String systemPrompt = """
                你是一个查询改写助手。将用户的问题改写为最适合在文档向量数据库中进行语义检索的关键词。
                要求：
                1. 最多输出三个与原问题语义最相似的并列关键词或短语，按相似度从高到低排列
                2. 关键词之间用单个空格分隔，不要使用逗号、顿号、编号或其他符号
                3. 保留核心语义，去除口语化表达和无关词汇，每个关键词尽量简短
                4. 只输出关键词本身，不要解释，不要加引号
                """;

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", question)
                    ),
                    "temperature", 0.6
            );

            Map<String, Object> response = deepSeekRestClient.post()
                    .uri("/v1/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String rewritten = ((String) message.get("content")).trim();
                    // 兜底截断：即使模型未遵守提示词，也只保留前三个关键词
                    String[] terms = rewritten.split("\\s+");
                    if (terms.length > 3) {
                        rewritten = String.join(" ", terms[0], terms[1], terms[2]);
                        log.info("DeepSeek 改写结果超过 3 个关键词，已截断: {}", rewritten);
                    }
                    log.info("DeepSeek 改写结果: {} -> {}", question, rewritten);
                    return rewritten;
                }
            }

            log.warn("DeepSeek 改写返回空结果，使用原始问题");
            return question;

        } catch (Exception e) {
            log.warn("DeepSeek 改写失败，降级使用原始问题: {}", e.getMessage());
            return question;
        }
    }

    /**
     * 基于检索到的文档内容，使用 DeepSeek 对用户问题进行总结回答
     *
     * @param question 用户问题
     * @param context  检索到的文档内容
     * @return DeepSeek 生成的总结回答
     */
    public String summarize(String question, String context) {
        log.info("调用 DeepSeek 进行总结，问题: {}", question);

        String systemPrompt = """
                你是一个智能文档问答助手。请根据以下参考文档内容来回答用户的问题。
                要求：
                1. 回答必须基于提供的参考文档内容，不要编造信息
                2. 如果参考文档中没有相关信息，请如实告知用户
                3. 回答要简洁、准确、有条理
                4. 如果参考文档中有多条相关内容，请综合总结
                """;

        String userPrompt = "参考文档内容：\n\n" + context + "\n\n用户问题：" + question;

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", 0.7
            );

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
                    log.info("DeepSeek 总结完成，回答长度: {}", answer.length());
                    return answer;
                }
            }

            log.warn("DeepSeek 返回空结果");
            return "抱歉，无法生成回答。";

        } catch (Exception e) {
            log.error("调用 DeepSeek 失败", e);
            throw new RuntimeException("调用 DeepSeek 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从文档分块中抽取知识三元组，要求模型只输出 JSON。
     *
     * @param chunkText          分块原文
     * @param allowedTypes       允许的实体类型（逗号分隔说明用）
     * @param allowedPredicates  允许的关系谓词（逗号分隔说明用）
     * @return 模型输出的 JSON 文本；失败时返回空字符串
     */
    public String extractTriplesJson(String chunkText, String allowedTypes, String allowedPredicates) {
        log.info("调用 DeepSeek 抽取知识三元组，文本长度: {}", chunkText.length());

        String systemPrompt = """
                你是知识图谱抽取助手。从用户提供的文档片段中抽取事实三元组。
                要求：
                1. 只输出 JSON，不要 Markdown，不要解释
                2. JSON 格式：{"triples":[{"subject":"","subjectType":"","predicate":"","object":"","objectType":"","confidence":0.0}]}
                3. subjectType 与 objectType 必须是以下之一：%s
                4. predicate 必须是以下之一：%s
                5. confidence 为 0 到 1 的小数，表示该三元组可从原文直接支持的把握
                6. 不要编造原文未出现的实体或关系；没有可抽取内容时返回 {"triples":[]}
                7. 实体名称尽量使用原文用语，不要翻译或扩写
                """.formatted(allowedTypes, allowedPredicates);

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", chunkText)
                    ),
                    "temperature", 0.2
            );

            Map<String, Object> response = deepSeekRestClient.post()
                    .uri("/v1/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    if (content != null) {
                        log.info("DeepSeek 三元组抽取完成，输出长度: {}", content.length());
                        return content.trim();
                    }
                }
            }

            log.warn("DeepSeek 三元组抽取返回空结果");
            return "";

        } catch (Exception e) {
            log.warn("DeepSeek 三元组抽取失败: {}", e.getMessage());
            return "";
        }
    }
}
