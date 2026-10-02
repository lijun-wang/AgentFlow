package com.vertor.model;

import java.time.LocalDateTime;

/**
 * 文档分块模型，对应数据库 document_chunk 表
 */
public class DocumentChunk {

    /** 主键ID */
    private Long id;
    /** 上传的文件名 */
    private String fileName;
    /** 分块在文档中的序号（从0开始） */
    private Integer chunkIndex;
    /** 分块的文本内容 */
    private String chunkText;
    /** 文本对应的嵌入向量（维度由模型决定，如1024） */
    private float[] embedding;
    /** 余弦距离：查询向量与该分块向量的余弦距离，范围[0,2]，值越小越相似（仅搜索时填充） */
    private Double cosineDistance;
    /** 相似度：1 - cosineDistance，范围[-1,1]，值越大越相似（仅搜索时填充） */
    private Double similarity;
    /** 记录创建时间 */
    private LocalDateTime createdAt;

    public DocumentChunk() {
    }

    public DocumentChunk(String fileName, Integer chunkIndex, String chunkText, float[] embedding) {
        this.fileName = fileName;
        this.chunkIndex = chunkIndex;
        this.chunkText = chunkText;
        this.embedding = embedding;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getChunkText() {
        return chunkText;
    }

    public void setChunkText(String chunkText) {
        this.chunkText = chunkText;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public Double getCosineDistance() {
        return cosineDistance;
    }

    public void setCosineDistance(Double cosineDistance) {
        this.cosineDistance = cosineDistance;
    }

    public Double getSimilarity() {
        return similarity;
    }

    public void setSimilarity(Double similarity) {
        this.similarity = similarity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
