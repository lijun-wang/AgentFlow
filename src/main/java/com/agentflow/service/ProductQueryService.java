package com.agentflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 商品查询服务：将自然语言转为 SQL，并在 product_sales 表上执行分页查询
 */
@Service
@ConditionalOnProperty(name = "database.available", havingValue = "true")
public class ProductQueryService {

    private static final Logger log = LoggerFactory.getLogger(ProductQueryService.class);

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 100;

    private static final String SCHEMA_PROMPT = """
            你是 PostgreSQL 查询助手。根据用户的自然语言需求，生成针对 product_sales 表的只读 SQL。

            表结构 product_sales（商品销售明细表）：
            - id BIGSERIAL PRIMARY KEY  -- 主键
            - order_no VARCHAR(32) NOT NULL  -- 订单编号，唯一
            - product_id VARCHAR(16) NOT NULL  -- 商品编码，如 P01~P12
            - product_name VARCHAR(64) NOT NULL  -- 商品名称
            - category VARCHAR(32) NOT NULL  -- 商品类别：手机数码/家用电器/运动户外/食品饮料/美妆个护/电脑办公
            - unit_price DECIMAL(10,2) NOT NULL  -- 商品单价(元)
            - quantity INTEGER NOT NULL  -- 购买数量
            - discount DECIMAL(3,2) NOT NULL DEFAULT 1.00  -- 折扣率(1.00=无折扣)
            - total_amount DECIMAL(12,2) NOT NULL  -- 订单金额=单价*数量*折扣
            - customer_name VARCHAR(32)  -- 客户姓名
            - region VARCHAR(32)  -- 销售区域：北京/上海/广州/深圳/杭州/成都/武汉/南京/西安/重庆
            - payment_method VARCHAR(16)  -- 支付方式：微信支付/支付宝/银行卡/信用卡
            - order_status VARCHAR(16) NOT NULL DEFAULT '待付款'  -- 订单状态：待付款/已发货/已完成/已取消
            - sale_date DATE NOT NULL  -- 销售日期
            - created_at TIMESTAMP  -- 创建时间
            - updated_at TIMESTAMP  -- 更新时间

            规则：
            1. 只输出一条可执行的 SELECT 语句，不要解释，不要 Markdown 代码块，不要分号以外的多余文字
            2. 只能查询 product_sales 表，禁止 INSERT/UPDATE/DELETE/DROP/ALTER/TRUNCATE/CREATE 等写操作
            3. 禁止多语句、禁止分号后附加其他语句
            4. 不要添加 LIMIT 或 OFFSET，分页由系统统一处理
            5. 字段名使用蛇形命名（与表结构一致）
            6. 字符串比较注意中文枚举值与表中一致
            """;

    private static final Pattern FORBIDDEN_SQL = Pattern.compile(
            "(?i)\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|GRANT|REVOKE|COPY|EXECUTE|CALL|MERGE|REPLACE|ATTACH|DETACH)\\b"
    );

    /** 去除末尾 LIMIT / OFFSET 子句，便于服务端统一分页 */
    private static final Pattern LIMIT_OFFSET_TAIL = Pattern.compile(
            "(?i)\\s+LIMIT\\s+\\d+(\\s+OFFSET\\s+\\d+)?\\s*$"
    );
    private static final Pattern OFFSET_LIMIT_TAIL = Pattern.compile(
            "(?i)\\s+OFFSET\\s+\\d+(\\s+ROWS?)?(\\s+FETCH\\s+(FIRST|NEXT)\\s+\\d+\\s+ROWS?\\s+ONLY)?\\s*$"
    );
    private static final Pattern FETCH_TAIL = Pattern.compile(
            "(?i)\\s+FETCH\\s+(FIRST|NEXT)\\s+\\d+\\s+ROWS?\\s+ONLY\\s*$"
    );

    private final RestClient deepSeekRestClient;
    private final JdbcTemplate jdbcTemplate;

    @Value("${deepseek.model:deepseek-chat}")
    private String model;

    public ProductQueryService(@Qualifier("deepSeekRestClient") RestClient deepSeekRestClient,
                               JdbcTemplate jdbcTemplate) {
        this.deepSeekRestClient = deepSeekRestClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 自然语言 / 已有 SQL 分页查询商品
     *
     * @param question    用户自然语言（首次查询时必填；翻页时可空）
     * @param existingSql 已生成的基础 SQL（翻页时传入，避免重复调用大模型）
     * @param page        页码，从 1 开始
     * @param size        每页条数，默认 10
     * @return 包含 sql、data、total、page、size 的结果
     */
    public Map<String, Object> query(String question, String existingSql, int page, int size) {
        int safePage = Math.max(page, DEFAULT_PAGE);
        int safeSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        String baseSql;
        if (existingSql != null && !existingSql.isBlank()) {
            baseSql = sanitizeSql(existingSql);
        } else {
            if (question == null || question.isBlank()) {
                throw new IllegalArgumentException("查询内容不能为空");
            }
            baseSql = sanitizeSql(generateSql(question));
        }

        validateSelectOnly(baseSql);
        baseSql = stripLimitOffset(baseSql);

        long total = countRows(baseSql);
        int offset = (safePage - 1) * safeSize;
        String pageSql = baseSql + " LIMIT " + safeSize + " OFFSET " + offset;

        log.info("执行商品分页查询 SQL: {} | total={}", pageSql, total);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(pageSql);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sql", baseSql);
        result.put("pageSql", pageSql);
        result.put("data", rows);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    private long countRows(String baseSql) {
        String countSql = "SELECT COUNT(*) FROM (" + baseSql + ") AS _product_count_q";
        Long total = jdbcTemplate.queryForObject(countSql, Long.class);
        return total != null ? total : 0L;
    }

    /**
     * 调用 DeepSeek 将自然语言转为 SQL
     */
    private String generateSql(String question) {
        log.info("调用 DeepSeek 生成商品查询 SQL，问题: {}", question);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", SCHEMA_PROMPT),
                        Map.of("role", "user", "content", question)
                ),
                "temperature", 0.1
        );

        try {
            Map<String, Object> response = deepSeekRestClient.post()
                    .uri("/v1/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = ((String) message.get("content")).trim();
                    log.info("DeepSeek 生成 SQL 原始输出: {}", content);
                    return content;
                }
            }
            throw new RuntimeException("DeepSeek 返回空结果，无法生成 SQL");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 DeepSeek 生成 SQL 失败", e);
            throw new RuntimeException("生成 SQL 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 去除 Markdown 代码块、多余空白，只保留 SQL 正文
     */
    private String sanitizeSql(String raw) {
        String sql = raw.trim();
        if (sql.startsWith("```")) {
            int firstNewline = sql.indexOf('\n');
            if (firstNewline > 0) {
                sql = sql.substring(firstNewline + 1);
            }
            int endFence = sql.lastIndexOf("```");
            if (endFence >= 0) {
                sql = sql.substring(0, endFence);
            }
        }
        sql = sql.trim();
        while (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        return sql;
    }

    /**
     * 去掉模型可能附带的 LIMIT/OFFSET/FETCH，由服务端统一分页
     */
    private String stripLimitOffset(String sql) {
        String result = sql.trim();
        result = LIMIT_OFFSET_TAIL.matcher(result).replaceFirst("");
        result = OFFSET_LIMIT_TAIL.matcher(result).replaceFirst("");
        result = FETCH_TAIL.matcher(result).replaceFirst("");
        return result.trim();
    }

    /**
     * 仅允许单条 SELECT / WITH...SELECT，拒绝写操作与多语句
     */
    private void validateSelectOnly(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("生成的 SQL 为空");
        }
        if (sql.contains(";")) {
            throw new IllegalArgumentException("禁止执行多条 SQL 语句");
        }
        String normalized = sql.replaceAll("\\s+", " ").trim();
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!(upper.startsWith("SELECT") || upper.startsWith("WITH"))) {
            throw new IllegalArgumentException("仅允许 SELECT 查询，实际: " + sql);
        }
        if (FORBIDDEN_SQL.matcher(normalized).find()) {
            throw new IllegalArgumentException("SQL 包含禁止的写操作关键字: " + sql);
        }
    }
}
