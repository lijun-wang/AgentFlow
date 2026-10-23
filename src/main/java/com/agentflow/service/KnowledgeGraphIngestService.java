package com.agentflow.service;

import com.agentflow.model.ExtractedTriple;
import com.agentflow.model.KgEntity;
import com.agentflow.model.KgRelation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档导入 3.0：切块、向量化，并抽取知识图谱
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "database.available", havingValue = "true")
public class KnowledgeGraphIngestService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphIngestService.class);

    private final TextExtractionService textExtractionService;
    private final EmbeddingService embeddingService;
    private final KnowledgeExtractionService knowledgeExtractionService;
    private final EntityResolutionService entityResolutionService;
    private final KnowledgeGraphStore knowledgeGraphStore;

    public KnowledgeGraphIngestService(TextExtractionService textExtractionService,
                                       EmbeddingService embeddingService,
                                       KnowledgeExtractionService knowledgeExtractionService,
                                       EntityResolutionService entityResolutionService,
                                       KnowledgeGraphStore knowledgeGraphStore) {
        this.textExtractionService = textExtractionService;
        this.embeddingService = embeddingService;
        this.knowledgeExtractionService = knowledgeExtractionService;
        this.entityResolutionService = entityResolutionService;
        this.knowledgeGraphStore = knowledgeGraphStore;
    }

    /**
     * 处理上传文件：分块入库 + 结构边 + LLM 三元组
     *
     * @return 含 fileName、chunkCount、entityCount、relationCount
     */
    public Map<String, Object> processAndStore(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        log.info("开始知识图谱导入: {}", fileName);

        List<String> chunks = textExtractionService.extractAndChunk(file);
        if (chunks.isEmpty()) {
            return Map.of(
                    "fileName", fileName,
                    "chunkCount", 0,
                    "entityCount", 0,
                    "relationCount", 0
            );
        }

        List<float[]> embeddings = embeddingService.embedTexts(chunks);
        List<Long> chunkIds = knowledgeGraphStore.saveChunks(fileName, chunks, embeddings);

        long documentEntityId = knowledgeGraphStore.upsertEntity(
                fileName,
                entityResolutionService.canonicalize(fileName),
                "Document"
        );

        Long previousSectionId = null;
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            Long chunkId = chunkIds.get(i);
            String sectionName = sectionNameOf(chunk, i);
            String sectionCanonical = entityResolutionService.canonicalize(fileName + "#" + i + ":" + sectionName);
            long sectionId = knowledgeGraphStore.upsertEntity(sectionName, sectionCanonical, "DocSection");

            knowledgeGraphStore.saveRelation(documentEntityId, "包含", sectionId, 1.0, chunkId, fileName);
            if (previousSectionId != null) {
                knowledgeGraphStore.saveRelation(previousSectionId, "下一节", sectionId, 1.0, chunkId, fileName);
            }
            previousSectionId = sectionId;

            List<ExtractedTriple> triples = knowledgeExtractionService.extractFromChunk(chunk);
            for (ExtractedTriple triple : triples) {
                long subjectId = upsertFactEntity(triple.getSubject(), triple.getSubjectType());
                long objectId = upsertFactEntity(triple.getObject(), triple.getObjectType());
                if (subjectId == objectId) {
                    continue;
                }
                knowledgeGraphStore.saveRelation(
                        subjectId, triple.getPredicate(), objectId,
                        triple.getConfidence(), chunkId, fileName
                );
                knowledgeGraphStore.saveRelation(sectionId, "提及", subjectId, 1.0, chunkId, fileName);
                knowledgeGraphStore.saveRelation(sectionId, "提及", objectId, 1.0, chunkId, fileName);
            }
        }

        KnowledgeGraphStore.GraphSnapshot snapshot = knowledgeGraphStore.loadGraph(fileName);
        log.info("知识图谱导入完成: {}，分块 {}，实体 {}，关系 {}",
                fileName, chunks.size(), snapshot.entities().size(), snapshot.relations().size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", fileName);
        result.put("chunkCount", chunks.size());
        result.put("entityCount", snapshot.entities().size());
        result.put("relationCount", snapshot.relations().size());
        return result;
    }

    /**
     * 按文件名返回图谱快照
     */
    public Map<String, Object> loadGraph(String fileName) {
        KnowledgeGraphStore.GraphSnapshot snapshot = knowledgeGraphStore.loadGraph(fileName);
        List<Map<String, Object>> entities = snapshot.entities().stream()
                .map(this::toEntityMap)
                .toList();
        List<Map<String, Object>> relations = snapshot.relations().stream()
                .map(this::toRelationMap)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", fileName);
        result.put("entityCount", entities.size());
        result.put("relationCount", relations.size());
        result.put("entities", entities);
        result.put("relations", relations);
        return result;
    }

    private long upsertFactEntity(String name, String type) {
        String display = entityResolutionService.canonicalize(name);
        return knowledgeGraphStore.upsertEntity(display, display, type);
    }

    private String sectionNameOf(String chunk, int index) {
        String firstLine = chunk.lines()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse("分块 " + index);
        if (firstLine.length() > 200) {
            return firstLine.substring(0, 200);
        }
        return firstLine;
    }

    private Map<String, Object> toEntityMap(KgEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("name", entity.getName());
        map.put("canonicalName", entity.getCanonicalName());
        map.put("type", entity.getType());
        return map;
    }

    private Map<String, Object> toRelationMap(KgRelation relation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", relation.getId());
        map.put("fromEntityId", relation.getFromEntityId());
        map.put("fromName", relation.getFromName());
        map.put("fromType", relation.getFromType());
        map.put("predicate", relation.getPredicate());
        map.put("toEntityId", relation.getToEntityId());
        map.put("toName", relation.getToName());
        map.put("toType", relation.getToType());
        map.put("confidence", relation.getConfidence());
        map.put("chunkId", relation.getChunkId());
        map.put("fileName", relation.getFileName());
        return map;
    }
}
