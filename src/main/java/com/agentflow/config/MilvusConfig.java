package com.agentflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;

/**
 * Milvus 客户端与 Collection 初始化。
 * <p>
 * 仅在 {@code vector-store.type=milvus} 时生效。
 */
@Configuration
@ConditionalOnProperty(name = "vector-store.type", havingValue = "milvus")
public class MilvusConfig {

    private static final Logger log = LoggerFactory.getLogger(MilvusConfig.class);

    @Value("${vector-store.milvus.uri:http://localhost:19530}")
    private String uri;

    @Value("${vector-store.milvus.token:}")
    private String token;

    @Value("${vector-store.milvus.username:root}")
    private String username;

    @Value("${vector-store.milvus.password:Milvus}")
    private String password;

    @Value("${vector-store.milvus.database:default}")
    private String database;

    @Value("${vector-store.milvus.dimension:1024}")
    private int dimension;

    @Value("${vector-store.milvus.collection-document-chunk:document_chunk}")
    private String collectionDocumentChunk;

    @Value("${vector-store.milvus.collection-langchain4j:document_chunk_langchain4j}")
    private String collectionLangchain4j;

    @Value("${vector-store.milvus.collection-v3:document_chunk_v3}")
    private String collectionV3;

    @Bean(destroyMethod = "close")
    public MilvusClientV2 milvusClientV2() {
        ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder().uri(uri);
        if (token != null && !token.isBlank()) {
            builder.token(token);
        } else if (username != null && !username.isBlank()) {
            builder.username(username).password(password != null ? password : "");
        }
        if (database != null && !database.isBlank()) {
            builder.dbName(database);
        }

        MilvusClientV2 client = new MilvusClientV2(builder.build());
        log.info("Milvus 客户端已连接: uri={}, database={}", uri, database);

        ensureCollection(client, collectionDocumentChunk);
        ensureCollection(client, collectionLangchain4j);
        ensureCollection(client, collectionV3);
        return client;
    }

    private void ensureCollection(MilvusClientV2 milvusClient, String collectionName) {
        boolean exists = milvusClient.hasCollection(
                HasCollectionReq.builder().collectionName(collectionName).build());
        if (!exists) {
            CreateCollectionReq.CollectionSchema schema = milvusClient.createSchema();
            schema.addField(AddFieldReq.builder()
                    .fieldName("id")
                    .dataType(DataType.Int64)
                    .isPrimaryKey(true)
                    .autoID(true)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("file_name")
                    .dataType(DataType.VarChar)
                    .maxLength(512)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("chunk_index")
                    .dataType(DataType.Int32)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("chunk_text")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("embedding")
                    .dataType(DataType.FloatVector)
                    .dimension(dimension)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("created_at")
                    .dataType(DataType.Int64)
                    .build());

            IndexParam indexParam = IndexParam.builder()
                    .fieldName("embedding")
                    .metricType(IndexParam.MetricType.COSINE)
                    .build();

            milvusClient.createCollection(CreateCollectionReq.builder()
                    .collectionName(collectionName)
                    .collectionSchema(schema)
                    .indexParams(java.util.Collections.singletonList(indexParam))
                    .build());
            log.info("已创建 Milvus collection: {}，dimension={}", collectionName, dimension);
        } else {
            log.info("Milvus collection 已存在: {}", collectionName);
        }

        milvusClient.loadCollection(LoadCollectionReq.builder()
                .collectionName(collectionName)
                .build());
    }
}
