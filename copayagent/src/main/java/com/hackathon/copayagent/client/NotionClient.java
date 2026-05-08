package com.hackathon.copayagent.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.hackathon.copayagent.dto.EspecialidadSintomaDto;
import com.hackathon.copayagent.dto.HospitalDto;
import com.hackathon.copayagent.dto.PatientDto;
import com.hackathon.copayagent.dto.PlanCoberturaDto;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
public class NotionClient {
    // WebClient configurado para consumir la API oficial de Notion
    private final WebClient notionWebClient;
    
    public NotionClient(@Qualifier("notionWebClient") WebClient notionWebClient) {
        this.notionWebClient = notionWebClient;
    }

    // Token de acceso a Notion (variable de entorno o application.yml)
    @Value("${NOTION_TOKEN:${notion.token:}}")
    private String notionToken;

    /**
     * Metodo helper para parsear valores de porcentaje como "80 %" a 80
     */
    private int parsePercentage(String percentageText) {
        if (percentageText == null || percentageText.isEmpty()) {
            return 0;
        }
        // Remover espacios y simbolo de porcentaje
        String clean = percentageText.replace("%", "").replace(" ", "").trim();
        try {
            return Integer.parseInt(clean);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Metodo helper para parsear precios como "50,00 US$" a 50.0
     */
    private double parsePrice(String priceText) {
        if (priceText == null || priceText.isEmpty()) {
            return 0;
        }
        // Remover simbolos de moneda y espacios
        String clean = priceText.replace("US$", "").replace("$", "").replace(" ", "").replace(",", ".").trim();
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Metodo helper para parsear calificaciones como "4,9" a 4.9
     */
    private double parseRating(String ratingText) {
        if (ratingText == null || ratingText.isEmpty()) {
            return 0;
        }
        // Reemplazar coma por punto decimal
        String clean = ratingText.replace(",", ".").trim();
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Metodo helper para extraer cobertura de diferentes tipos de campos
     */
    private int extractCoverage(com.fasterxml.jackson.databind.JsonNode props, String fieldName) {
        com.fasterxml.jackson.databind.JsonNode fieldNode = props.get(fieldName);
        if (fieldNode == null) {
            System.out.println("DEBUG - Campo " + fieldName + " no encontrado");
            return 0;
        }
        
        System.out.println("DEBUG - Campo " + fieldName + " encontrado: " + fieldNode);
        
        // Intentar rich_text primero
        if (fieldNode.has("rich_text") && fieldNode.get("rich_text").size() > 0) {
            String value = fieldNode.get("rich_text").get(0).get("plain_text").asText("");
            System.out.println("DEBUG - Valor rich_text de " + fieldName + ": " + value);
            return parsePercentage(value);
        }
        
        // Intentar select
        if (fieldNode.has("select") && fieldNode.get("select").has("name")) {
            String value = fieldNode.get("select").get("name").asText("");
            System.out.println("DEBUG - Valor select de " + fieldName + ": " + value);
            return parsePercentage(value);
        }
        
        // Intentar number
        if (fieldNode.has("number")) {
            double value = fieldNode.get("number").asDouble(0);
            System.out.println("DEBUG - Valor number de " + fieldName + ": " + value);
            // Convertir decimal a porcentaje (0.9 = 90%, 1.0 = 100%)
            int percentage = (int) (value * 100);
            System.out.println("DEBUG - Convertido a porcentaje: " + percentage + "%");
            return percentage;
        }
        
        System.out.println("DEBUG - No se pudo extraer valor de " + fieldName);
        return 0;
    }

    // ID de la base de datos de pacientes en Notion
    @Value("${notion.database.pacientes:}")
    private String pacientesDbId;
    
    @Value("${notion.database.planes:}")
    private String planesDbId;
    
    @Value("${notion.database.hospitales:}")
    private String hospitalesDbId;
    
    @Value("${notion.database.especialidades:}")
    private String especialidadesDbId;

    /**
     * Obtiene los datos del paciente por clientId.
     * @param clientId ID del paciente
     * @return Mono<PatientDto> con los datos del paciente
     */
    public Mono<PatientDto> getPacienteByClientId(String clientId) {
        if (notionToken == null || notionToken.isEmpty() || pacientesDbId == null || pacientesDbId.isEmpty()) {
            System.out.println("DEBUG - Variables de Notion no configuradas, usando paciente mock");
            // Retornar paciente mock en lugar de error
            return Mono.just(PatientDto.builder()
                    .nombre("Juan Perez")
                    .clientId(clientId)
                    .plan("Premium")
                    .deducibleAnual(200)
                    .deducibleUsado(50)
                    .build());
        }
        return notionWebClient.post()
                .uri(uriBuilder -> uriBuilder.path("/databases/{databaseId}/query").build(pacientesDbId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + notionToken)
                .header("Notion-Version", "2022-06-28")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{" +
                        "\"filter\": {\"property\": \"ClientId\", \"rich_text\": {\"equals\": \"" + clientId + "\"}}" +
                        "}")
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> {
                    try {
                        System.out.println("DEBUG - JSON de Pacientes: " + json.substring(0, Math.min(500, json.length())) + "...");
                        com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
                        com.fasterxml.jackson.databind.JsonNode results = root.get("results");
                        System.out.println("DEBUG - Results count: " + (results != null ? results.size() : "null"));
                        if (results != null && results.isArray() && results.size() > 0) {
                            com.fasterxml.jackson.databind.JsonNode props = results.get(0).get("properties");
                            System.out.println("DEBUG - Properties: " + props);
                            // Extraer campos con manejo flexible
                            String nombre = "";
                            String clientIdValue = "";
                            String plan = "";
                            double deducibleAnual = 0;
                            double deducibleUsado = 0;
                            
                            java.util.Iterator<String> fieldNames = props.fieldNames();
                            while (fieldNames.hasNext()) {
                                String fieldName = fieldNames.next();
                                com.fasterxml.jackson.databind.JsonNode fieldValue = props.get(fieldName);
                                
                                // Nombre - campo title (exact match)
                                if (fieldName.equals("Nombre") && fieldValue.has("title") && fieldValue.get("title").size() > 0) {
                                    nombre = fieldValue.get("title").get(0).get("plain_text").asText("");
                                }
                                // ClientId - campo rich_text (exact match)
                                if (fieldName.equals("ClientId") && fieldValue.has("rich_text") && fieldValue.get("rich_text").size() > 0) {
                                    clientIdValue = fieldValue.get("rich_text").get(0).get("plain_text").asText("");
                                }
                                // Plan - campo select (exact match)
                                if (fieldName.equals("Plan") && fieldValue.has("select") && fieldValue.get("select").has("name")) {
                                    plan = fieldValue.get("select").get("name").asText("");
                                }
                                // Deducible anual - campo number (exact match)
                                if (fieldName.equals("Deducible anual (USD)") && fieldValue.has("number")) {
                                    deducibleAnual = fieldValue.get("number").asDouble(0);
                                }
                                // Deducible usado - campo number (exact match)
                                if (fieldName.equals("Deducible usado (USD)") && fieldValue.has("number")) {
                                    deducibleUsado = fieldValue.get("number").asDouble(0);
                                }
                            }
                            System.out.println("DEBUG - Valores extraídos: nombre='" + nombre + "', clientId='" + clientIdValue + "', plan='" + plan + "', deducibleAnual=" + deducibleAnual + ", deducibleUsado=" + deducibleUsado);
                            return PatientDto.builder()
                                    .nombre(nombre)
                                    .clientId(clientIdValue)
                                    .plan(plan)
                                    .deducibleAnual(deducibleAnual)
                                    .deducibleUsado(deducibleUsado)
                                    .build();
                        }
                        return null;
                    } catch (Exception e) {
                        throw new RuntimeException("Error parseando paciente de Notion: " + e.getMessage());
                    }
                })
                .onErrorResume(WebClientResponseException.class, ex -> Mono.error(new RuntimeException("Error consultando paciente en Notion: " + ex.getMessage())));
    }

    /**
     * Obtiene todos los planes de la base de datos (sin filtros)
     * @return Mono<List<PlanCoberturaDto>> con todos los planes
     */
    public Mono<java.util.List<PlanCoberturaDto>> getAllPlanes() {
        if (notionToken == null || notionToken.isEmpty() || planesDbId == null || planesDbId.isEmpty()) {
            System.out.println("DEBUG - Variables de Notion no configuradas, usando planes mock");
            java.util.List<PlanCoberturaDto> mockPlanes = java.util.List.of(
                PlanCoberturaDto.builder()
                    .plan("Básico")
                    .coberturaConsulta(70)
                    .coberturaEmergencia(80)
                    .coberturaExamenes(60)
                    .coberturaCirugia(70)
                    .coberturaHospitalizacion(70)
                    .build()
            );
            return Mono.just(mockPlanes);
        }
        
        return notionWebClient.post()
                .uri(uriBuilder -> uriBuilder.path("/databases/{databaseId}/query").build(planesDbId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + notionToken)
                .header("Notion-Version", "2022-06-28")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> {
                    System.out.println("DEBUG - JSON de Planes: " + json.substring(0, Math.min(500, json.length())) + "...");
                    java.util.List<PlanCoberturaDto> planes = new java.util.ArrayList<>();
                    try {
                        com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
                        com.fasterxml.jackson.databind.JsonNode results = root.get("results");
                        System.out.println("DEBUG - Results count (Planes): " + (results != null ? results.size() : "null"));
                        if (results != null && results.isArray()) {
                            for (com.fasterxml.jackson.databind.JsonNode page : results) {
                                com.fasterxml.jackson.databind.JsonNode props = page.get("properties");
                                System.out.println("DEBUG - Properties de Planes: " + props);
                                
                                // Extraer nombre del plan - campo title (exact match)
                                String nombre = "";
                                com.fasterxml.jackson.databind.JsonNode planNode = props.get("Plan");
                                if (planNode != null && planNode.has("title") && planNode.get("title").size() > 0) {
                                    nombre = planNode.get("title").get(0).get("plain_text").asText("");
                                }
                                
                                // Extraer coberturas - campos number con valores decimales (0.7 = 70%)
                                int coberturaConsulta = 0;
                                int coberturaEmergencia = 0;
                                int coberturaExamenes = 0;
                                int coberturaCirugia = 0;
                                int coberturaHospitalizacion = 0;
                                
                                // Cobertura consulta (%)
                                com.fasterxml.jackson.databind.JsonNode consultaNode = props.get("Cobertura consulta (%)");
                                if (consultaNode != null && consultaNode.has("number")) {
                                    coberturaConsulta = (int) (consultaNode.get("number").asDouble(0) * 100);
                                }
                                
                                // Cobertura emergencia (%)
                                com.fasterxml.jackson.databind.JsonNode emergenciaNode = props.get("Cobertura emergencia (%)");
                                if (emergenciaNode != null && emergenciaNode.has("number")) {
                                    coberturaEmergencia = (int) (emergenciaNode.get("number").asDouble(0) * 100);
                                }
                                
                                // Cobertura exámenes (%)
                                com.fasterxml.jackson.databind.JsonNode examenesNode = props.get("Cobertura exámenes (%)");
                                if (examenesNode != null && examenesNode.has("number")) {
                                    coberturaExamenes = (int) (examenesNode.get("number").asDouble(0) * 100);
                                }
                                
                                // Cobertura cirugía (%)
                                com.fasterxml.jackson.databind.JsonNode cirugiaNode = props.get("Cobertura cirugía (%)");
                                if (cirugiaNode != null && cirugiaNode.has("number")) {
                                    coberturaCirugia = (int) (cirugiaNode.get("number").asDouble(0) * 100);
                                }
                                
                                // Cobertura hospitalización (%)
                                com.fasterxml.jackson.databind.JsonNode hospitalizacionNode = props.get("Cobertura hospitalización (%)");
                                if (hospitalizacionNode != null && hospitalizacionNode.has("number")) {
                                    coberturaHospitalizacion = (int) (hospitalizacionNode.get("number").asDouble(0) * 100);
                                }
                                
                                System.out.println("DEBUG - Plan extraido: " + nombre + " - " + coberturaConsulta + "%, " + coberturaEmergencia + "%, " + coberturaExamenes + "%, " + coberturaCirugia + "%, " + coberturaHospitalizacion + "%");
                                
                                PlanCoberturaDto dto = PlanCoberturaDto.builder()
                                        .plan(nombre)
                                        .coberturaConsulta(coberturaConsulta)
                                        .coberturaEmergencia(coberturaEmergencia)
                                        .coberturaExamenes(coberturaExamenes)
                                        .coberturaCirugia(coberturaCirugia)
                                        .coberturaHospitalizacion(coberturaHospitalizacion)
                                        .build();
                                planes.add(dto);
                            }
                        }
                        return planes;
                    } catch (Exception e) {
                        throw new RuntimeException("Error parseando planes de Notion: " + e.getMessage());
                    }
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    System.out.println("Error consultando planes en Notion: " + ex.getMessage());
                    // Retornar datos mock en caso de error
                    java.util.List<PlanCoberturaDto> mockPlanes = java.util.List.of(
                        PlanCoberturaDto.builder()
                            .plan("Básico")
                            .coberturaConsulta(70)
                            .coberturaEmergencia(80)
                            .coberturaExamenes(60)
                            .coberturaCirugia(70)
                            .coberturaHospitalizacion(70)
                            .build()
                    );
                    return Mono.just(mockPlanes);
                });
    }

    /**
     * Obtiene el plan de cobertura por nombre de plan.
     * @param plan Nombre del plan (ej: "Basico", "Plus", "Premium")
     * @return Mono<PlanCoberturaDto> con los datos del plan
     */
    public Mono<PlanCoberturaDto> getPlanCoberturaByNombre(String plan) {
        if (notionToken == null || notionToken.isEmpty() || planesDbId == null || planesDbId.isEmpty()) {
            System.out.println("DEBUG - Variables de Notion no configuradas, usando plan mock");
            // Retornar plan mock en lugar de error
            return Mono.just(PlanCoberturaDto.builder()
                    .plan(plan)
                    .coberturaConsulta(80)
                    .coberturaEmergencia(90)
                    .coberturaExamenes(70)
                    .coberturaCirugia(85)
                    .coberturaHospitalizacion(95)
                    .build());
        }
        return notionWebClient.post()
                .uri(uriBuilder -> uriBuilder.path("/databases/{databaseId}/query").build(planesDbId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + notionToken)
                .header("Notion-Version", "2022-06-28")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{" +
                        "\"filter\": {\"property\": \"Plan\", \"rich_text\": {\"equals\": \"" + plan + "\"}}" +
                        "}")
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> {
                    try {
                        System.out.println("DEBUG - JSON de Planes: " + json.substring(0, Math.min(500, json.length())) + "...");
                        com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
                        com.fasterxml.jackson.databind.JsonNode results = root.get("results");
                        System.out.println("DEBUG - Results count (Planes): " + (results != null ? results.size() : "null"));
                        if (results != null && results.isArray() && results.size() > 0) {
                            com.fasterxml.jackson.databind.JsonNode props = results.get(0).get("properties");
                            System.out.println("DEBUG - Properties de Planes: " + props);
                            
                            // Extraer nombre del plan - campo title (exact match)
                            String nombre = "";
                            com.fasterxml.jackson.databind.JsonNode planNode = props.get("Plan");
                            if (planNode != null && planNode.has("title") && planNode.get("title").size() > 0) {
                                nombre = planNode.get("title").get(0).get("plain_text").asText("");
                            }
                            
                            // Extraer coberturas - campos number con valores decimales (0.7 = 70%)
                            int coberturaConsulta = 0;
                            int coberturaEmergencia = 0;
                            int coberturaExamenes = 0;
                            int coberturaCirugia = 0;
                            int coberturaHospitalizacion = 0;
                            
                            // Cobertura consulta (%)
                            com.fasterxml.jackson.databind.JsonNode consultaNode = props.get("Cobertura consulta (%)");
                            if (consultaNode != null && consultaNode.has("number")) {
                                coberturaConsulta = (int) (consultaNode.get("number").asDouble(0) * 100);
                            }
                            
                            // Cobertura emergencia (%)
                            com.fasterxml.jackson.databind.JsonNode emergenciaNode = props.get("Cobertura emergencia (%)");
                            if (emergenciaNode != null && emergenciaNode.has("number")) {
                                coberturaEmergencia = (int) (emergenciaNode.get("number").asDouble(0) * 100);
                            }
                            
                            // Cobertura exámenes (%)
                            com.fasterxml.jackson.databind.JsonNode examenesNode = props.get("Cobertura exámenes (%)");
                            if (examenesNode != null && examenesNode.has("number")) {
                                coberturaExamenes = (int) (examenesNode.get("number").asDouble(0) * 100);
                            }
                            
                            // Cobertura cirugía (%)
                            com.fasterxml.jackson.databind.JsonNode cirugiaNode = props.get("Cobertura cirugía (%)");
                            if (cirugiaNode != null && cirugiaNode.has("number")) {
                                coberturaCirugia = (int) (cirugiaNode.get("number").asDouble(0) * 100);
                            }
                            
                            // Cobertura hospitalización (%)
                            com.fasterxml.jackson.databind.JsonNode hospitalizacionNode = props.get("Cobertura hospitalización (%)");
                            if (hospitalizacionNode != null && hospitalizacionNode.has("number")) {
                                coberturaHospitalizacion = (int) (hospitalizacionNode.get("number").asDouble(0) * 100);
                            }
                            
                            System.out.println("DEBUG - Plan extraido: " + nombre + " - " + coberturaConsulta + "%, " + coberturaEmergencia + "%, " + coberturaExamenes + "%, " + coberturaCirugia + "%, " + coberturaHospitalizacion + "%");
                            
                            return PlanCoberturaDto.builder()
                                    .plan(nombre)
                                    .coberturaConsulta(coberturaConsulta)
                                    .coberturaEmergencia(coberturaEmergencia)
                                    .coberturaExamenes(coberturaExamenes)
                                    .coberturaCirugia(coberturaCirugia)
                                    .coberturaHospitalizacion(coberturaHospitalizacion)
                                    .build();
                        }
                        return null;
                    } catch (Exception e) {
                        throw new RuntimeException("Error parseando plan de Notion: " + e.getMessage());
                    }
                })
                .onErrorResume(WebClientResponseException.class, ex -> Mono.error(new RuntimeException("Error consultando plan en Notion: " + ex.getMessage())));
    }

    /**
     * Obtiene la lista de hospitales que aceptan el plan y tienen la especialidad.
     * @param plan Nombre del plan del paciente
     * @param especialidad Especialidad medica detectada
     * @return Mono<List<HospitalDto>> con hospitales compatibles
     */
    public Mono<List<HospitalDto>> getHospitalesByPlanYEspecialidad(String plan, String especialidad) {
        if (notionToken == null || notionToken.isEmpty() || hospitalesDbId == null || hospitalesDbId.isEmpty()) {
            System.out.println("DEBUG - Variables de Notion no configuradas, usando hospitales mock");
            // Retornar hospitales mock en lugar de error
            java.util.List<HospitalDto> mockHospitales = java.util.List.of(
                    HospitalDto.builder()
                            .nombre("Hospital Vozandes")
                            .planesAceptados(java.util.List.of("premium", "plus", "basico"))
                            .especialidades(java.util.List.of("medicina general", "cardiologia", "neurologia"))
                            .precioConsultaGeneral(50)
                            .precioConsultaEspecialista(85)
                            .precioEmergencia(120)
                            .calificacion(4.8)
                            .tiempoEsperaPromedio("15 minutos")
                            .build(),
                    HospitalDto.builder()
                            .nombre("Hospital Metropolitano")
                            .planesAceptados(java.util.List.of("premium", "plus"))
                            .especialidades(java.util.List.of("medicina general", "cardiologia", "neurologia", "cirugia"))
                            .precioConsultaGeneral(60)
                            .precioConsultaEspecialista(100)
                            .precioEmergencia(150)
                            .calificacion(4.5)
                            .tiempoEsperaPromedio("20 minutos")
                            .build()
            );
            return Mono.just(mockHospitales);
        }
        // Aqui deberias hacer un filtro compuesto en Notion, pero si no es posible, filtra en Java tras obtener todos los hospitales
                return notionWebClient.post()
                                .uri(uriBuilder -> uriBuilder.path("/databases/{databaseId}/query").build(hospitalesDbId))
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + notionToken)
                                .header("Notion-Version", "2022-06-28")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue("{}")
                                .retrieve()
                                .bodyToMono(String.class)
                                .map(json -> {
                                        System.out.println("DEBUG - JSON de Hospitales: " + json.substring(0, Math.min(500, json.length())) + "...");
                                        java.util.List<HospitalDto> hospitales = new java.util.ArrayList<>();
                                        try {
                                                com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
                                                com.fasterxml.jackson.databind.JsonNode results = root.get("results");
                                                System.out.println("DEBUG - Results count (Hospitales): " + (results != null ? results.size() : "null"));
                                                if (results != null && results.isArray()) {
                                                        for (com.fasterxml.jackson.databind.JsonNode page : results) {
                                                                com.fasterxml.jackson.databind.JsonNode props = page.get("properties");
                                                                
                                                                // Extraer campos con manejo flexible
                                                                String nombre = "";
                                                                java.util.List<String> planesAceptados = new java.util.ArrayList<>();
                                                                java.util.List<String> especialidades = new java.util.ArrayList<>();
                                                                double precioConsultaGeneral = 0;
                                                                double precioConsultaEspecialista = 0;
                                                                double precioEmergencia = 0;
                                                                double calificacion = 0;
                                                                String tiempoEsperaPromedio = "";
                                                                
                                                                java.util.Iterator<String> fieldNames = props.fieldNames();
                                                                while (fieldNames.hasNext()) {
                                                                        String fieldName = fieldNames.next();
                                                                        com.fasterxml.jackson.databind.JsonNode fieldValue = props.get(fieldName);
                                                                        
                                                                        // Nombre - campo title (exact match)
                                                                        if (fieldName.equals("Nombre") && fieldValue.has("title") && fieldValue.get("title").size() > 0) {
                                                                                nombre = fieldValue.get("title").get(0).get("plain_text").asText("");
                                                                        }
                                                                        
                                                                        // Planes aceptados - campo multi_select (exact match)
                                                                        if (fieldName.equals("Planes aceptados") && fieldValue.has("multi_select")) {
                                                                                for (com.fasterxml.jackson.databind.JsonNode p : fieldValue.get("multi_select")) {
                                                                                        planesAceptados.add(p.get("name").asText("").toLowerCase());
                                                                                }
                                                                        }
                                                                        
                                                                        // Especialidades - campo multi_select (exact match)
                                                                        if (fieldName.equals("Especialidades") && fieldValue.has("multi_select")) {
                                                                                for (com.fasterxml.jackson.databind.JsonNode e : fieldValue.get("multi_select")) {
                                                                                        especialidades.add(e.get("name").asText("").toLowerCase());
                                                                                }
                                                                        }
                                                                        
                                                                        // Precio consulta general (USD) - campo number (exact match)
                                                                        if (fieldName.equals("Precio consulta general (USD)") && fieldValue.has("number")) {
                                                                                precioConsultaGeneral = fieldValue.get("number").asDouble(0);
                                                                        }
                                                                        
                                                                        // Precio consulta especialista (USD) - campo number (exact match)
                                                                        if (fieldName.equals("Precio consulta especialista (USD)") && fieldValue.has("number")) {
                                                                                precioConsultaEspecialista = fieldValue.get("number").asDouble(0);
                                                                        }
                                                                        
                                                                        // Precio emergencia (USD) - campo number (exact match)
                                                                        if (fieldName.equals("Precio emergencia (USD)") && fieldValue.has("number")) {
                                                                                precioEmergencia = fieldValue.get("number").asDouble(0);
                                                                        }
                                                                        
                                                                        // Calificación - campo number (exact match)
                                                                        if (fieldName.equals("Calificación") && fieldValue.has("number")) {
                                                                                calificacion = fieldValue.get("number").asDouble(0);
                                                                        }
                                                                        
                                                                        // Tiempo espera promedio - campo rich_text (exact match)
                                                                        if (fieldName.equals("Tiempo espera promedio") && fieldValue.has("rich_text") && fieldValue.get("rich_text").size() > 0) {
                                                                                tiempoEsperaPromedio = fieldValue.get("rich_text").get(0).get("plain_text").asText("");
                                                                        }
                                                                }
                                                                
                                                                HospitalDto dto = HospitalDto.builder()
                                                                        .nombre(nombre)
                                                                        .planesAceptados(planesAceptados)
                                                                        .especialidades(especialidades)
                                                                        .precioConsultaGeneral(precioConsultaGeneral)
                                                                        .precioConsultaEspecialista(precioConsultaEspecialista)
                                                                        .precioEmergencia(precioEmergencia)
                                                                        .calificacion(calificacion)
                                                                        .tiempoEsperaPromedio(tiempoEsperaPromedio)
                                                                        .build();
                                                                hospitales.add(dto);
                                                        }
                                                }
                                                return hospitales;
                                        } catch (Exception e) {
                                                throw new RuntimeException("Error parseando hospitales: " + e.getMessage());
                                        }
                                })
                                .onErrorResume(WebClientResponseException.class, ex -> Mono.error(new RuntimeException("Error consultando hospitales en Notion: " + ex.getMessage())));
    }

    /**
     * Obtiene todos los hospitales de la base de datos (sin filtros)
     * @return Mono<List<HospitalDto>> con todos los hospitales
     */
    public Mono<java.util.List<HospitalDto>> getAllHospitales() {
        if (notionToken == null || notionToken.isEmpty() || hospitalesDbId == null || hospitalesDbId.isEmpty()) {
            System.out.println("DEBUG - Variables de Notion no configuradas, usando hospitales mock");
            java.util.List<HospitalDto> mockHospitales = java.util.List.of(
                HospitalDto.builder()
                    .nombre("Hospital de Prueba")
                    .planesAceptados(java.util.List.of("premium", "plus"))
                    .especialidades(java.util.List.of("medicina general", "cardiologia"))
                    .precioConsultaGeneral(50)
                    .precioConsultaEspecialista(85)
                    .precioEmergencia(120)
                    .calificacion(4.8)
                    .tiempoEsperaPromedio("15 minutos")
                    .build()
            );
            return Mono.just(mockHospitales);
        }
        
        return notionWebClient.post()
                .uri(uriBuilder -> uriBuilder.path("/databases/{databaseId}/query").build(hospitalesDbId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + notionToken)
                .header("Notion-Version", "2022-06-28")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> {
                    System.out.println("DEBUG - JSON de Hospitales: " + json.substring(0, Math.min(500, json.length())) + "...");
                    java.util.List<HospitalDto> hospitales = new java.util.ArrayList<>();
                    try {
                        com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
                        com.fasterxml.jackson.databind.JsonNode results = root.get("results");
                        System.out.println("DEBUG - Results count (Hospitales): " + (results != null ? results.size() : "null"));
                        if (results != null && results.isArray()) {
                            for (com.fasterxml.jackson.databind.JsonNode page : results) {
                                com.fasterxml.jackson.databind.JsonNode props = page.get("properties");
                                System.out.println("DEBUG - Properties de Hospitales: " + props);
                                
                                // Extraer campos con manejo flexible
                                String nombre = "";
                                java.util.List<String> planesAceptados = new java.util.ArrayList<>();
                                java.util.List<String> especialidades = new java.util.ArrayList<>();
                                double precioConsultaGeneral = 0;
                                double precioConsultaEspecialista = 0;
                                double precioEmergencia = 0;
                                double calificacion = 0;
                                String tiempoEsperaPromedio = "";
                                
                                java.util.Iterator<String> fieldNames = props.fieldNames();
                                while (fieldNames.hasNext()) {
                                    String fieldName = fieldNames.next();
                                    com.fasterxml.jackson.databind.JsonNode fieldValue = props.get(fieldName);
                                    
                                    // Nombre - campo title (exact match)
                                    if (fieldName.equals("Nombre") && fieldValue.has("title") && fieldValue.get("title").size() > 0) {
                                        nombre = fieldValue.get("title").get(0).get("plain_text").asText("");
                                    }
                                    
                                    // Planes aceptados - campo multi_select (exact match)
                                    if (fieldName.equals("Planes aceptados") && fieldValue.has("multi_select")) {
                                        for (com.fasterxml.jackson.databind.JsonNode p : fieldValue.get("multi_select")) {
                                            planesAceptados.add(p.get("name").asText("").toLowerCase());
                                        }
                                    }
                                    
                                    // Especialidades - campo multi_select (exact match)
                                    if (fieldName.equals("Especialidades") && fieldValue.has("multi_select")) {
                                        for (com.fasterxml.jackson.databind.JsonNode e : fieldValue.get("multi_select")) {
                                            especialidades.add(e.get("name").asText("").toLowerCase());
                                        }
                                    }
                                    
                                    // Precio consulta general (USD) - campo number (exact match)
                                    if (fieldName.equals("Precio consulta general (USD)") && fieldValue.has("number")) {
                                        precioConsultaGeneral = fieldValue.get("number").asDouble(0);
                                    }
                                    
                                    // Precio consulta especialista (USD) - campo number (exact match)
                                    if (fieldName.equals("Precio consulta especialista (USD)") && fieldValue.has("number")) {
                                        precioConsultaEspecialista = fieldValue.get("number").asDouble(0);
                                    }
                                    
                                    // Precio emergencia (USD) - campo number (exact match)
                                    if (fieldName.equals("Precio emergencia (USD)") && fieldValue.has("number")) {
                                        precioEmergencia = fieldValue.get("number").asDouble(0);
                                    }
                                    
                                    // Calificación - campo number (exact match)
                                    if (fieldName.equals("Calificación") && fieldValue.has("number")) {
                                        calificacion = fieldValue.get("number").asDouble(0);
                                    }
                                    
                                    // Tiempo espera promedio - campo rich_text (exact match)
                                    if (fieldName.equals("Tiempo espera promedio") && fieldValue.has("rich_text") && fieldValue.get("rich_text").size() > 0) {
                                        tiempoEsperaPromedio = fieldValue.get("rich_text").get(0).get("plain_text").asText("");
                                    }
                                }
                                
                                HospitalDto dto = HospitalDto.builder()
                                        .nombre(nombre)
                                        .planesAceptados(planesAceptados)
                                        .especialidades(especialidades)
                                        .precioConsultaGeneral(precioConsultaGeneral)
                                        .precioConsultaEspecialista(precioConsultaEspecialista)
                                        .precioEmergencia(precioEmergencia)
                                        .calificacion(calificacion)
                                        .tiempoEsperaPromedio(tiempoEsperaPromedio)
                                        .build();
                                hospitales.add(dto);
                            }
                        }
                        return hospitales;
                    } catch (Exception e) {
                        throw new RuntimeException("Error parseando hospitales: " + e.getMessage());
                    }
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    System.out.println("Error consultando hospitales en Notion: " + ex.getMessage());
                    // Retornar datos mock en caso de error
                    java.util.List<HospitalDto> mockHospitales = java.util.List.of(
                        HospitalDto.builder()
                            .nombre("Hospital de Prueba")
                            .planesAceptados(java.util.List.of("premium", "plus"))
                            .especialidades(java.util.List.of("medicina general", "cardiologia"))
                            .precioConsultaGeneral(50)
                            .precioConsultaEspecialista(85)
                            .precioEmergencia(120)
                            .calificacion(4.8)
                            .tiempoEsperaPromedio("15 minutos")
                            .build()
                    );
                    return Mono.just(mockHospitales);
                });
    }

    /**
     * Obtiene la especialidad y prioridad a partir de un sintoma usando la tabla de especialidades_sintomas.
     * @param sintoma Texto del sintoma
     * @return Mono<EspecialidadSintomaDto> con especialidad, prioridad y precio referencial
     */
    public Mono<EspecialidadSintomaDto> getEspecialidadPorSintoma(String sintoma) {
        if (notionToken == null || notionToken.isEmpty() || especialidadesDbId == null || especialidadesDbId.isEmpty()) {
            System.out.println("DEBUG - Variables de Notion no configuradas, usando especialidad mock");
            // Retornar especialidad mock basada en sintomas comunes
            String sintomaLower = sintoma.toLowerCase();
            if (sintomaLower.contains("dolor") && sintomaLower.contains("cabeza")) {
                return Mono.just(EspecialidadSintomaDto.builder()
                        .especialidad("Neurologia")
                        .prioridad("Media")
                        .tipoConsulta("Especialista")
                        .precioReferencialUSD(85)
                        .keywords(java.util.List.of("dolor cabeza", "migrana", "jaqueca", "mareo"))
                        .build());
            } else if (sintomaLower.contains("dolor") && sintomaLower.contains("pecho")) {
                return Mono.just(EspecialidadSintomaDto.builder()
                        .especialidad("Cardiologia")
                        .prioridad("Alta")
                        .tipoConsulta("Especialista")
                        .precioReferencialUSD(100)
                        .keywords(java.util.List.of("dolor pecho", "palpitaciones", "presion alta"))
                        .build());
            } else {
                return Mono.just(EspecialidadSintomaDto.builder()
                        .especialidad("Medicina General")
                        .prioridad("Media")
                        .tipoConsulta("General")
                        .precioReferencialUSD(50)
                        .keywords(java.util.List.of("dolor", "fiebre", "tos", "malestar"))
                        .build());
            }
        }
                // Traer todas las filas de la tabla de especialidades-sintomas y mapear manualmente
                return notionWebClient.post()
                                .uri(uriBuilder -> uriBuilder.path("/databases/{databaseId}/query").build(especialidadesDbId))
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + notionToken)
                                .header("Notion-Version", "2022-06-28")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue("{}")
                                .retrieve()
                                .bodyToMono(String.class)
                                .flatMapMany(json -> {
                                        // Log para debugging
                                        System.out.println("DEBUG - JSON de Notion: " + json);
                                        // Parsear el JSON manualmente para extraer la lista de EspecialidadSintomaDto
                                        // Espera un objeto con propiedad 'results' que es un array de paginas
                                        try {
                                                com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
                                                System.out.println("DEBUG - Root: " + root);
                                                com.fasterxml.jackson.databind.JsonNode results = root.get("results");
                                                System.out.println("DEBUG - Results: " + results);
                                                java.util.List<EspecialidadSintomaDto> lista = new java.util.ArrayList<>();
                                                if (results != null && results.isArray()) {
                                                        for (com.fasterxml.jackson.databind.JsonNode page : results) {
                                                                System.out.println("DEBUG - Page: " + page);
                                                                // Extraer campos segun la estructura de Notion
                                                                com.fasterxml.jackson.databind.JsonNode props = page.get("properties");
                                                                System.out.println("DEBUG - Properties: " + props);
                                                                if (props == null) {
                                                                        System.out.println("DEBUG - Properties es null");
                                                                        continue;
                                                                }
                                                                
                                                                // Intentar obtener las propiedades con nombres comunes
                                                                String especialidad = "";
                                                                String prioridad = "";
                                                                String tipoConsulta = "";
                                                                double precioReferencial = 0;
                                                                java.util.List<String> keywords = new java.util.ArrayList<>();
                                                                
                                                                // Buscar propiedades por nombres exactos segun la estructura real
                                                                java.util.Iterator<String> fieldNames = props.fieldNames();
                                                                while (fieldNames.hasNext()) {
                                                                        String fieldName = fieldNames.next();
                                                                        com.fasterxml.jackson.databind.JsonNode fieldValue = props.get(fieldName);
                                                                        
                                                                        System.out.println("DEBUG - Campo: " + fieldName + " = " + fieldValue);
                                                                        
                                                                        // Especialidad - campo title (exact match)
                                                                        if (fieldName.equals("Especialidad") && fieldValue.has("title") && fieldValue.get("title").size() > 0) {
                                                                                especialidad = fieldValue.get("title").get(0).get("plain_text").asText("");
                                                                                System.out.println("DEBUG - Especialidad extraida: " + especialidad);
                                                                        }
                                                                        // Prioridad - campo select (exact match)
                                                                        if (fieldName.equals("Prioridad") && fieldValue.has("select") && fieldValue.get("select").has("name")) {
                                                                                prioridad = fieldValue.get("select").get("name").asText("");
                                                                                System.out.println("DEBUG - Prioridad extraida: " + prioridad);
                                                                        }
                                                                        // Tipo de consulta - campo select (exact match)
                                                                        if (fieldName.equals("Tipo de consulta") && fieldValue.has("select") && fieldValue.get("select").has("name")) {
                                                                                tipoConsulta = fieldValue.get("select").get("name").asText("");
                                                                                System.out.println("DEBUG - Tipo consulta extraido: " + tipoConsulta);
                                                                        }
                                                                        // Precio referencial (USD) - campo number (exact match)
                                                                        if (fieldName.equals("Precio referencial (USD)") && fieldValue.has("number")) {
                                                                                precioReferencial = fieldValue.get("number").asDouble(0);
                                                                                System.out.println("DEBUG - Precio extraido: " + precioReferencial);
                                                                        }
                                                                        // Keywords (coma-separadas) - campo rich_text (exact match)
                                                                        if (fieldName.equals("Keywords (coma-separadas)") && fieldValue.has("rich_text")) {
                                                                                String keywordsText = "";
                                                                                for (com.fasterxml.jackson.databind.JsonNode textNode : fieldValue.get("rich_text")) {
                                                                                        if (textNode.has("plain_text")) {
                                                                                                keywordsText += textNode.get("plain_text").asText();
                                                                                        }
                                                                                }
                                                                                System.out.println("DEBUG - Keywords text: " + keywordsText);
                                                                                // Procesar keywords separadas por coma
                                                                                String[] keywordArray = keywordsText.split(",");
                                                                                for (String kw : keywordArray) {
                                                                                        String cleanKeyword = kw.trim().toLowerCase();
                                                                                        if (!cleanKeyword.isEmpty()) {
                                                                                                keywords.add(cleanKeyword);
                                                                                        }
                                                                                }
                                                                                System.out.println("DEBUG - Keywords procesadas: " + keywords);
                                                                        }
                                                                }
                                                                
                                                                System.out.println("DEBUG - Extraido: especialidad=" + especialidad + ", prioridad=" + prioridad + ", tipoConsulta=" + tipoConsulta + ", keywords=" + keywords);
                                                                
                                                                EspecialidadSintomaDto dto = EspecialidadSintomaDto.builder()
                                                                                .especialidad(especialidad)
                                                                                .prioridad(prioridad)
                                                                                .tipoConsulta(tipoConsulta)
                                                                                .precioReferencialUSD(precioReferencial)
                                                                                .keywords(keywords)
                                                                                .build();
                                                                lista.add(dto);
                                                        }
                                                }
                                                return reactor.core.publisher.Flux.fromIterable(lista);
                                        } catch (Exception e) {
                                                System.out.println("DEBUG - Error parseando: " + e.getMessage());
                                                e.printStackTrace();
                                                return reactor.core.publisher.Flux.error(new RuntimeException("Error parseando especialidades de Notion: " + e.getMessage()));
                                        }
                                })
                                .collectList()
                                .map(lista -> {
                                    System.out.println("DEBUG - Lista de especialidades obtenida: " + lista.size() + " elementos");
                                    for (EspecialidadSintomaDto dto : lista) {
                                        System.out.println("DEBUG - Especialidad: " + dto.getEspecialidad() + ", Keywords: " + dto.getKeywords());
                                    }
                                    
                                    EspecialidadSintomaDto resultado = lista.stream()
                                                .filter(e -> {
                                                    boolean match = e.getKeywords().stream().anyMatch(k -> sintoma.toLowerCase().contains(k));
                                                    System.out.println("DEBUG - Comparando '" + sintoma.toLowerCase() + "' con keywords de " + e.getEspecialidad() + ": " + match);
                                                    return match;
                                                })
                                                .findFirst()
                                                .orElseGet(() -> {
                                                    System.out.println("DEBUG - No se encontró especialidad coincidente, usando Medicina General por defecto");
                                                    return EspecialidadSintomaDto.builder()
                                                            .especialidad("Medicina General")
                                                            .prioridad("Media")
                                                            .tipoConsulta("General")
                                                            .precioReferencialUSD(50)
                                                            .keywords(java.util.List.of("consulta general", "chequeo", "control"))
                                                            .build();
                                                });
                                    
                                    System.out.println("DEBUG - Resultado final: " + resultado.getEspecialidad());
                                    return resultado;
                                })
                                .onErrorResume(WebClientResponseException.class, ex -> Mono.error(new RuntimeException("Error consultando especialidad en Notion: " + ex.getMessage())));
    }

    /**
     * Consulta hospitales afiliados por especialidad en Notion.
     * Debes mapear la respuesta JSON de Notion a una lista de HospitalDto.
     *
     * @param specialty Especialidad medica
     * @return Mono<List<HospitalDto>> con hospitales compatibles
     */
    public Mono<List<HospitalDto>> getHospitalsBySpecialty(String specialty) {
        if (notionToken == null || notionToken.isEmpty() || hospitalesDbId == null || hospitalesDbId.isEmpty()) {
            System.out.println("DEBUG - Variables de Notion no configuradas, usando hospitales mock");
            // Retornar hospitales mock
            java.util.List<HospitalDto> mockHospitales = java.util.List.of(
                HospitalDto.builder()
                    .nombre("Hospital Vozandes")
                    .planesAceptados(java.util.List.of("premium", "plus", "basico"))
                    .especialidades(java.util.List.of("medicina general", "cardiologia", "neurologia"))
                    .precioConsultaGeneral(50)
                    .precioConsultaEspecialista(85)
                    .precioEmergencia(120)
                    .calificacion(4.8)
                    .tiempoEsperaPromedio("15 minutos")
                    .build()
            );
            return Mono.just(mockHospitales);
        }
        
        // Ejemplo de consulta a la base de datos de hospitales usando filtro por especialidad
        return notionWebClient.post()
                .uri(uriBuilder -> uriBuilder.path("/databases/{databaseId}/query").build(hospitalesDbId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + notionToken)
                .header("Notion-Version", "2022-06-28")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{" +
                        "\"filter\": {\"property\": \"specialty\", \"rich_text\": {\"equals\": \"" + specialty + "\"}}" +
                        "}")
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> {
                    // TODO: Mapear la respuesta JSON de Notion a una lista de HospitalDto segun tu estructura
                    java.util.List<HospitalDto> hospitales = new java.util.ArrayList<>();
                    // Implementar mapeo aqui
                    return hospitales;
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    System.out.println("Error consultando hospitales en Notion: " + ex.getMessage());
                    // Retornar datos mock en caso de error
                    java.util.List<HospitalDto> mockHospitales = java.util.List.of(
                        HospitalDto.builder()
                            .nombre("Hospital Vozandes")
                            .planesAceptados(java.util.List.of("premium", "plus", "basico"))
                            .especialidades(java.util.List.of("medicina general", "cardiologia", "neurologia"))
                            .precioConsultaGeneral(50)
                            .precioConsultaEspecialista(85)
                            .precioEmergencia(120)
                            .calificacion(4.8)
                            .tiempoEsperaPromedio("15 minutos")
                            .build()
                    );
                    return Mono.just(mockHospitales);
                });
    }

    public String detectSpecialtyFromKeywords(String message) {
        try {
            System.out.println("DEBUG - Buscando especialidad para mensaje: " + message);
            
            // Usar el metodo para obtener especialidades
            java.util.List<EspecialidadSintomaDto> especialidades = getEspecialidades().block();
            
            if (especialidades == null || especialidades.isEmpty()) {
                System.out.println("DEBUG - No se encontraron especialidades");
                return "Medicina General";
            }
            
            String messageLower = message.toLowerCase();
            
            for (EspecialidadSintomaDto especialidad : especialidades) {
                for (String keyword : especialidad.getKeywords()) {
                    if (messageLower.contains(keyword.toLowerCase())) {
                        System.out.println("DEBUG - Especialidad encontrada: " + especialidad.getEspecialidad());
                        return especialidad.getEspecialidad();
                    }
                }
            }
            
            System.out.println("DEBUG - No se encontro especialidad especifica, usando Medicina General");
            return "Medicina General";
        } catch (Exception e) {
            System.err.println("Error detectando especialidad: " + e.getMessage());
            return "Medicina General";
        }
    }

    public String getPriorityForSpecialty(String especialidad) {
        try {
            java.util.List<EspecialidadSintomaDto> especialidades = getEspecialidades().block();
            
            for (EspecialidadSintomaDto esp : especialidades) {
                if (esp.getEspecialidad().equals(especialidad)) {
                    return esp.getPrioridad();
                }
            }
        } catch (Exception e) {
            System.err.println("Error obteniendo prioridad: " + e.getMessage());
        }
        return "Media";
    }

    public String getTipoConsultaForSpecialty(String especialidad) {
        try {
            java.util.List<EspecialidadSintomaDto> especialidades = getEspecialidades().block();
            
            for (EspecialidadSintomaDto esp : especialidades) {
                if (esp.getEspecialidad().equals(especialidad)) {
                    return esp.getTipoConsulta();
                }
            }
        } catch (Exception e) {
            System.err.println("Error obteniendo tipo consulta: " + e.getMessage());
        }
        return "General";
    }

    public double getPrecioReferencialForSpecialty(String especialidad) {
        try {
            java.util.List<EspecialidadSintomaDto> especialidades = getEspecialidades().block();
            
            for (EspecialidadSintomaDto esp : especialidades) {
                if (esp.getEspecialidad().equals(especialidad)) {
                    return esp.getPrecioReferencialUSD();
                }
            }
        } catch (Exception e) {
            System.err.println("Error obteniendo precio referencial: " + e.getMessage());
        }
        return 50.0; // Precio por defecto
    }

    /**
     * Obtiene todas las especialidades de la base de datos
     * @return Mono<List<EspecialidadSintomaDto>> con todas las especialidades
     */
    public Mono<java.util.List<EspecialidadSintomaDto>> getEspecialidades() {
        if (notionToken == null || notionToken.isEmpty() || especialidadesDbId == null || especialidadesDbId.isEmpty()) {
            System.out.println("DEBUG - Variables de Notion no configuradas, usando especialidades mock");
            // Retornar especialidades mock
            java.util.List<EspecialidadSintomaDto> mockEspecialidades = java.util.List.of(
                EspecialidadSintomaDto.builder()
                    .especialidad("Neurologia")
                    .prioridad("Media")
                    .tipoConsulta("Especialista")
                    .precioReferencialUSD(85)
                    .keywords(java.util.List.of("dolor cabeza", "migrana", "jaqueca", "convulsiones"))
                    .build()
            );
            return Mono.just(mockEspecialidades);
        }
        
        return notionWebClient.post()
                .uri(uriBuilder -> uriBuilder.path("/databases/{databaseId}/query").build(especialidadesDbId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + notionToken)
                .header("Notion-Version", "2022-06-28")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .retrieve()
                .bodyToMono(String.class)
                .flatMapMany(json -> {
                    try {
                        com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
                        com.fasterxml.jackson.databind.JsonNode results = root.get("results");
                        java.util.List<EspecialidadSintomaDto> lista = new java.util.ArrayList<>();
                        
                        if (results != null && results.isArray()) {
                            for (com.fasterxml.jackson.databind.JsonNode page : results) {
                                com.fasterxml.jackson.databind.JsonNode props = page.get("properties");
                                if (props == null) continue;
                                
                                String especialidad = "";
                                String prioridad = "";
                                String tipoConsulta = "";
                                double precioReferencial = 0;
                                java.util.List<String> keywords = new java.util.ArrayList<>();
                                
                                java.util.Iterator<String> fieldNames = props.fieldNames();
                                while (fieldNames.hasNext()) {
                                    String fieldName = fieldNames.next();
                                    com.fasterxml.jackson.databind.JsonNode fieldValue = props.get(fieldName);
                                    
                                    // Especialidad - campo title
                                    if (fieldName.equals("Especialidad") && fieldValue.has("title") && fieldValue.get("title").size() > 0) {
                                        especialidad = fieldValue.get("title").get(0).get("plain_text").asText("");
                                    }
                                    // Prioridad - campo select
                                    if (fieldName.equals("Prioridad") && fieldValue.has("select") && fieldValue.get("select").has("name")) {
                                        prioridad = fieldValue.get("select").get("name").asText("");
                                    }
                                    // Tipo de consulta - campo select
                                    if (fieldName.equals("Tipo Consulta") && fieldValue.has("select") && fieldValue.get("select").has("name")) {
                                        tipoConsulta = fieldValue.get("select").get("name").asText("");
                                    }
                                    // Precio - campo number
                                    if (fieldName.equals("Precio Referencial") && fieldValue.has("number")) {
                                        precioReferencial = fieldValue.get("number").asDouble(0);
                                    }
                                    // Keywords - campo multi_select
                                    if (fieldName.equals("Keywords") && fieldValue.has("multi_select")) {
                                        for (com.fasterxml.jackson.databind.JsonNode kw : fieldValue.get("multi_select")) {
                                            keywords.add(kw.get("name").asText(""));
                                        }
                                    }
                                }
                                
                                if (!especialidad.isEmpty()) {
                                    EspecialidadSintomaDto dto = EspecialidadSintomaDto.builder()
                                            .especialidad(especialidad)
                                            .prioridad(prioridad.isEmpty() ? "Media" : prioridad)
                                            .tipoConsulta(tipoConsulta.isEmpty() ? "General" : tipoConsulta)
                                            .precioReferencialUSD(precioReferencial == 0 ? 50 : precioReferencial)
                                            .keywords(keywords.isEmpty() ? java.util.List.of(especialidad.toLowerCase()) : keywords)
                                            .build();
                                    lista.add(dto);
                                }
                            }
                        }
                        return reactor.core.publisher.Flux.fromIterable(lista);
                    } catch (Exception e) {
                        return reactor.core.publisher.Flux.error(new RuntimeException("Error parseando especialidades: " + e.getMessage()));
                    }
                })
                .collectList()
                .onErrorResume(WebClientResponseException.class, ex -> {
                    System.out.println("Error consultando especialidades en Notion: " + ex.getMessage());
                    // Retornar datos mock en caso de error
                    java.util.List<EspecialidadSintomaDto> mockEspecialidades = java.util.List.of(
                        EspecialidadSintomaDto.builder()
                            .especialidad("Medicina General")
                            .prioridad("Media")
                            .tipoConsulta("General")
                            .precioReferencialUSD(50)
                            .keywords(java.util.List.of("dolor", "fiebre", "tos", "malestar"))
                            .build()
                    );
                    return Mono.just(mockEspecialidades);
                });
    }

    /**
     * Obtiene todos los pacientes de la base de datos (sin filtros)
     * @return Mono<List<PatientDto>> con todos los pacientes
     */
    public Mono<java.util.List<PatientDto>> getAllPacientes() {
        if (notionToken == null || notionToken.isEmpty() || pacientesDbId == null || pacientesDbId.isEmpty()) {
            System.out.println("DEBUG - Variables de Notion no configuradas, usando pacientes mock");
            java.util.List<PatientDto> mockPacientes = java.util.List.of(
                PatientDto.builder()
                    .clientId("paciente-001")
                    .nombre("Paciente de Prueba")
                    .plan("Premium")
                    .deducibleAnual(500.0)
                    .deducibleUsado(0.0)
                    .build()
            );
            return Mono.just(mockPacientes);
        }
        
        return notionWebClient.post()
                .uri(uriBuilder -> uriBuilder.path("/databases/{databaseId}/query").build(pacientesDbId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + notionToken)
                .header("Notion-Version", "2022-06-28")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> {
                    System.out.println("DEBUG - JSON de Pacientes: " + json.substring(0, Math.min(500, json.length())) + "...");
                    java.util.List<PatientDto> pacientes = new java.util.ArrayList<>();
                    try {
                        com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
                        com.fasterxml.jackson.databind.JsonNode results = root.get("results");
                        System.out.println("DEBUG - Results count: " + (results != null ? results.size() : "null"));
                        if (results != null && results.isArray()) {
                            for (com.fasterxml.jackson.databind.JsonNode page : results) {
                                com.fasterxml.jackson.databind.JsonNode props = page.get("properties");
                                System.out.println("DEBUG - Properties: " + props);
                                
                                // Extraer campos con manejo flexible
                                String nombre = "";
                                String clientIdValue = "";
                                String plan = "";
                                double deducibleAnual = 0;
                                double deducibleUsado = 0;
                                
                                java.util.Iterator<String> fieldNames = props.fieldNames();
                                while (fieldNames.hasNext()) {
                                    String fieldName = fieldNames.next();
                                    com.fasterxml.jackson.databind.JsonNode fieldValue = props.get(fieldName);
                                    
                                    // Nombre - campo title (exact match)
                                    if (fieldName.equals("Nombre") && fieldValue.has("title") && fieldValue.get("title").size() > 0) {
                                        nombre = fieldValue.get("title").get(0).get("plain_text").asText("");
                                    }
                                    // ClientId - campo rich_text (exact match)
                                    if (fieldName.equals("ClientId") && fieldValue.has("rich_text") && fieldValue.get("rich_text").size() > 0) {
                                        clientIdValue = fieldValue.get("rich_text").get(0).get("plain_text").asText("");
                                    }
                                    // Plan - campo select (exact match)
                                    if (fieldName.equals("Plan") && fieldValue.has("select") && fieldValue.get("select").has("name")) {
                                        plan = fieldValue.get("select").get("name").asText("");
                                    }
                                    // Deducible anual - campo number (exact match)
                                    if (fieldName.equals("Deducible anual (USD)") && fieldValue.has("number")) {
                                        deducibleAnual = fieldValue.get("number").asDouble(0);
                                    }
                                    // Deducible usado - campo number (exact match)
                                    if (fieldName.equals("Deducible usado (USD)") && fieldValue.has("number")) {
                                        deducibleUsado = fieldValue.get("number").asDouble(0);
                                    }
                                }
                                System.out.println("DEBUG - Valores extraídos: nombre='" + nombre + "', clientId='" + clientIdValue + "', plan='" + plan + "', deducibleAnual=" + deducibleAnual + ", deducibleUsado=" + deducibleUsado);
                                PatientDto dto = PatientDto.builder()
                                        .nombre(nombre)
                                        .clientId(clientIdValue)
                                        .plan(plan)
                                        .deducibleAnual(deducibleAnual)
                                        .deducibleUsado(deducibleUsado)
                                        .build();
                                pacientes.add(dto);
                            }
                        }
                        return pacientes;
                    } catch (Exception e) {
                        throw new RuntimeException("Error parseando pacientes de Notion: " + e.getMessage());
                    }
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    System.out.println("Error consultando pacientes en Notion: " + ex.getMessage());
                    // Retornar datos mock en caso de error
                    java.util.List<PatientDto> mockPacientes = java.util.List.of(
                        PatientDto.builder()
                            .clientId("paciente-001")
                            .nombre("Paciente de Prueba")
                            .plan("Premium")
                            .deducibleAnual(500.0)
                            .deducibleUsado(0.0)
                            .build()
                    );
                    return Mono.just(mockPacientes);
                });
    }

    /**
     * Obtiene todos los pacientes de la base de datos
     * @return Mono<List<PatientDto>> con todos los pacientes
     */
    public Mono<java.util.List<PatientDto>> getPacientes() {
        if (notionToken == null || notionToken.isEmpty() || pacientesDbId == null || pacientesDbId.isEmpty()) {
            System.out.println("DEBUG - Variables de Notion no configuradas, usando pacientes mock");
            java.util.List<PatientDto> mockPacientes = java.util.List.of(
                PatientDto.builder()
                    .clientId("paciente-001")
                    .nombre("Paciente de Prueba")
                    .plan("Premium")
                    .deducibleAnual(500.0)
                    .deducibleUsado(0.0)
                    .build()
            );
            return Mono.just(mockPacientes);
        }
        
        return notionWebClient.post()
                .uri(uriBuilder -> uriBuilder.path("/databases/{databaseId}/query").build(pacientesDbId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + notionToken)
                .header("Notion-Version", "2022-06-28")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> {
                    // TODO: Mapear la respuesta JSON de Notion a una lista de PatientDto
                    java.util.List<PatientDto> pacientes = new java.util.ArrayList<>();
                    // Implementar mapeo aquí
                    return pacientes;
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    System.out.println("Error consultando pacientes en Notion: " + ex.getMessage());
                    java.util.List<PatientDto> mockPacientes = java.util.List.of(
                        PatientDto.builder()
                            .clientId("paciente-001")
                            .nombre("Paciente de Prueba")
                            .plan("Premium")
                            .deducibleAnual(500.0)
                            .deducibleUsado(0.0)
                            .build()
                    );
                    return Mono.just(mockPacientes);
                });
    }

    /**
     * Obtiene todos los planes de la base de datos
     * @return Mono<List<PlanCoberturaDto>> con todos los planes
     */
    public Mono<java.util.List<PlanCoberturaDto>> getPlanes() {
        if (notionToken == null || notionToken.isEmpty() || planesDbId == null || planesDbId.isEmpty()) {
            System.out.println("DEBUG - Variables de Notion no configuradas, usando planes mock");
            java.util.List<PlanCoberturaDto> mockPlanes = java.util.List.of(
                PlanCoberturaDto.builder()
                    .plan("Premium")
                    .coberturaConsulta(90)
                    .coberturaEmergencia(100)
                    .deducibleAnual(500.0)
                    .build()
            );
            return Mono.just(mockPlanes);
        }
        
        return notionWebClient.post()
                .uri(uriBuilder -> uriBuilder.path("/databases/{databaseId}/query").build(planesDbId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + notionToken)
                .header("Notion-Version", "2022-06-28")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> {
                    // TODO: Mapear la respuesta JSON de Notion a una lista de PlanCoberturaDto
                    java.util.List<PlanCoberturaDto> planes = new java.util.ArrayList<>();
                    // Implementar mapeo aquí
                    return planes;
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    System.out.println("Error consultando planes en Notion: " + ex.getMessage());
                    java.util.List<PlanCoberturaDto> mockPlanes = java.util.List.of(
                        PlanCoberturaDto.builder()
                            .plan("Premium")
                            .coberturaConsulta(90)
                            .coberturaEmergencia(100)
                            .deducibleAnual(500.0)
                            .build()
                    );
                    return Mono.just(mockPlanes);
                });
    }

    /**
     * Obtiene todos los hospitales de la base de datos
     * @return Mono<List<HospitalDto>> con todos los hospitales
     */
    public Mono<java.util.List<HospitalDto>> getHospitales() {
        if (notionToken == null || notionToken.isEmpty() || hospitalesDbId == null || hospitalesDbId.isEmpty()) {
            System.out.println("DEBUG - Variables de Notion no configuradas, usando hospitales mock");
            java.util.List<HospitalDto> mockHospitales = java.util.List.of(
                HospitalDto.builder()
                    .nombre("Hospital Vozandes")
                    .planesAceptados(java.util.List.of("premium", "plus", "basico"))
                    .especialidades(java.util.List.of("medicina general", "cardiologia", "neurologia"))
                    .precioConsultaGeneral(50)
                    .precioConsultaEspecialista(85)
                    .precioEmergencia(120)
                    .calificacion(4.8)
                    .tiempoEsperaPromedio("15 minutos")
                    .build()
            );
            return Mono.just(mockHospitales);
        }
        
        return notionWebClient.post()
                .uri(uriBuilder -> uriBuilder.path("/databases/{databaseId}/query").build(hospitalesDbId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + notionToken)
                .header("Notion-Version", "2022-06-28")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> {
                    // TODO: Mapear la respuesta JSON de Notion a una lista de HospitalDto
                    java.util.List<HospitalDto> hospitales = new java.util.ArrayList<>();
                    // Implementar mapeo aquí
                    return hospitales;
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    System.out.println("Error consultando hospitales en Notion: " + ex.getMessage());
                    java.util.List<HospitalDto> mockHospitales = java.util.List.of(
                        HospitalDto.builder()
                            .nombre("Hospital Vozandes")
                            .planesAceptados(java.util.List.of("premium", "plus", "basico"))
                            .especialidades(java.util.List.of("medicina general", "cardiologia", "neurologia"))
                            .precioConsultaGeneral(50)
                            .precioConsultaEspecialista(85)
                            .precioEmergencia(120)
                            .calificacion(4.8)
                            .tiempoEsperaPromedio("15 minutos")
                            .build()
                    );
                    return Mono.just(mockHospitales);
                });
    }
}
