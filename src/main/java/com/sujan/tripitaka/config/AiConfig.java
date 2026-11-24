package com.sujan.tripitaka.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class AiConfig {

    @Bean
    public JdbcChatMemoryRepository chatMemoryRepository(DataSource dataSource,
                                                         PlatformTransactionManager transactionManager) {
        return JdbcChatMemoryRepository.builder()
                .dataSource(dataSource)
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(12)
                .build();
    }

    /**
     * Answers questions, on its own (usually stronger) model: this is where depth matters.
     * Memory stores only the user's question and the answer, not retrieved passages.
     */
    @Bean
    public ChatClient answerClient(ChatClient.Builder builder, ChatMemory chatMemory,
                                   @Value("${rag.answer-model}") String answerModel) {
        return builder
                .defaultOptions(OpenAiChatOptions.builder().model(answerModel))
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /** Rewrites follow-up questions into standalone search queries on the cheap default model. No memory, no tools. */
    @Bean
    public ChatClient rewriteClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
