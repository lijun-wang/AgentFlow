package com.agentflow.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * MySQL 数据源配置：专供 Text-to-SQL 商品查询模块使用。
 * <p>
 * 与主数据源 PostgreSQL 隔离；仅当 mysql.available=true 时注册 Bean，
 * 并在启动时执行 product.sql 初始化表与测试数据。
 */
@Configuration
@ConditionalOnProperty(name = "mysql.available", havingValue = "true")
public class MySqlDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(MySqlDataSourceConfig.class);

    @Bean(name = "mysqlDataSource")
    public DataSource mysqlDataSource(
            @Value("${mysql.datasource.url}") String url,
            @Value("${mysql.datasource.username}") String username,
            @Value("${mysql.datasource.password}") String password,
            @Value("${mysql.datasource.driver-class-name}") String driverClassName) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverClassName);
        dataSource.setPoolName("mysql-product-pool");
        dataSource.setMaximumPoolSize(10);

        initProductSchema(dataSource);
        log.info("MySQL 数据源已创建: {}", maskJdbcUrl(url));
        return dataSource;
    }

    @Bean(name = "mysqlJdbcTemplate")
    public JdbcTemplate mysqlJdbcTemplate(@Qualifier("mysqlDataSource") DataSource mysqlDataSource) {
        return new JdbcTemplate(mysqlDataSource);
    }

    private void initProductSchema(DataSource mysqlDataSource) {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource("product.sql"));
            populator.setContinueOnError(true);
            populator.setSeparator(";");
            populator.execute(mysqlDataSource);
            log.info("MySQL product.sql 初始化完成");
        } catch (Exception e) {
            log.warn("执行 MySQL product.sql 失败: {}", e.getMessage());
        }
    }

    private static String maskJdbcUrl(String url) {
        return url == null ? "" : url.replaceAll("password=[^&]*", "password=***");
    }
}
