package com.hackathon.copayagent.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.copayagent.client.NotionClient;
import com.hackathon.copayagent.dto.ChatRequest;
import com.hackathon.copayagent.dto.ChatResponse;
import com.hackathon.copayagent.dto.HospitalDto;
import com.hackathon.copayagent.dto.PlanCoberturaDto;
import com.hackathon.copayagent.service.CopayService;
import com.hackathon.copayagent.service.HospitalRecommendationService;
import com.hackathon.copayagent.service.IntelligentAnalysisService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
// import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    // Inyección de dependencias de los servicios principales
    private final NotionClient notionClient;
    private final CopayService copayService;
    private final HospitalRecommendationService hospitalRecommendationService;
    private final IntelligentAnalysisService intelligentAnalysisService;

    /**
     * Endpoint principal conversacional.
     * Orquesta el flujo: IA -> Notion -> lógica de negocio -> respuesta consolidada.
     * @param request ChatRequest con clientId y mensaje del usuario
     * @return ChatResponse con especialidad, prioridad, cobertura, copago y hospital recomendado
     */
    @PostMapping
    public Mono<ResponseEntity<ChatResponse>> chat(@Valid @RequestBody ChatRequest request) {
        // 1. Detectar especialidad y prioridad usando Notion (keywords) primero
        return notionClient.getEspecialidadPorSintoma(request.getMessage())
            .flatMap(especialidadSintoma -> {
                if (especialidadSintoma == null) {
                    return Mono.just(ResponseEntity.badRequest().body(ChatResponse.builder().specialty(null).priority(null).coverage(null).estimatedCopay(0d).recommendedHospital(null).build()));
                }
                String especialidad = especialidadSintoma.getEspecialidad();
                String tipoConsulta = especialidadSintoma.getTipoConsulta();
                String prioridad = especialidadSintoma.getPrioridad();
                
                // 2. Obtener paciente
                return notionClient.getPacienteByClientId(request.getClientId())
                    .flatMap(paciente -> {
                        // 3. Obtener plan de cobertura
                        return notionClient.getPlanCoberturaByNombre(paciente.getPlan())
                            .flatMap(plan -> {
                                // 4. Obtener hospitales compatibles
                                return notionClient.getHospitalesByPlanYEspecialidad(paciente.getPlan(), especialidad)
                                    .flatMap(hospitales -> {
                                        // 5. Usar IA inteligente para análisis y recomendaciones
                                        return intelligentAnalysisService.analyzePatientMessage(
                                                request.getMessage(), 
                                                paciente, 
                                                plan, 
                                                hospitales
                                            )
                                            .map(analysis -> {
                                                // 6. Construir respuesta inteligente basada en análisis de IA
                                                // Caso especial: cuando el backend necesita más info, el frontend actual
                                                // espera `priority=NECESITA_MAS_INFO` y usa `coverage` para mostrar la pregunta.
                                                boolean needsMoreInfo = "NECESITA_MAS_INFO".equalsIgnoreCase(analysis.getRecomendacionesAdicionales());
                                                Double estimatedCopay = needsMoreInfo
                                                    ? 0d
                                                    : java.util.Optional.ofNullable(analysis.getCopagoEstimado()).orElse(0d);

                                                ChatResponse response = ChatResponse.builder()
                                                        .specialty(needsMoreInfo ? null : analysis.getEspecialidad())
                                                        .coverage(needsMoreInfo ? analysis.getRazonRecomendacion() : (analysis.getCoberturaAplicada() + "%"))
                                                    .estimatedCopay(estimatedCopay)
                                                        .recommendedHospital(needsMoreInfo ? null : analysis.getHospitalRecomendado())
                                                        .priority(needsMoreInfo ? "NECESITA_MAS_INFO" : analysis.getPrioridad())
                                                    .probableDiseases(needsMoreInfo ? null : analysis.getEnfermedadesProbables())
                                                    .selectedDisease(needsMoreInfo ? null : analysis.getEnfermedadMasProbable())
                                                    .triageSummary(needsMoreInfo ? null : analysis.getResumenTriage())
                                                    .aiTraceId(analysis.getAiTraceId())
                                                        .build();
                                                return ResponseEntity.ok(response);
                                            })
                                            // Fallback al cálculo tradicional si IA falla
                                            .onErrorResume(ex -> {
                                                // 5. Recomendar hospital con menor copago (tradicional)
                                                HospitalDto hospital = hospitalRecommendationService.recomendarHospitalMenorCopago(
                                                        hospitales,
                                                        tipoConsulta,
                                                        getCoberturaPorTipoConsulta(plan, tipoConsulta),
                                                        paciente.getDeducibleAnual(),
                                                        paciente.getDeducibleUsado(),
                                                        copayService
                                                );
                                                // 6. Calcular copago estimado (tradicional)
                                                double copago = hospitalRecommendationService.calcularCopagoHospital(
                                                        hospital,
                                                        tipoConsulta,
                                                        getCoberturaPorTipoConsulta(plan, tipoConsulta),
                                                        paciente.getDeducibleAnual(),
                                                        paciente.getDeducibleUsado(),
                                                        copayService
                                                );
                                                // 7. Construir respuesta tradicional
                                                ChatResponse response = ChatResponse.builder()
                                                        .specialty(especialidad)
                                                        .coverage(getCoberturaPorTipoConsulta(plan, tipoConsulta) + "%")
                                                        .estimatedCopay(copago)
                                                        .recommendedHospital(hospital != null ? hospital.getNombre() : null)
                                                        .priority(prioridad)
                                                    .probableDiseases(java.util.List.of(especialidad))
                                                    .selectedDisease(especialidad)
                                                    .triageSummary("Fallback tradicional por indisponibilidad de IA")
                                                        .build();
                                                return Mono.just(ResponseEntity.ok(response));
                                            });
                                    });
                            });
                    });
            })
            .onErrorResume(ex -> {
                log.error("Error en /api/chat: {}", ex.getMessage(), ex);
                return Mono.just(ResponseEntity.internalServerError().body(
                    ChatResponse.builder()
                        .specialty("ERROR")
                        .priority("ERROR")
                        .coverage("ERROR")
                        .estimatedCopay(0d)
                        .recommendedHospital(ex.getMessage())
                        .build()
                ));
            });
    }

    /**
     * Obtiene el porcentaje de cobertura según el tipo de consulta.
     * Los tipos de consulta reales son: "General" y "Especialista"
     */
    private double getCoberturaPorTipoConsulta(PlanCoberturaDto plan, String tipoConsulta) {
        return switch (tipoConsulta) {
            case "General" -> plan.getCoberturaConsulta();
            case "Especialista" -> plan.getCoberturaConsulta();
            case "Emergencia" -> plan.getCoberturaEmergencia();
            case "Exámenes", "Examenes" -> plan.getCoberturaExamenes();
            case "Cirugía", "Cirugia" -> plan.getCoberturaCirugia();
            case "Hospitalización", "Hospitalizacion" -> plan.getCoberturaHospitalizacion();
            default -> plan.getCoberturaConsulta();
        };
    }

    /**
     * Construye el prompt para OpenAI con el formato esperado.
     */
    // Métodos de OpenAI eliminados tras refactorización a flujo Notion-first
}
