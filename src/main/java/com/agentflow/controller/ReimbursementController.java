package com.agentflow.controller;

import com.agentflow.service.ReimbursementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * LangGraph4j 报销流程控制器：提供基于状态图工作流的报销审批接口
 *
 * 通过 POST /api/reimbursement/process 接收报销申请�? * 驱动 LangGraph4j 报销工作流（提交申请 �?部门审批 �?财务审核 �?完成报销）执行并返回结果
 */
@Tag(name = "LangGraph4j 报销流程", description = "基于 LangGraph4j 状态图工作流的报销审批 Demo 接口")
@RestController
@RequestMapping("/api/reimbursement")
public class ReimbursementController {

    private static final Logger log = LoggerFactory.getLogger(ReimbursementController.class);

    private final ReimbursementService reimbursementService;

    public ReimbursementController(ReimbursementService reimbursementService) {
        this.reimbursementService = reimbursementService;
    }

    /**
     * 提交报销申请�?LangGraph4j 报销工作流进行处�?     */
    @Operation(
            summary = "提交报销申请",
            description = "基于 LangGraph4j 状态图工作流，依次执行提交申请、部门审批、财务审核、完成报销四个节点，返回最终结果及各步骤中间结果。审批节点可能被 LLM 判定为驳回，此时流程提前终止�?
    )
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> process(@RequestBody Map<String, String> request) {
        try {
            String reimbursementRequest = request.get("request");
            if (reimbursementRequest == null || reimbursementRequest.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "报销申请内容不能为空"
                ));
            }

            log.info("报销流程工作流收到申�? {}", reimbursementRequest);

            Map<String, Object> result = reimbursementService.process(reimbursementRequest);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("报销流程工作流处理失�?, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "处理失败: " + e.getMessage()
            ));
        }
    }
}
