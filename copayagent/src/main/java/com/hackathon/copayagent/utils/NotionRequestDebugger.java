package com.hackathon.copayagent.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpHeaders;

@Component
public class NotionRequestDebugger {
    
    private final WebClient notionWebClient;
    
    public NotionRequestDebugger(@Qualifier("notionWebClient") WebClient notionWebClient) {
        this.notionWebClient = notionWebClient;
    }
    
    @Value("${NOTION_TOKEN:}")
    private String notionToken;
    
    public void debugTokenRequest() {
        System.out.println("🔍 DEBUGGING NOTION REQUEST");
        System.out.println("============================");
        
        System.out.println("🔑 Token configurado: " + notionToken.substring(0, Math.min(20, notionToken.length())) + "...");
        System.out.println("🌐 Base URL: " + "https://api.notion.com/v1");
        System.out.println("📍 Endpoint: /users/me");
        System.out.println("📋 Headers:");
        System.out.println("   Authorization: Bearer " + notionToken.substring(0, Math.min(20, notionToken.length())) + "...");
        System.out.println("   Notion-Version: 2022-06-28");
        System.out.println("   Content-Type: application/json");
        
        try {
            System.out.println("\n🚀 Enviando petición...");
            
            String response = notionWebClient.get()
                    .uri("/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + notionToken)
                    .header("Notion-Version", "2022-06-28")
                    .header("Content-Type", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            System.out.println("✅ Respuesta exitosa:");
            System.out.println(response.substring(0, Math.min(200, response.length())) + "...");
            
        } catch (Exception e) {
            System.out.println("❌ Error en la petición:");
            System.out.println("   Tipo: " + e.getClass().getSimpleName());
            System.out.println("   Mensaje: " + e.getMessage());
            
            if (e.getMessage().contains("401")) {
                System.out.println("\n🔍 Análisis del error 401:");
                System.out.println("   - El token podría estar mal formateado");
                System.out.println("   - Podría haber caracteres extra (espacios, saltos de línea)");
                System.out.println("   - La URL podría ser incorrecta");
                
                // Verificar si hay caracteres extraños en el token
                if (notionToken.contains(" ") || notionToken.contains("\n") || notionToken.contains("\r")) {
                    System.out.println("   ⚠️ DETECTADO: El token contiene espacios o saltos de línea");
                    System.out.println("   Token limpio: '" + notionToken.trim() + "'");
                }
            }
        }
        
        System.out.println("\n💡 Comparación con Postman:");
        System.out.println("   - URL: https://api.notion.com/v1/users/me");
        System.out.println("   - Method: GET");
        System.out.println("   - Headers: Authorization (Bearer token), Notion-Version (2022-06-28)");
        System.out.println("   - Body: none");
    }
}
