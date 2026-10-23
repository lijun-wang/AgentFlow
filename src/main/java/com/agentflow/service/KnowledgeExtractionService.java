package com.agentflow.service;

import com.agentflow.model.ExtractedTriple;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 按块调用 DeepSeek 抽取三元组，并按词表、置信度、原文提及做校验
 */
@Service
public class KnowledgeExtractionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeExtractionService.class);

    private final ChatService chatService;
    private final ObjectMapper objectMapper;
    private final double confidenceThreshold;
    private final boolean requireMentionInChunk;
    private final Set<String> allowedTypes;
    private final Set<String> allowedPredicates;
    private final String allowedTypesLabel;
    private final String allowedPredicatesLabel;

    public KnowledgeExtractionService(
            ChatService chatService,
            ObjectMapper objectMapper,
            @Value("${knowledge-graph.confidence-threshold:0.6}") double confidenceThreshold,
            @Value("${knowledge-graph.require-mention-in-chunk:true}") boolean requireMentionInChunk,
            @Value("${knowledge-graph.allowed-types:Person,Org,Concept,Location,Event,Product}") String allowedTypesRaw,
            @Value("${knowledge-graph.allowed-predicates:属于,包含,依赖,导致,位于,参与,相关,任职于}") String allowedPredicatesRaw) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
        this.confidenceThreshold = confidenceThreshold;
        this.requireMentionInChunk = requireMentionInChunk;
        this.allowedTypesLabel = allowedTypesRaw;
        this.allowedPredicatesLabel = allowedPredicatesRaw;
        this.allowedTypes = splitToSet(allowedTypesRaw);
        this.allowedPredicates = splitToSet(allowedPredicatesRaw);
    }

    /**
     * 从单个分块抽取并过滤三元组
     */
    public List<ExtractedTriple> extractFromChunk(String chunkText) {
        if (chunkText == null || chunkText.isBlank()) {
            return List.of();
        }

        String raw = chatService.extractTriplesJson(chunkText, allowedTypesLabel, allowedPredicatesLabel);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        List<ExtractedTriple> parsed = parseTriples(raw);
        List<ExtractedTriple> valid = new ArrayList<>();
        for (ExtractedTriple triple : parsed) {
            if (isValid(triple, chunkText)) {
                valid.add(triple);
            }
        }
        log.info("分块抽取三元组：原始 {} 条，校验后 {} 条", parsed.size(), valid.size());
        return valid;
    }

    private List<ExtractedTriple> parseTriples(String raw) {
        try {
            String json = unwrapJson(raw);
            TriplePayload payload = objectMapper.readValue(json, TriplePayload.class);
            if (payload.triples == null) {
                return List.of();
            }
            return payload.triples;
        } catch (Exception e) {
            log.warn("解析三元组 JSON 失败: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean isValid(ExtractedTriple triple, String chunkText) {
        if (triple == null) {
            return false;
        }
        String subject = trimToNull(triple.getSubject());
        String object = trimToNull(triple.getObject());
        String predicate = trimToNull(triple.getPredicate());
        if (subject == null || object == null || predicate == null) {
            return false;
        }
        if (subject.equals(object)) {
            return false;
        }

        String subjectType = normalizeType(triple.getSubjectType());
        String objectType = normalizeType(triple.getObjectType());
        if (subjectType == null || objectType == null) {
            return false;
        }
        if (!allowedPredicates.contains(predicate)) {
            return false;
        }

        double confidence = triple.getConfidence() != null ? triple.getConfidence() : 0.0;
        if (confidence < confidenceThreshold) {
            return false;
        }

        if (requireMentionInChunk) {
            String haystack = chunkText.toLowerCase(Locale.ROOT);
            boolean mentioned = haystack.contains(subject.toLowerCase(Locale.ROOT))
                    || haystack.contains(object.toLowerCase(Locale.ROOT));
            if (!mentioned) {
                return false;
            }
        }

        triple.setSubject(subject);
        triple.setObject(object);
        triple.setPredicate(predicate);
        triple.setSubjectType(subjectType);
        triple.setObjectType(objectType);
        triple.setConfidence(confidence);
        return true;
    }

    private String normalizeType(String type) {
        String trimmed = trimToNull(type);
        if (trimmed == null) {
            return "Concept";
        }
        for (String allowed : allowedTypes) {
            if (allowed.equalsIgnoreCase(trimmed)) {
                return allowed;
            }
        }
        return null;
    }

    private String unwrapJson(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            int start = s.indexOf('\n');
            int end = s.lastIndexOf("```");
            if (start >= 0 && end > start) {
                s = s.substring(start + 1, end).trim();
            }
        }
        int i = s.indexOf('{');
        int j = s.lastIndexOf('}');
        if (i >= 0 && j > i) {
            s = s.substring(i, j + 1);
        }
        return s;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Set<String> splitToSet(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TriplePayload {
        public List<ExtractedTriple> triples;
    }
}
