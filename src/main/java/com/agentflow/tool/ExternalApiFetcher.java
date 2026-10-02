package com.agentflow.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 外部 API 取数组件：集中承载工具类对外部免�?API �?HTTP 调用，并提供注解式重�? * <p>
 * 重试策略（仅针对瞬时故障，指数退避：1s -> 2s -> 4s，最�?3 次尝试）�? * - 可重试：网络超时/连接失败（ResourceAccessException）�?xx 服务端错误�?29 限流
 * - 不可重试�?00/401/403/404 �?4xx 客户端错误（参数或认证问题，重试无意义），异常直接抛�? * <p>
 * 注意：@Retryable 基于 Spring AOP 代理生效，必须从其他 Bean �?bean 调用�? * 因此工具类（WeatherTool 等）注入本组件调用取数方法，而不能在工具类内部自调用
 */
@Component
public class ExternalApiFetcher {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiFetcher.class);

    /** 连接超时时间 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** 读取超时时间（外部免�?API 响应可能较慢�?*/
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestClient weatherClient;
    private final RestClient geocodingClient;
    private final RestClient holidayClient;

    public ExternalApiFetcher(
            @Value("${holiday.api.base-url:https://timor.tech}") String holidayApiBaseUrl) {
        this.weatherClient = buildClient("https://wttr.in");
        this.geocodingClient = buildClient("https://geocoding-api.open-meteo.com");
        this.holidayClient = buildClient(holidayApiBaseUrl);
    }

    /**
     * 构建带超时控制的 RestClient
     */
    private RestClient buildClient(String baseUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .build()
        );
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /**
     * 获取 wttr.in 天气原始 JSON 数据
     *
     * @param city 城市名称（支持中文和英文�?     * @return 天气 JSON 字符�?     * @throws RestClientException 网络故障、超时或服务端错误（重试耗尽后抛出）
     */
    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            noRetryFor = {HttpClientErrorException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    public String fetchWeather(String city) {
        log.debug("请求 wttr.in 天气数据，城�? {}", city);
        return weatherClient.get()
                .uri("/{city}?format=j1&lang=zh", city)
                .retrieve()
                .body(String.class);
    }

    /**
     * 获取 Open-Meteo 地理编码原始 JSON 数据
     *
     * @param city 城市名称（支持中文和英文�?     * @return 地理编码 JSON 字符�?     * @throws RestClientException 网络故障、超时或服务端错误（重试耗尽后抛出）
     */
    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            noRetryFor = {HttpClientErrorException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    public String fetchGeocoding(String city) {
        log.debug("请求 Open-Meteo 地理编码数据，城�? {}", city);
        return geocodingClient.get()
                .uri("/v1/search?name={city}&count=1&language=zh", city)
                .retrieve()
                .body(String.class);
    }

    /**
     * 获取指定年份的节假日原始 JSON 数据
     *
     * @param year 年份（如 2025�?     * @return 节假�?JSON 字符�?     * @throws RestClientException 网络故障、超时或服务端错误（重试耗尽后抛出）
     */
    @Retryable(
            retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
            noRetryFor = {HttpClientErrorException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    public String fetchHoliday(int year) {
        log.debug("请求节假�?API 数据，年�? {}", year);
        return holidayClient.get()
                .uri("/api/holiday/year/{year}", year)
                .retrieve()
                .body(String.class);
    }
}
