package com.aiexam.aigeneratorservice.config;

import java.util.Map;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Builds the provider-specific {@link ChatOptions} that requests a strict JSON-Schema response,
 * since OpenAI and Ollama expose Structured Outputs through different option shapes.
 */
public interface StructuredOutputOptionsFactory {

    ChatOptions create(String jsonSchema, Map<String, Object> jsonSchemaMap);
}
