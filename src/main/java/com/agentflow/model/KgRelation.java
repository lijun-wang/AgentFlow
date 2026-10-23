package com.agentflow.model;

/**
 * 知识图谱关系，对应 kg_relation 表（含展示用的两端实体名）
 */
public class KgRelation {

    /** 主键 ID */
    private Long id;
    /** 起始实体 ID（主语） */
    private Long fromEntityId;
    /** 起始实体展示名称（查询展示用，非表字段） */
    private String fromName;
    /** 起始实体类型（查询展示用，非表字段） */
    private String fromType;
    /** 关系谓词（受控词表） */
    private String predicate;
    /** 目标实体 ID（宾语） */
    private Long toEntityId;
    /** 目标实体展示名称（查询展示用，非表字段） */
    private String toName;
    /** 目标实体类型（查询展示用，非表字段） */
    private String toType;
    /** 抽取置信度，范围 0~1 */
    private Double confidence;
    /** 来源分块 ID */
    private Long chunkId;
    /** 来源文件名 */
    private String fileName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFromEntityId() {
        return fromEntityId;
    }

    public void setFromEntityId(Long fromEntityId) {
        this.fromEntityId = fromEntityId;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getFromType() {
        return fromType;
    }

    public void setFromType(String fromType) {
        this.fromType = fromType;
    }

    public String getPredicate() {
        return predicate;
    }

    public void setPredicate(String predicate) {
        this.predicate = predicate;
    }

    public Long getToEntityId() {
        return toEntityId;
    }

    public void setToEntityId(Long toEntityId) {
        this.toEntityId = toEntityId;
    }

    public String getToName() {
        return toName;
    }

    public void setToName(String toName) {
        this.toName = toName;
    }

    public String getToType() {
        return toType;
    }

    public void setToType(String toType) {
        this.toType = toType;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
