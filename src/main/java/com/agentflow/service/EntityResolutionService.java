package com.agentflow.service;

import org.springframework.stereotype.Service;

/**
 * 实体对齐：一期仅做名称规范化，精确匹配交给数据库唯一约束
 */
@Service
public class EntityResolutionService {

    /**
     * 规范化实体名：去首尾空白、压缩连续空白
     */
    public String canonicalize(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().replaceAll("\\s+", " ");
    }
}
