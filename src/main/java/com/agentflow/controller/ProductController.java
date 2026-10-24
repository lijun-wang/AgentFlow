package com.agentflow.controller;

import com.agentflow.service.ProductQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商品查询控制器：自然语言 → SQL → 分页查询 product_sales
 */
@Tag(name = "商品查询", description = "自然语言商品销售查询（Text-to-SQL）")
@RestController
@RequestMapping("/api/product")
@ConditionalOnProperty(name = "database.available", havingValue = "true")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductQueryService productQueryService;

    public ProductController(ProductQueryService productQueryService) {
        this.productQueryService = productQueryService;
    }

    /**
     * 自然语言 / 已有 SQL 分页查询商品销售数据
     * <p>
     * 请求体示例：
     * <pre>
     * {"question": "查询北京地区已完成的手机数码订单", "page": 1, "size": 10}
     * {"sql": "SELECT * FROM product_sales WHERE region = '北京'", "page": 2, "size": 10}
     * </pre>
     * 返回：基础 SQL、当前页数据、总条数、page、size
     */
    @Operation(
            summary = "自然语言商品分页查询",
            description = "将用户输入转为 SQL 并分页查询 product_sales；翻页时可传 sql 复用，避免重复调用大模型"
    )
    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> query(@RequestBody Map<String, Object> request) {
        try {
            String question = request.get("question") != null
                    ? String.valueOf(request.get("question")).trim()
                    : "";
            String sql = request.get("sql") != null
                    ? String.valueOf(request.get("sql")).trim()
                    : "";

            if (question.isEmpty() && sql.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "查询内容不能为空"
                ));
            }

            int page = parsePositiveInt(request.get("page"), ProductQueryService.DEFAULT_PAGE);
            int size = parsePositiveInt(request.get("size"), ProductQueryService.DEFAULT_SIZE);

            log.info("收到商品查询请求: question={}, page={}, size={}, reuseSql={}",
                    question, page, size, !sql.isEmpty());

            Map<String, Object> queryResult = productQueryService.query(
                    question.isEmpty() ? null : question,
                    sql.isEmpty() ? null : sql,
                    page,
                    size
            );

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("question", question);
            body.put("sql", queryResult.get("sql"));
            body.put("pageSql", queryResult.get("pageSql"));
            body.put("data", queryResult.get("data"));
            body.put("total", queryResult.get("total"));
            body.put("page", queryResult.get("page"));
            body.put("size", queryResult.get("size"));
            return ResponseEntity.ok(body);

        } catch (IllegalArgumentException e) {
            log.warn("商品查询 SQL 校验失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("商品查询失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "查询失败: " + e.getMessage()
            ));
        }
    }

    private int parsePositiveInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
