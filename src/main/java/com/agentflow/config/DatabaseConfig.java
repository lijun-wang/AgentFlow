package com.agentflow.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * PostgreSQL 数据库初始化配置：当主库可用时执行 schema.sql。
 * <p>
 * 商品表 product.sql 已迁至 MySQL（见 {@link MySqlDataSourceConfig}），
 * 不再在 PostgreSQL 上初始化。
 * <p>
 * 数据库可用性由 DatabaseAvailabilityEnvironmentPostProcessor 在启动早期检测，
 * 并通过 database.available 属性控制。
 */
@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    /**
     * 显式声明 PostgreSQL 主数据源，避免多数据源场景下被 MySQL 数据源覆盖。
     *
     * @return HikariDataSource（PostgreSQL）
     */
    @Bean(name = "postgresDataSource")
    @Primary
    public DataSource postgresDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${spring.datasource.driver-class-name}") String driverClassName) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverClassName);
        dataSource.setPoolName("postgres-hikari-pool");
        dataSource.setMaximumPoolSize(10);
        return dataSource;
    }

    /**
     * 显式声明主库 JdbcTemplate Bean，绑定 PostgreSQL 主数据源。
     * <p>
     * PostgresChunkVectorRepository 等依赖主库的组件通过 @Qualifier("postgresJdbcTemplate") 注入此 Bean。
     *
     * @param dataSource        PostgreSQL 主数据源
     * @param databaseAvailable 数据库可用性标志（由 EnvironmentPostProcessor 设置）
     */
    @Bean(name = "postgresJdbcTemplate")
    @Primary
    public JdbcTemplate postgresJdbcTemplate(@Qualifier("postgresDataSource") DataSource dataSource,
                                              @Value("${database.available:false}") boolean databaseAvailable) {
        if (databaseAvailable) {
            executeScript(dataSource, "schema.sql");
        }
        return new JdbcTemplate(dataSource);
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
