package com.hackathon.copayagent.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpHeaders;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NotionDiagnostics {
    
    private final WebClient notionWebClient;
    
    @Value("${notion.token:}")
    private String notionToken;
    
    @Value("${notion.database.pacientes:}")
    private String pacientesDbId;
    
    @Value("${notion.database.planes:}")
    private String planesDbId;
    
    @Value("${notion.database.hospitales:}")
    private String hospitalesDbId;
    
    @Value("${notion.database.especialidades:}")
    private String especialidadesDbId;
    
    public void diagnoseDatabaseIds() {
        System.out.println("🔍 DIAGNÓSTICO DE BASES DE DATOS NOTION");
        System.out.println("=====================================");
        
        if (notionToken == null || notionToken.isEmpty()) {
            System.out.println("❌ Token de Notion no configurado");
            return;
        }
        
        System.out.println("✅ Token configurado: " + notionToken.substring(0, Math.min(10, notionToken.length())) + "...");
        System.out.println();
        
        // Verificar cada database ID
        checkDatabase("Pacientes", pacientesDbId);
        checkDatabase("Planes", planesDbId);
        checkDatabase("Hospitales", hospitalesDbId);
        checkDatabase("Especialidades", especialidadesDbId);
        
        System.out.println();
        System.out.println("💡 Si todos muestran 404, verifica:");
        System.out.println("   1. Que los IDs sean correctos (32 caracteres hexadecimales)");
        System.out.println("   2. Que el token tenga acceso a estas bases de datos");
        System.out.println("   3. Que las bases de datos existan en tu workspace de Notion");
    }
    
    private void checkDatabase(String name, String dbId) {
        System.out.println("📊 Verificando " + name + ":");
        System.out.println("   ID: " + dbId);
        
        if (dbId == null || dbId.isEmpty()) {
            System.out.println("   ❌ ID no configurado");
            return;
        }
        
        if (dbId.length() != 32) {
            System.out.println("   ⚠️ ID con longitud incorrecta (debe ser 32 caracteres)");
            return;
        }
        
        try {
            String response = notionWebClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/databases/{databaseId}/query").build(dbId))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + notionToken)
                    .header("Notion-Version", "2022-06-28")
                    .header("Content-Type", "application/json")
                    .bodyValue("{}")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            if (response != null && response.contains("\"results\"")) {
                System.out.println("   ✅ Base de datos accesible");
            } else {
                System.out.println("   ⚠️ Respuesta inesperada: " + response.substring(0, Math.min(100, response.length())));
            }
        } catch (Exception e) {
            if (e.getMessage().contains("404")) {
                System.out.println("   ❌ 404 - Base de datos no encontrada o sin acceso");
            } else if (e.getMessage().contains("401") || e.getMessage().contains("403")) {
                System.out.println("   ❌ 401/403 - Sin permisos para esta base de datos");
            } else {
                System.out.println("   ❌ Error: " + e.getMessage());
            }
        }
    }
}
