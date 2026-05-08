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
public class OpenAiClient implements AiClient {
    private final WebClient openAiWebClient;

    @Value("${OPENAI_API_KEY:}")
    private String apiKey;

    public Mono<OpenAiResponse> getSpecialtyAndPriority(OpenAiRequest request) {
        // Debug para verificar API key
        System.out.println("DEBUG - OpenAI API Key configurada: " + (apiKey != null && !apiKey.trim().isEmpty() ? "SI" : "NO"));
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return Mono.error(new RuntimeException("OpenAI API key no configurada"));
        }
        
        return openAiWebClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OpenAiResponse.class)
                .timeout(java.time.Duration.ofSeconds(20))
                // Retraso exponencial para manejar límite 429
                .retryWhen(reactor.util.retry.Retry.backoff(3, java.time.Duration.ofSeconds(2))
                    .maxBackoff(java.time.Duration.ofSeconds(10))
                    .doBeforeRetry(retrySignal -> {
                        System.out.println("Reintentando OpenAI... intento: " + (retrySignal.totalRetries() + 1));
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
                    System.err.println("OpenAI API error: " + ex.getMessage());
                    System.err.println("Status: " + ex.getStatusCode());
                    // Si es 429, dar mensaje específico
                    if (ex.getStatusCode().value() == 429) {
                        System.err.println("Límite de cuota de OpenAI alcanzado. Usando fallback.");
                    }
                    return Mono.error(new RuntimeException("OpenAI API error: " + ex.getMessage()));
                })
                .onErrorResume(ex -> {
                    System.err.println("OpenAI error general: " + ex.getMessage());
                    return Mono.error(new RuntimeException("OpenAI error: " + ex.getMessage()));
                });
    }
}
