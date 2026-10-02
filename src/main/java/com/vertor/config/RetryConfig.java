package com.vertor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * 重试配置：启用 Spring Retry 注解式重试支持
 * 使外部 API 工具（天气、地理编码、节假日）中 @Retryable 标注的方法在瞬时故障
 * （网络超时、5xx 服务端错误、429 限流）时自动按指数退避策略重试；
 * 4xx 客户端错误（参数错误、认证失败）不属于瞬时故障，不会触发重试
 */
@Configuration
@EnableRetry
public class RetryConfig {
}
