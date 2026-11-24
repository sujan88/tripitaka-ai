package com.sujan.tripitaka.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RerankConfig {

    @Bean("rerankRestClient")
    public RestClient rerankRestClient(RestClient.Builder builder,
                                       @Value("${rag.rerank.enabled:false}") boolean enabled,
                                       @Value("${rag.rerank.api-key:}") String apiKey) {
        if (enabled && apiKey.isBlank()) {
            throw new IllegalStateException("COHERE_API_KEY is required when reranking is enabled");
        }

        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        return builder
                .baseUrl("https://api.cohere.com/v2")
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> {
                    if (!apiKey.isBlank()) {
                        headers.setBearerAuth(apiKey);
                    }
                })
                .build();
    }
}
