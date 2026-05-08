package com.hackathon.copayagent.utils;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpHeaders;

import lombok.RequiredArgsConstructor;

@Component
public class NotionPermissionChecker {
    
    public final WebClient notionWebClient;
    
    public NotionPermissionChecker(@Qualifier("notionWebClient") WebClient notionWebClient) {
        this.notionWebClient = notionWebClient;
    }
    
    @Value("${NOTION_TOKEN:}")
    private String notionToken;
    
    public void checkIntegrationPermissions() {
        System.out.println("🔍 VERIFICANDO PERMISOS DE INTEGRACIÓN NOTION");
        System.out.println("==========================================");
        
        if (notionToken == null || notionToken.isEmpty()) {
            System.out.println("❌ Token no configurado");
            return;
        }
        
        // 1. Verificar que el token es válido
        checkTokenValidity();
        
        // 2. Listar bases de datos accesibles
        listAccessibleDatabases();
        
        System.out.println();
        System.out.println("💡 Si algunas bases de datos no aparecen, debes:");
        System.out.println("   1. Ir a cada base de datos en Notion");
        System.out.println("   2. Hacer clic en 'Share' (Compartir)");
        System.out.println("   3. Invitar a tu integración por nombre");
        System.out.println("   4. Dar permisos 'Full access'");
    }
    
    private void checkTokenValidity() {
        System.out.println("🔑 Verificando validez del token...");
        
        try {
            String response = notionWebClient.get()
                    .uri("/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + notionToken)
                    .header("Notion-Version", "2022-06-28")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            if (response != null && response.contains("id")) {
                System.out.println("✅ Token válido y activo");
            } else {
                System.out.println("⚠️ Respuesta inesperada del token");
            }
        } catch (Exception e) {
            System.out.println("❌ Token inválido o inactivo: " + e.getMessage());
        }
    }
    
    private void listAccessibleDatabases() {
        System.out.println("📊 Listando bases de datos accesibles...");
        
        try {
            String response = notionWebClient.post()
                    .uri("/search")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + notionToken)
                    .header("Notion-Version", "2022-06-28")
                    .header("Content-Type", "application/json")
                    .bodyValue("{\"filter\":{\"property\":\"object\",\"value\":\"database\"}}")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            if (response != null && response.contains("\"results\"")) {
                System.out.println("✅ Bases de datos accesibles:");
                
                // Extraer nombres de bases de datos (parseo simple)
                String[] results = response.split("\"title\":\\[");
                for (int i = 1; i < results.length; i++) {
                    int end = results[i].indexOf("\"plain_text\":\"");
                    if (end != -1) {
                        int start = results[i].indexOf("\"plain_text\":\"", end) + 15;
                        int finish = results[i].indexOf("\"", start);
                        if (finish != -1) {
                            String dbName = results[i].substring(start, finish);
                            System.out.println("   - " + dbName);
                        }
                    }
                }
            } else {
                System.out.println("⚠️ No se encontraron bases de datos accesibles");
            }
        } catch (Exception e) {
            System.out.println("❌ Error listando bases de datos: " + e.getMessage());
        }
    }
}
