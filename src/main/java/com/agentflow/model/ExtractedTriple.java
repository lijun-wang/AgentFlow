package com.agentflow.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * LLM 从文档分块中抽取的知识三元组
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractedTriple {

    /** 主语实体名称 */
    private String subject;
    /** 主语实体类型：Person/Org/Concept/Location/Event/Product/Document/DocSection */
    private String subjectType;
    /** 关系谓词（受控词表） */
    private String predicate;
    /** 宾语实体名称 */
    private String object;
    /** 宾语实体类型：Person/Org/Concept/Location/Event/Product/Document/DocSection */
    private String objectType;
    /** 抽取置信度，范围 0~1 */
    private Double confidence;

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getPredicate() {
        return predicate;
    }

    public void setPredicate(String predicate) {
        this.predicate = predicate;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }
}
