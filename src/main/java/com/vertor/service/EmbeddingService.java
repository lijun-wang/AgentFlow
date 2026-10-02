package com.vertor.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 嵌入服务：调用 OpenAI Embedding API 将文本转换为向量
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int MAX_BATCH_SIZE = 10;

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * 将文本列表批量转换为向量
     *
     * @param texts 文本列表
     * @return 向量列表（每个 float[] 长度为 1024）
     */
    public List<float[]> embedTexts(List<String> texts) {
        log.info("开始生成嵌入向量，共 {} 个文本块", texts.size());

        List<float[]> allEmbeddings = new ArrayList<>();

        for (int i = 0; i < texts.size(); i += MAX_BATCH_SIZE) {
            int end = Math.min(i + MAX_BATCH_SIZE, texts.size());
            List<String> batch = texts.subList(i, end);
            log.debug("发送第 {}/{} 批，共 {} 个文本", (i / MAX_BATCH_SIZE) + 1,
                    (int) Math.ceil((double) texts.size() / MAX_BATCH_SIZE), batch.size());

            EmbeddingRequest request = new EmbeddingRequest(batch, null);
            EmbeddingResponse response = embeddingModel.call(request);

            response.getResults().stream()
                    .map(embedding -> embedding.getOutput())
                    .forEach(allEmbeddings::add);
        }

        log.info("嵌入向量生成完成，共 {} 个向量", allEmbeddings.size());
        return allEmbeddings;
    }

    /**
     * 将单个文本转换为向量
     *
     * @param text 文本
     * @return 向量（float[] 长度为 1024）
     */
    public float[] embedText(String text) {
        return embeddingModel.embed(text);
    }
}
