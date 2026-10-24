package com.agentflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * 数据库初始化配置：当数据库可用时执行 schema.sql / product.sql 初始化
 * <p>
 * 数据库可用性由 DatabaseAvailabilityEnvironmentPostProcessor 在启动早期检测，
 * 并通过 database.available 属性控制。仅当属性为 true 时执行 SQL 初始化。
 * <p>
 * 依赖数据库的服务和控制器同样通过 database.available 属性条件化注册。
 */
@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    /**
     * 当 database.available=true 时，依次执行 schema.sql、product.sql
     * <p>
     * 使用 continueOnError=true，表已存在时忽略错误（幂等执行）
     *
     * @param dataSource      Spring Boot 自动配置的 HikariCP 数据源
     * @param databaseAvailable 数据库可用性标志（由 EnvironmentPostProcessor 设置）
     */
    public DatabaseConfig(DataSource dataSource,
                          @Value("${database.available:false}") boolean databaseAvailable) {
        if (databaseAvailable) {
            executeScript(dataSource, "schema.sql");
            executeScript(dataSource, "product.sql");
        }
    }

    private void executeScript(DataSource dataSource, String scriptName) {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource(scriptName));
            populator.setContinueOnError(true); // 表已存在时忽略错误
            populator.execute(dataSource);
            log.info("数据库 {} 初始化完成", scriptName);
        } catch (Exception e) {
            log.warn("执行 {} 失败: {}", scriptName, e.getMessage());
        }
    }
}
