package com.hackathon.copayagent.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.hackathon.copayagent.dto.OpenAiRequest;
import com.hackathon.copayagent.dto.OpenAiResponse;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class GitHubModelsClient implements AiClient {
    private final WebClient gitHubModelsWebClient;

    @Value("${GITHUB_MODELS_API_KEY:}")
    private String apiKey;

    public Mono<OpenAiResponse> getSpecialtyAndPriority(OpenAiRequest request) {
        // Debug para verificar API key
        System.out.println("DEBUG - GitHub Models API Key configurada: " + (apiKey != null && !apiKey.trim().isEmpty() ? "SI" : "NO"));
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return Mono.error(new RuntimeException("GitHub Models API key no configurada"));
        }
        
        // Modificar el request para usar modelo de GitHub Models
        OpenAiRequest githubModelsRequest = OpenAiRequest.builder()
            .model("gpt-4o-mini") // Modelo GPT-4o-mini de GitHub Models
            .messages(request.getMessages())
            .maxTokens(request.getMaxTokens()) // El DTO ahora mapea a max automáticamente
            .temperature(request.getTemperature())
            .build();
        
        return gitHubModelsWebClient.post()
                .uri("/chat/completions") // Endpoint de GitHub Models
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(githubModelsRequest)
                .retrieve()
                .bodyToMono(OpenAiResponse.class)
                .timeout(java.time.Duration.ofSeconds(30))
                // Retraso exponencial para manejar límite 429
                .retryWhen(reactor.util.retry.Retry.backoff(3, java.time.Duration.ofSeconds(2))
                    .maxBackoff(java.time.Duration.ofSeconds(10))
                    .doBeforeRetry(retrySignal -> {
                        System.out.println("Reintentando GitHub Models... intento: " + (retrySignal.totalRetries() + 1));
                        System.out.println("Esperando " + (2 * Math.pow(2, retrySignal.totalRetries())) + " segundos");
                    })
                    .filter(throwable -> {
                        // Solo reintentar en caso de 429 o errores de red
                        if (throwable instanceof WebClientResponseException) {
                            WebClientResponseException ex = (WebClientResponseException) throwable;
                            return ex.getStatusCode().value() == 429 || 
                                   ex.getStatusCode().is5xxServerError();
                        }
                        return true;
                    })
                )
                .onErrorResume(WebClientResponseException.class, ex -> {
                    System.err.println("GitHub Models API error: " + ex.getMessage());
                    System.err.println("Status: " + ex.getStatusCode());
                    System.err.println("Response: " + ex.getResponseBodyAsString());
                    // Si es 401, dar mensaje específico
                    if (ex.getStatusCode().value() == 401) {
                        System.err.println("Error de autenticación con GitHub Models. Verifica tu API key.");
                    }
                    return Mono.error(new RuntimeException("GitHub Models API error: " + ex.getMessage()));
                })
                .onErrorResume(ex -> {
                    System.err.println("GitHub Models error general: " + ex.getMessage());
                    return Mono.error(new RuntimeException("GitHub Models error: " + ex.getMessage()));
                });
    }
}
