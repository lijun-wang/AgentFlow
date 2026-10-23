package com.agentflow.model;

/**
 * 知识图谱实体，对应 kg_entity 表
 */
public class KgEntity {

    /** 主键 ID */
    private Long id;
    /** 实体展示名称 */
    private String name;
    /** 规范化名称，用于跨文档对齐 */
    private String canonicalName;
    /** 实体类型：Person/Org/Concept/Location/Event/Product/Document/DocSection */
    private String type;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    public void setCanonicalName(String canonicalName) {
        this.canonicalName = canonicalName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
