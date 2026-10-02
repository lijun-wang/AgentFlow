package com.agentflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * 数据库初始化配置：当数据库可用时执行 schema.sql 初始化表结构
 * <p>
 * 数据库可用性由 DatabaseAvailabilityEnvironmentPostProcessor 在启动早期检测，
 * 并通过 database.available 属性控制。仅当属性为 true 时执�?schema 初始化�? * <p>
 * 依赖数据库的服务和控制器同样通过 database.available 属性条件化注册�? */
@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    /**
     * �?database.available=true 时，执行 schema.sql 初始化数据库表结�?     * <p>
     * 使用 continueOnError=true，表已存在时忽略错误（幂等执行）
     *
     * @param dataSource      Spring Boot 自动配置�?HikariCP 数据�?     * @param databaseAvailable 数据库可用性标志（�?EnvironmentPostProcessor 设置�?     */
    public DatabaseConfig(DataSource dataSource,
                          @Value("${database.available:false}") boolean databaseAvailable) {
        if (databaseAvailable) {
            try {
                ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
                populator.addScript(new ClassPathResource("schema.sql"));
                populator.setContinueOnError(true); // 表已存在时忽略错�?                populator.execute(dataSource);
                log.info("数据�?schema.sql 初始化完�?);
            } catch (Exception e) {
                log.warn("执行 schema.sql 失败: {}", e.getMessage());
            }
        }
    }
}
