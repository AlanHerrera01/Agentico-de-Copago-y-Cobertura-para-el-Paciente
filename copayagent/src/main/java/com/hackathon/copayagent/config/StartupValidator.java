package com.hackathon.copayagent.config;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.hackathon.copayagent.client.GitHubModelsClient;
import com.hackathon.copayagent.client.NotionClient;
import com.hackathon.copayagent.dto.EspecialidadSintomaDto;
import com.hackathon.copayagent.utils.NotionDiagnostics;
import com.hackathon.copayagent.utils.NotionPermissionChecker;
import com.hackathon.copayagent.utils.NotionRequestDebugger;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class StartupValidator {
    private static final Logger log = LoggerFactory.getLogger(StartupValidator.class);
    
    private final GitHubModelsClient gitHubModelsClient;
    private final NotionClient notionClient;
    private final NotionDiagnostics notionDiagnostics;
    private final NotionPermissionChecker notionPermissionChecker;
    private final NotionRequestDebugger notionRequestDebugger;

    @Value("${AI_PROVIDER:openai}")
    private String aiProvider;

    @Value("${GITHUB_MODELS_API_KEY:}")
    private String githubModelsApiKey;

    @Value("${NOTION_TOKEN:}")
    private String notionToken;
    
    @Value("${notion.database.pacientes:}")
    private String pacientesDbId;
    
    @Value("${notion.database.planes:}")
    private String planesDbId;
    
    @Value("${notion.database.hospitales:}")
    private String hospitalesDbId;
    
    @Value("${notion.database.especialidades:}")
    private String especialidadesDbId;

    @PostConstruct
    public void validateConnections() {
        log.info("🚀 Iniciando validación de conexiones del sistema...");
        
        // Validar conexión con IA
        validateAIConnection();
        
        // Validar conexión con Notion
        validateNotionConnection();
        
        log.info("✅ Validación de conexiones completada. Sistema listo para operar.");
    }

    private void validateAIConnection() {
        log.info("🤖 Validando conexión con IA Provider: {}", aiProvider);
        
        if ("github-models".equalsIgnoreCase(aiProvider)) {
            if (githubModelsApiKey == null || githubModelsApiKey.trim().isEmpty()) {
                log.warn("⚠️ GitHub Models API Key no configurada - usando fallback");
            } else {
                log.info("✅ GitHub Models API Key configurada correctamente");
                // Validar conexión real con una petición de prueba con timeout más largo
                try {
                    gitHubModelsClient.getSpecialtyAndPriority(
                        com.hackathon.copayagent.dto.OpenAiRequest.builder()
                            .model("gpt-4o")
                            .messages(java.util.List.of(
                                new com.hackathon.copayagent.dto.OpenAiRequest.Message("user", "Test connection")
                            ))
                            .maxTokens(10)
                            .temperature(0.1)
                            .build()
                    )
                    .timeout(java.time.Duration.ofSeconds(15))
                    .doOnSuccess(response -> {
                        log.info("✅ Conexión con GitHub Models validada exitosamente");
                    })
                    .doOnError(error -> {
                        log.warn("⚠️ Error validando conexión con GitHub Models: {} - La aplicación continuará funcionando con fallbacks", error.getMessage());
                    })
                    .onErrorResume(error -> {
                        log.warn("⚠️ GitHub Models no disponible durante startup, se usará fallback durante runtime");
                        return Mono.empty();
                    })
                    .subscribe();
                } catch (Exception e) {
                    log.warn("⚠️ Error al validar GitHub Models: {} - La aplicación continuará funcionando", e.getMessage());
                }
            }
        } else {
            log.info("ℹ️ Usando provider de IA: {}", aiProvider);
        }
    }

    private void validateNotionConnection() {
        log.info("📊 Validando conexión con Notion...");
        
        if (notionToken == null || notionToken.trim().isEmpty()) {
            log.warn("⚠️ Notion Token no configurado - usando datos mock");
            return;
        }
        
        // 1. Debuggear la petición antes de validar
        log.info("🔍 Debuggeando petición de Notion...");
        notionRequestDebugger.debugTokenRequest();
        
        // 2. Primero validar el token y sus permisos
        log.info("🔑 Validando token Bearer de Notion...");
        boolean tokenValid = validateNotionToken();
        
        if (!tokenValid) {
            log.error("❌ Token de Notion inválido - no se puede continuar");
            return;
        }
        
        // 2. Verificar permisos específicos para cada base de datos
        log.info("🔍 Verificando permisos del token en cada base de datos...");
        validateDatabasePermissions();
        
        // 3. Solo si los permisos son válidos, cargar datos
        log.info("📊 Cargando datos de bases de datos con acceso...");
        validateNotionTable("Especialidades", notionClient.getEspecialidades());
        validateNotionTable("Pacientes", notionClient.getAllPacientes());
        validateNotionTable("Planes", notionClient.getAllPlanes());
        validateNotionTable("Hospitales", notionClient.getAllHospitales());
    }
    
    private boolean validateNotionToken() {
        try {
            String response = notionPermissionChecker.notionWebClient.get()
                    .uri("/users/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + notionToken)
                    .header("Notion-Version", "2022-06-28")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            if (response != null && response.contains("\"id\"")) {
                log.info("✅ Token Bearer de Notion válido y activo");
                return true;
            } else {
                log.error("❌ Respuesta inesperada del token");
                return false;
            }
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg.contains("401") && errorMsg.contains("invalid")) {
                log.error("❌ Token Bearer inválido o expirado");
                log.error("   💡 Verifica: https://www.notion.so/my-integrations");
                log.error("   💡 Copia el token exacto del 'Internal Integration Token'");
                log.error("   💡 Asegúrate que la integración esté activa");
            } else if (errorMsg.contains("401")) {
                log.error("❌ Token Bearer sin autorización");
                log.error("   💡 La integración puede no tener los permisos necesarios");
            } else {
                log.error("❌ Error validando token: " + errorMsg);
            }
            return false;
        }
    }
    
    private void validateDatabasePermissions() {
        String[] databases = {
            "Especialidades", especialidadesDbId,
            "Pacientes", pacientesDbId,
            "Planes", planesDbId,
            "Hospitales", hospitalesDbId
        };
        
        for (int i = 0; i < databases.length; i += 2) {
            String dbName = databases[i];
            String dbId = databases[i + 1];
            
            try {
                String response = notionPermissionChecker.notionWebClient.post()
                        .uri("/databases/{databaseId}/query", dbId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + notionToken)
                        .header("Notion-Version", "2022-06-28")
                        .header("Content-Type", "application/json")
                        .bodyValue("{}")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
                
                if (response != null && response.contains("\"results\"")) {
                    log.info("✅ {} - Token tiene acceso a la base de datos", dbName);
                } else {
                    log.warn("⚠️ {} - Respuesta inesperada", dbName);
                }
            } catch (Exception e) {
                if (e.getMessage().contains("401") || e.getMessage().contains("403")) {
                    log.error("❌ {} - Token NO tiene acceso (401/403)", dbName);
                } else if (e.getMessage().contains("404")) {
                    log.error("❌ {} - Base de datos no encontrada (404)", dbName);
                } else {
                    log.error("❌ {} - Error: {}", dbName, e.getMessage());
                }
            }
        }
    }
    
    private void validateNotionTable(String tableName, Mono<?> tableMono) {
        try {
            tableMono
                .timeout(java.time.Duration.ofSeconds(5))
                .doOnSuccess(data -> {
                    if (data instanceof java.util.List) {
                        java.util.List<?> list = (java.util.List<?>) data;
                        log.info("✅ {} - {} registros cargados", tableName, list.size());
                        if (tableName.equals("Especialidades") && !list.isEmpty()) {
                            for (int i = 0; i < Math.min(3, list.size()); i++) {
                                Object item = list.get(i);
                                if (item instanceof EspecialidadSintomaDto) {
                                    EspecialidadSintomaDto esp = (EspecialidadSintomaDto) item;
                                    log.info("   - {} (Keywords: {})", esp.getEspecialidad(), String.join(", ", esp.getKeywords()));
                                }
                            }
                        }
                    }
                })
                .doOnError(error -> {
                    log.error("❌ Error validando {}: {}", tableName, error.getMessage());
                    if (error.getMessage().contains("404")) {
                        log.error("   - ⚠️ Database ID no encontrado o sin acceso");
                        log.error("   - 🔍 Verifica que el ID sea correcto y el token tenga permisos");
                    } else if (error.getMessage().contains("timeout")) {
                        log.error("   - ⏰ Timeout - posible problema de conexión");
                    }
                })
                .subscribe();
        } catch (Exception e) {
            log.error("❌ Error al validar {}: {}", tableName, e.getMessage());
        }
    }
}
