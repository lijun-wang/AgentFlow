package com.agentflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * 数据库可用性检测：在 Spring Boot 启动早期检测 PostgreSQL 与 MySQL 连接。
 * <p>
 * - database.available：主库 PostgreSQL（向量存储 / 知识图谱）
 * - mysql.available：MySQL（Text-to-SQL 商品查询）
 * <p>
 * 若 MySQL 库 product 不存在，会尝试自动创建。
 */
public class DatabaseAvailabilityEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DatabaseAvailabilityEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String vectorStoreType = environment.getProperty("vector-store.type", "postgres");
        log.info("向量存储后端配置: vector-store.type={}", vectorStoreType);

        Map<String, Object> flags = new HashMap<>();
        checkPostgres(environment, vectorStoreType, flags);
        checkMysql(environment, flags);

        if (!flags.isEmpty()) {
            environment.getPropertySources().addFirst(
                    new MapPropertySource("databaseAvailability", flags)
            );
        }
    }

    private void checkPostgres(ConfigurableEnvironment environment, String vectorStoreType, Map<String, Object> flags) {
        String url = environment.getProperty("spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");

        if (url == null || url.isBlank()) {
            if ("milvus".equalsIgnoreCase(vectorStoreType)) {
                log.info("未配置 spring.datasource.url；当前向量后端为 milvus，文档向量功能仍可用，知识图谱等依赖 Postgres 的功能将不可用");
            } else {
                log.warn("未配置 spring.datasource.url，跳过数据库可用性检测，向量存储和文档管理功能将不可用");
            }
            return;
        }

        try {
            try (Connection conn = DriverManager.getConnection(url, username, password)) {
                if (conn.isValid(5)) {
                    flags.put("database.available", "true");
                    log.info("PostgreSQL 数据库连接成功，启用向量存储和文档管理功能");
                } else {
                    log.warn("PostgreSQL 数据库连接无效（isValid 返回 false），向量存储和文档管理功能将不可用，其他功能正常运行");
                }
            }
        } catch (Exception e) {
            log.warn("PostgreSQL 数据库连接失败: {}，向量存储和文档管理功能将不可用，其他功能正常运行", e.getMessage());
        }
    }

    private void checkMysql(ConfigurableEnvironment environment, Map<String, Object> flags) {
        String url = environment.getProperty("mysql.datasource.url");
        String username = environment.getProperty("mysql.datasource.username");
        String password = environment.getProperty("mysql.datasource.password");

        if (url == null || url.isBlank()) {
            log.warn("未配置 mysql.datasource.url，跳过 MySQL 可用性检测，Text-to-SQL 商品查询将不可用");
            return;
        }

        try {
            ensureMysqlDatabaseExists(url, username, password);
            try (Connection conn = DriverManager.getConnection(url, username, password)) {
                if (conn.isValid(5)) {
                    flags.put("mysql.available", "true");
                    log.info("MySQL 数据库连接成功，启用 Text-to-SQL 商品查询功能");
                } else {
                    log.warn("MySQL 数据库连接无效（isValid 返回 false），Text-to-SQL 商品查询将不可用");
                }
            }
        } catch (Exception e) {
            log.warn("MySQL 数据库连接失败: {}，Text-to-SQL 商品查询将不可用", e.getMessage());
        }
    }

    /**
     * 若 JDBC URL 指向的库不存在，则先连到 MySQL 服务器创建该库（utf8mb4）。
     */
    private void ensureMysqlDatabaseExists(String jdbcUrl, String username, String password) {
        String database = extractDatabaseName(jdbcUrl);
        if (database == null || database.isBlank()) {
            return;
        }
        String serverUrl = jdbcUrl.replaceFirst("/" + database + "(\\?|$)", "/$1");
        if (serverUrl.equals(jdbcUrl)) {
            return;
        }
        try (Connection conn = DriverManager.getConnection(serverUrl, username, password);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "CREATE DATABASE IF NOT EXISTS `" + database
                            + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
            );
            log.info("已确保 MySQL 数据库存在: {}", database);
        } catch (Exception e) {
            log.debug("创建 MySQL 数据库 {} 时跳过或失败: {}", database, e.getMessage());
        }
    }

    private String extractDatabaseName(String jdbcUrl) {
        // jdbc:mysql://host:port/dbname?...
        int schemeEnd = jdbcUrl.indexOf("://");
        if (schemeEnd < 0) {
            return null;
        }
        int pathStart = jdbcUrl.indexOf('/', schemeEnd + 3);
        if (pathStart < 0 || pathStart + 1 >= jdbcUrl.length()) {
            return null;
        }
        String path = jdbcUrl.substring(pathStart + 1);
        int queryIdx = path.indexOf('?');
        if (queryIdx >= 0) {
            path = path.substring(0, queryIdx);
        }
        return path.isBlank() ? null : path;
    }
}
