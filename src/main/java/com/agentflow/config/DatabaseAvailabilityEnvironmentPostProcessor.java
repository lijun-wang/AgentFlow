package com.agentflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;

/**
 * 数据库可用性检测：�?Spring Boot 启动早期（Bean 定义解析之前）检�?PostgreSQL 连接
 * <p>
 * 通过 EnvironmentPostProcessor 机制，在 ApplicationContext 刷新之前设置 database.available 属性�? * 这确保了 @ConditionalOnProperty("database.available") 能正确评估条件�? * <p>
 * 直接�?Environment 中读�?datasource 配置并通过 DriverManager 创建连接测试�? * 因为此时 ApplicationContext 尚未创建，无法通过 BeanFactory 获取 DataSource�? * <p>
 * 如果数据库可连接：设�?database.available=true，启用向量存储和文档管理功能�? * 如果数据库不可连接：设置 database.available=false（默认），跳过数据库相关功能�? * 其他功能（聊天机器人、LangGraph 工作流等）正常运行�? */
public class DatabaseAvailabilityEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DatabaseAvailabilityEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // �?Environment 中直接读�?datasource 配置
        String url = environment.getProperty("spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");

        if (url == null || url.isBlank()) {
            log.warn("未配�?spring.datasource.url，跳过数据库可用性检测，向量存储和文档管理功能将不可�?);
            return;
        }

        try {
            // 直接使用 DriverManager 创建连接测试（此�?ApplicationContext 尚未创建，无法使�?DataSource Bean�?            try (Connection conn = DriverManager.getConnection(url, username, password)) {
                if (conn.isValid(5)) {
                    // 数据库可用：设置 database.available=true 属�?                    // addFirst 确保优先级最高，覆盖其他来源的同名属�?                    environment.getPropertySources().addFirst(
                            new MapPropertySource("databaseAvailability",
                                    Map.of("database.available", "true"))
                    );
                    log.info("PostgreSQL 数据库连接成功，启用向量存储和文档管理功�?);
                } else {
                    log.warn("PostgreSQL 数据库连接无效（isValid 返回 false），向量存储和文档管理功能将不可用，其他功能正常运行");
                }
            }
        } catch (Exception e) {
            log.warn("PostgreSQL 数据库连接失�? {}，向量存储和文档管理功能将不可用，其他功能正常运�?, e.getMessage());
        }
    }
}
