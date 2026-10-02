package com.agentflow.config;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j 配置：创�?EmbeddingModel 和文档分块器 Bean
 */
@Configuration
public class Langchain4jConfig {

    /**
     * 创建 LangChain4j EmbeddingModel Bean
     * 使用 OpenAI 兼容协议调用通义千问 text-embedding-v2 模型生成 1024 维向�?     * <p>
     * 注意：baseUrl 需包含 /v1 路径（如 https://dashscope.aliyuncs.com/compatible-mode/v1），
     * 因为 LangChain4j 内部会在 baseUrl 后拼�?/embeddings
     *
     * @param baseUrl   API 地址（通义千问 OpenAI 兼容端点，含 /v1�?     * @param apiKey    API 密钥
     * @param modelName 嵌入模型名称
     * @param maxRetries 调用失败时的内部重试次数（不含首次调用）
     * @return LangChain4j EmbeddingModel 实例
     */
    @Bean("langchain4jEmbeddingModel")
    public EmbeddingModel langchain4jEmbeddingModel(
            @Value("${langchain4j.embedding.base-url}") String baseUrl,
            @Value("${langchain4j.embedding.api-key}") String apiKey,
            @Value("${langchain4j.embedding.model}") String modelName,
            @Value("${langchain4j.embedding.max-retries:2}") Integer maxRetries) {

        return OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(maxRetries)
                .build();
    }

    /**
     * 创建 LangChain4j 递归文档分块�?Bean
     * 按段落、行、句子等自然边界递归分块，支持中英文混合文档
     *
     * @return DocumentSplitter 实例（maxSegmentSize=1000字符, maxOverlap=100字符�?     */
    @Bean("langchain4jTextSplitter")
    public DocumentSplitter langchain4jTextSplitter() {
        return DocumentSplitters.recursive(1000, 100);
    }
}
