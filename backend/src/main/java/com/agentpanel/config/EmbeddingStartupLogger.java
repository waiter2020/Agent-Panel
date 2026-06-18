package com.agentpanel.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingStartupLogger implements ApplicationRunner {

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final MemoryProperties memoryProperties;

    @Override
    public void run(ApplicationArguments args) {
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model != null) {
            log.info("EmbeddingModel available: {} — shared memory will use vector similarity search (dim={})",
                    model.getClass().getSimpleName(), memoryProperties.getEmbeddingDimensions());
        } else {
            log.warn("EmbeddingModel not available — shared memory will store text only and fall back to keyword search. "
                    + "Enable spring.ai.openai or spring.ai.ollama embedding in application.yml to activate vectors.");
        }
    }
}
