package com.sujan.tripitaka.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Qdrant connection. Spring AI's default single-collection store is excluded (application.yaml), so its
 * connection properties are bound here; VectorStores opens one store per logical collection.
 */
@Configuration
@EnableConfigurationProperties(QdrantVectorStoreProperties.class)
public class QdrantConfig {

    @Bean(destroyMethod = "close")
    public QdrantClient qdrantClient(QdrantVectorStoreProperties properties) {
        var builder = QdrantGrpcClient.newBuilder(
                        properties.getHost(), properties.getPort(), properties.isUseTls())
                .withTimeout(Duration.ofSeconds(10));

        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.withApiKey(properties.getApiKey());
        }
        return new QdrantClient(builder.build());
    }
}
