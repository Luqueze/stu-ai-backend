package com.aiexam.aigeneratorservice.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResponseErrorHandler;

/**
 * Provider selection is manual (not Spring AI's auto-configuration) so that both the OpenAI and
 * Ollama starters can coexist on the classpath without bean ambiguity: {@code
 * OpenAiAutoConfiguration}/{@code OllamaAutoConfiguration} are excluded (see application.yml) and
 * exactly one {@link ChatModel} bean is created here based on {@code app.ai.provider}.
 */
@Configuration
public class AiProviderConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "openai", matchIfMissing = true)
    public ChatModel openAiChatModel(
            RetryTemplate retryTemplate,
            ResponseErrorHandler responseErrorHandler,
            @Value("${spring.ai.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String model,
            @Value("${spring.ai.openai.chat.options.temperature:0.7}") Double temperature) {
        OpenAiApi openAiApi =
                OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).responseErrorHandler(responseErrorHandler).build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model(model).temperature(temperature).build())
                .retryTemplate(retryTemplate)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "ollama")
    public ChatModel ollamaChatModel(
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${spring.ai.ollama.chat.options.model:qwen2.5:14b}") String model,
            @Value("${spring.ai.ollama.chat.options.temperature:0.7}") Double temperature) {
        OllamaApi ollamaApi = new OllamaApi(baseUrl);

        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaOptions.builder().model(model).temperature(temperature).build())
                .build();
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "openai", matchIfMissing = true)
    public StructuredOutputOptionsFactory openAiStructuredOutputOptionsFactory() {
        return (jsonSchema, jsonSchemaMap) ->
                OpenAiChatOptions.builder()
                        .responseFormat(
                                ResponseFormat.builder()
                                        .type(ResponseFormat.Type.JSON_SCHEMA)
                                        .jsonSchema(jsonSchema)
                                        .build())
                        .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "ollama")
    public StructuredOutputOptionsFactory ollamaStructuredOutputOptionsFactory() {
        return (jsonSchema, jsonSchemaMap) -> OllamaOptions.builder().format(jsonSchemaMap).build();
    }
}
