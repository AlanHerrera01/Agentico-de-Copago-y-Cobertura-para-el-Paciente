package com.hackathon.copayagent.config;

import javax.net.ssl.SSLException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.netty.http.client.HttpClient;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

@Configuration
public class WebClientConfig {
        @Value("${OPENAI_BASE_URL:https://api.openai.com}")
        private String openAiBaseUrl;

    @Bean
    @Qualifier("openAiWebClient")
    public WebClient openAiWebClient() {
        return WebClient.builder()
                                .baseUrl(openAiBaseUrl)
                .build();
    }

    @Bean
    @Qualifier("groqWebClient")
    public WebClient groqWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.groq.com")
                .build();
    }

    @Bean
    @Qualifier("notionWebClient")
    public WebClient notionWebClient() throws SSLException {
        // Configurar SSL inseguro para desarrollo local
        io.netty.handler.ssl.SslContext sslContext = io.netty.handler.ssl.SslContextBuilder.forClient()
                .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
                .build();
        
        HttpClient httpClient = HttpClient.create()
                .secure(ssl -> ssl.sslContext(sslContext));
        
        return WebClient.builder()
                .baseUrl("https://api.notion.com/v1")
                .defaultHeader("Notion-Version", "2022-06-28")
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    @Primary
    public WebClient gitHubModelsWebClient() {
        try {
            // Configurar SSL para confiar en todos los certificados (solo para desarrollo)
            SslContext sslContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
            
            HttpClient httpClient = HttpClient.create()
                    .secure(ssl -> ssl.sslContext(sslContext));
            
            return WebClient.builder()
                    .baseUrl("https://models.inference.ai.azure.com")
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                    .build();
        } catch (Exception e) {
            // Fallback sin SSL personalizado si hay error
            return WebClient.builder()
                    .baseUrl("https://models.inference.ai.azure.com")
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
        }
    }
}
