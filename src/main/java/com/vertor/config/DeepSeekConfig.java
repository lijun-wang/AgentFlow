package com.vertor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * DeepSeek 配置：创建专用的 RestClient 用于调用 DeepSeek Chat API
 */
@Configuration
public class DeepSeekConfig {

    @Bean("deepSeekRestClient")
    public RestClient deepSeekRestClient(
            @Value("${deepseek.api-key}") String apiKey,
            @Value("${deepseek.base-url:https://api.deepseek.com}") String baseUrl) {

        // 使用 JDK HttpClient，设置较长超时以支持 LLM 推理
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(30))
                        .build()
        );
        factory.setReadTimeout(Duration.ofSeconds(120));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
