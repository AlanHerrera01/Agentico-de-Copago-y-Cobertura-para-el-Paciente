package com.hackathon.copayagent.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.hackathon.copayagent.client.AiClient;
import com.hackathon.copayagent.client.GitHubModelsClient;
import com.hackathon.copayagent.client.GroqClient;
import com.hackathon.copayagent.client.NotionClient;
import com.hackathon.copayagent.client.OpenAiClient;
import com.hackathon.copayagent.dto.EspecialidadSintomaDto;
import com.hackathon.copayagent.dto.HospitalDto;
import com.hackathon.copayagent.dto.IntelligentAnalysisResult;
import com.hackathon.copayagent.dto.OpenAiRequest;
import com.hackathon.copayagent.dto.PatientDto;
import com.hackathon.copayagent.dto.PlanCoberturaDto;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class IntelligentAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(IntelligentAnalysisService.class);

    private final OpenAiClient openAiClient;
    private final GroqClient groqClient;
    private final GitHubModelsClient gitHubModelsClient;
    private final NotionClient notionClient;

    @Value("${AI_PROVIDER:openai}")
    private String aiProvider;

    @Value("${OPENAI_MODEL:gpt-4o-mini}")
    private String openAiModel;

    /**
     * Analiza inteligentemente el mensaje del paciente usando IA
     * y devuelve especialidad, prioridad y recomendaciones basadas en tarifas
     */
    public Mono<IntelligentAnalysisResult> analyzePatientMessage(String message, PatientDto paciente, PlanCoberturaDto plan, List<HospitalDto> hospitales) {
        String safeMessage = message == null ? "" : message.trim();
        String aiTraceId = UUID.randomUUID().toString();

        log.info("AI_PROCESSING STARTED");
        log.info("AI_FLOW [{}] Message: {}", aiTraceId, safeMessage);
        log.info("AI_FLOW [{}] Patient: {} (Plan: {})", aiTraceId, paciente != null ? paciente.getNombre() : "NULL", plan != null ? plan.getPlan() : "NULL");
        log.info("AI_FLOW [{}] Available Hospitals: {}", aiTraceId, hospitales != null ? hospitales.size() : 0);
        log.info("AI_FLOW [{}] Provider: {} | Model: {}", aiTraceId, aiProvider, openAiModel);

        if (hasRedFlagSymptoms(safeMessage)) {
            log.warn("AI_FLOW [{}] red flags detected, forcing critical triage", aiTraceId);
            return Mono.just(createEmergencyAnalysisFromRedFlags(safeMessage, paciente, plan, hospitales, aiTraceId));
        }

        // Guardrail mejorado: si el mensaje es vago, usar IA profesional para pedir información específica
        if (shouldAskClarifyingQuestions(safeMessage)) {
            log.info("AI_FLOW [{}] Using professional AI to gather specific medical information", aiTraceId);
            return handleMedicalClarification(safeMessage, aiTraceId);
        }

        // First, check if the message is medical or conversational
        log.info("AI_FLOW [{}] Checking if message is medical or conversational...", aiTraceId);
        boolean isMedical = isMedicalMessage(safeMessage);
        log.info("AI_FLOW [{}] Message classified as: {}", aiTraceId, isMedical ? "MEDICAL" : "CONVERSATIONAL");

        if (!isMedical) {
            // Handle conversational messages with natural AI response
            return handleConversationalMessage(safeMessage, aiTraceId);
        }

        log.info("AI_FLOW [{}] sending clinical triage to provider={}", aiTraceId, aiProvider);

        // Proceder directamente con diagnóstico clínico usando IA
        // Primero intentar con Notion para obtener especialidades, pero si falla usar defaults
        log.info("AI_FLOW [{}] Loading specialties from Notion...", aiTraceId);
        Mono<DetectedInfo> detectedInfoMono = notionClient.getEspecialidades()
                .map(especialidades -> {
                    log.info("AI_FLOW [{}] Loaded {} specialties from Notion", aiTraceId, especialidades.size());
                    
                    // Detectar especialidad usando keywords
                    String detectedSpecialty = detectSpecialtyFromKeywords(safeMessage, especialidades);
                    String detectedPriority = getPriorityForSpecialty(detectedSpecialty, especialidades);
                    String detectedTipoConsulta = getTipoConsultaForSpecialty(detectedSpecialty, especialidades);

                    log.info("AI_FLOW [{}] KEYWORD ANALYSIS RESULTS:", aiTraceId);
                    log.info("   Detected Specialty: {}", detectedSpecialty);
                    log.info("   Detected Priority: {}", detectedPriority);
                    log.info("   Detected Consultation Type: {}", detectedTipoConsulta);

                    return new DetectedInfo(detectedSpecialty, detectedPriority, detectedTipoConsulta);
                })
                .onErrorResume(ex -> {
                    log.warn("AI_FLOW [{}] notion specialties unavailable, fallback defaults: {}", aiTraceId, ex.getMessage());
                    // Usar detección simple por defecto si Notion falla
                    String detectedSpecialty = detectSpecialtySimple(safeMessage);
                    String detectedPriority = "Media";
                    String detectedTipoConsulta = "General";

                    return Mono.just(new DetectedInfo(detectedSpecialty, detectedPriority, detectedTipoConsulta));
                });

        return detectedInfoMono.flatMap(detectedInfo -> {
            String prompt = buildIntelligentPrompt(safeMessage, paciente, plan, hospitales);

            OpenAiRequest request = OpenAiRequest.builder()
                    .model(openAiModel)
                    .messages(java.util.List.of(
                            new OpenAiRequest.Message("system", "Eres un asistente médico. Tu trabajo es hacer triaje: pedir aclaraciones cuando falte información y, solo cuando haya datos suficientes, proponer una especialidad y prioridad."),
                            new OpenAiRequest.Message("user", prompt)
                    ))
                    .maxTokens(500)
                    .temperature(0.3)
                    .build();

            // Elegir el cliente de IA según la configuración
            AiClient aiClient = switch (aiProvider.toLowerCase()) {
                case "groq" -> groqClient;
                case "github-models" -> gitHubModelsClient;
                default -> openAiClient;
            };

            return aiClient.getSpecialtyAndPriority(request)
                    .map(response -> {
                        String aiResponseContent = response.getChoices().get(0).getMessage().getContent();
                        log.info("AI_RESPONSE [{}] Raw response from {}: {}", aiTraceId, aiProvider, aiResponseContent);
                        
                        IntelligentAnalysisResult result = parseIntelligentResponse(aiResponseContent, safeMessage, paciente, plan, hospitales, aiTraceId);
                        
                        log.info("AI_PROCESSING COMPLETED [{}]", aiTraceId);
                        log.info("Final Specialty: {}", result.getEspecialidad());
                        log.info("Final Priority: {}", result.getPrioridad());
                        log.info("Final Consultation Type: {}", result.getTipoConsulta());
                        log.info("Recommended Hospital: {}", result.getHospitalRecomendado());
                        log.info("Estimated Price: ${} | Copay: ${} | Coverage: {}%", 
                            result.getPrecioEstimado(), result.getCopagoEstimado(), result.getCoberturaAplicada());
                        
                        return result;
                    })
                    .onErrorResume(ex -> {
                        // Fallback usando especialidad detectada por Notion si la IA falla
                        log.error("AI_PROVIDER_FAILED [{}] {} failed, using fallback: {}", aiTraceId, aiProvider, ex.getMessage());
                        
                        IntelligentAnalysisResult fallbackResult = createAnalysisWithDetectedSpecialty(detectedInfo.specialty, detectedInfo.priority, detectedInfo.tipoConsulta, paciente, plan, hospitales, aiTraceId);
                        
                        log.info("AI_FALLBACK_COMPLETED [{}] Using keyword-based detection", aiTraceId);
                        log.info("Fallback Specialty: {}", fallbackResult.getEspecialidad());
                        log.info("Fallback Priority: {}", fallbackResult.getPrioridad());
                        
                        return Mono.just(fallbackResult);
                    });
        });
    }

    /**
     * Maneja la aclaración médica usando IA profesional para pedir información específica
     */
    private Mono<IntelligentAnalysisResult> handleMedicalClarification(String message, String aiTraceId) {
        log.info("AI_FLOW [{}] Generating professional medical clarification questions", aiTraceId);
        
        String medicalPrompt = "Eres el Dr. PanIA, un médico profesional. El paciente te ha dado información breve: '" + message + "'. " +
            "Tu tarea es hacer preguntas específicas para entender mejor su situación. " +
            "Siempre responde comenzando con 'Dr. PanIA: '. " +
            "Usa lenguaje médico profesional pero claro. " +
            "No des diagnósticos, solo pide información que necesites. " +
            "Adapta tus preguntas según los síntomas: " +
            "- CARDIOLOGIA (dolor pecho, palpitaciones, presion alta): pregunta tipo de dolor, irradiacion, dificultad respirar, factores desencadenantes " +
            "- NEUROLOGIA (dolor cabeza, mareos, convulsiones): pregunta localizacion, intensidad, fotofobia, fonofobia, nauseas, perdida conciencia " +
            "- GASTROENTEROLOGIA (dolor estomago, nausea, vomito, diarrea): pregunta localizacion, relacion alimentos, caracteristicas heces, sangre " +
            "- NEUMOLOGIA (tos, dificultad respirar, dolor pecho): pregunta tipo tos, flema, disnea, sibilancias, fiebre " +
            "- TRAUMATOLOGIA (lesiones, fracturas, dolor articulaciones): pregunta mecanismo lesion, localizacion, inflamacion, movilidad " +
            "- DERMATOLOGIA (erupciones, picazon, manchas): pregunta tipo lesion, distribucion, factores desencadenantes, fiebre " +
            "- OFTALMOLOGIA (ojos rojos, vision, dolor ocular): pregunta vision, secrecion, fotofobia, trauma, tiempo evolucion " +
            "- GINECOLOGIA (dolor pelvicico, menstruacion): pregunta localizacion, caracteristicas sangrado, ciclo menstrual, sintomas asociados " +
            "- UROLOGIA (dolor orinar, sangre orina, prostata): pregunta tipo dolor, frecuencia, color orina, fiebre, disfuncion " +
            "- PSIQUIATRIA (ansiedad, depresion, insomnio): pregunta duracion, intensidad, funcionamiento diario, pensamientos, sintomas fisicos " +
            "- PEDIATRIA (sintomas en niños): pregunta edad, peso, fiebre, apetito, comportamiento, signos alarma " +
            "- ENDOCRINOLOGIA (diabetes, tiroides): pregunta sed, orina, peso, temblores, intolerancia temperatura " +
            "-OTORRINOLARINGOLOGIA (oido, garganta, nariz): pregunta localizacion, secrecion, audicion, dificultad tragar, fiebre " +
            "Sé breve y directo. Muestra interés en ayudar al paciente.";
        
        OpenAiRequest request = OpenAiRequest.builder()
                .model(openAiModel)
                .messages(java.util.List.of(
                        new OpenAiRequest.Message("system", "Eres un médico profesional. Tu objetivo es recopilar información médica específica haciendo preguntas relevantes y breves. Muestra empatía pero sé directo."),
                        new OpenAiRequest.Message("user", medicalPrompt)
                ))
                .maxTokens(200)
                .temperature(0.4)
                .build();

        // Elegir el cliente de IA según la configuración
        AiClient aiClient = switch (aiProvider.toLowerCase()) {
            case "groq" -> groqClient;
            case "github-models" -> gitHubModelsClient;
            default -> openAiClient;
        };

        return aiClient.getSpecialtyAndPriority(request)
                .map(response -> {
                    String aiResponseContent = response.getChoices().get(0).getMessage().getContent();
                    log.info("AI_MEDICAL_CLARIFICATION [{}]: {}", aiTraceId, aiResponseContent);
                    
                    return IntelligentAnalysisResult.builder()
                            .especialidad(null)
                            .prioridad(null)
                            .tipoConsulta(null)
                            .hospitalRecomendado(null)
                            .razonRecomendacion(aiResponseContent.trim())
                            .precioEstimado(0.0)
                            .copagoEstimado(0.0)
                            .coberturaAplicada(0.0)
                            .recomendacionesAdicionales("NECESITA_MAS_INFO")
                            .enfermedadesProbables(List.of())
                            .enfermedadMasProbable(null)
                            .resumenTriage("Recopilando información médica específica")
                            .aiTraceId(aiTraceId)
                            .build();
                })
                .onErrorResume(ex -> {
                    log.error("AI_MEDICAL_CLARIFICATION_ERROR [{}]: {}", aiTraceId, ex.getMessage());
                    // Fallback profesional si la IA falla
                    return Mono.just(IntelligentAnalysisResult.builder()
                            .especialidad(null)
                            .prioridad(null)
                            .tipoConsulta(null)
                            .hospitalRecomendado(null)
                            .razonRecomendacion(getProfessionalClarificationFallback(message))
                            .precioEstimado(0.0)
                            .copagoEstimado(0.0)
                            .coberturaAplicada(0.0)
                            .recomendacionesAdicionales("NECESITA_MAS_INFO")
                            .enfermedadesProbables(List.of())
                            .enfermedadMasProbable(null)
                            .resumenTriage("Recopilando información médica específica")
                            .aiTraceId(aiTraceId)
                            .build());
                });
    }

    /**
     * Fallback profesional para aclaraciones médicas
     */
    private String getProfessionalClarificationFallback(String message) {
        String messageLower = message.toLowerCase();
        
        if (messageLower.contains("dolor")) {
            return "Dr. PanIA: Entiendo que tienes dolor. Para ayudarte mejor, necesito saber: ¿dónde te duele exactamente?, ¿cuándo empezó?, ¿qué tipo de dolor es?, ¿qué tan fuerte es de 1 a 10?";
        } else if (messageLower.contains("fiebre")) {
            return "Dr. PanIA: Veo que tienes fiebre. ¿Podrías decirme cuántos grados tienes?, ¿desde cuándo?, ¿tienes otros síntomas como tos o dolor de cuerpo?";
        } else if (messageLower.contains("cabeza")) {
            return "Dr. PanIA: Entiendo que tienes dolor de cabeza. ¿Es en toda la cabeza o en un lado?, ¿te molesta la luz o el sonido?, ¿desde cuándo lo tienes?";
        } else if (messageLower.contains("estómago")) {
            return "Dr. PanIA: Comprendo que tienes molestias estomacales. ¿Dónde exactamente duele?, ¿después de comer?, ¿tienes náuseas o vómito?";
        } else if (messageLower.contains("pecho")) {
            return "Dr. PanIA: Entiendo que tienes molestias en el pecho. ¿Es un dolor opresivo o punzante?, ¿se irradia a otro lado?, ¿tienes dificultad para respirar?";
        } else {
            return "Dr. PanIA: Hola, gracias por contactarme. Para poder ayudarte mejor, necesito hacerte algunas preguntas. ¿Tienes algún síntoma específico que te preocupe en este momento? Si es así, ¿puedes describirlo?";
        }
    }

    /**
     * Detecta si un mensaje es de naturaleza médica o es conversacional
     */
    private boolean isMedicalMessage(String message) {
        String messageLower = message.toLowerCase();
        
        // Palabras clave médicas que indican que el usuario busca ayuda médica
        String[] medicalKeywords = {
            "dolor", "duele", "dolor", "síntoma", "síntomas", "enfermo", "enferma", "enfermedad",
            "fiebre", "tos", "resfriado", "gripe", "cansancio", "fatiga", "malestar",
            "cabeza", "estómago", "pecho", "garganta", "pulmón", "corazón", "presión",
            "médico", "doctor", "doctora", "especialista", "consulta", "revisión", "chequeo",
            "medicina", "tratamiento", "diagnóstico", "receta", "pastillas", "remedio",
            "náuseas", "vómito", "mareo", "vértigo", "picazón", "sarpullido", "erupción",
            "fractura", "herida", "corte", "quemadura", "inflamación", "hinchazón",
            "sangre", "sangrar", "hemorragia", "presión alta", "diabetes", "colesterol"
        };
        
        // Saludos y palabras conversacionales comunes
        String[] conversationalKeywords = {
            "hola", "buenos días", "buenas tardes", "buenas noches", "gracias", "adiós",
            "cómo estás", "qué tal", "bien", "mal", "regular", "ok", "perfecto",
            "ayuda", "favor", "por favor", "disculpa", "perdona", "lo siento"
        };
        
        // Primero verificar si es puramente conversacional
        for (String convKeyword : conversationalKeywords) {
            if (messageLower.trim().equals(convKeyword) || 
                messageLower.startsWith(convKeyword + " ") || 
                messageLower.endsWith(" " + convKeyword)) {
                // Si es solo un saludo o algo muy simple, considerarlo conversacional
                if (messageLower.split(" ").length <= 3) {
                    log.info("Message detected as purely conversational: {}", convKeyword);
                    return false;
                }
            }
        }
        
        // Verificar si contiene palabras clave médicas
        for (String medKeyword : medicalKeywords) {
            if (messageLower.contains(medKeyword)) {
                log.info("Medical keyword detected: {}", medKeyword);
                return true;
            }
        }
        
        // Si no hay palabras clave claras, usar heurística basada en longitud y contexto
        if (messageLower.length() < 10) {
            // Mensajes muy cortos sin palabras médicas son probablemente conversacionales
            log.info("Short message classified as conversational");
            return false;
        }
        
        // Por defecto, si hay duda, tratar como médico para ser conservador
        log.info("Ambiguous message, defaulting to medical for safety");
        return true;
    }

    /**
     * Maneja mensajes conversacionales con una respuesta natural de la IA
     */
    private Mono<IntelligentAnalysisResult> handleConversationalMessage(String message, String aiTraceId) {
        log.info("AI_FLOW [{}] Handling conversational message", aiTraceId);
        
        String conversationalPrompt = "Eres el Dr. PanIA, un médico asistente. Responde siempre comenzando con 'Dr. PanIA: '. Sé profesional y directo. Si el usuario necesita atención médica, invítale a describir sus síntomas. Mensaje: " + message;
        
        OpenAiRequest request = OpenAiRequest.builder()
                .model(openAiModel)
                .messages(java.util.List.of(
                        new OpenAiRequest.Message("system", "Eres el Dr. PanIA, un médico asistente. Sé profesional y siempre firma con 'Dr. PanIA:'."),
                        new OpenAiRequest.Message("user", conversationalPrompt)
                ))
                .maxTokens(150)
                .temperature(0.7)
                .build();

        // Elegir el cliente de IA según la configuración
        AiClient aiClient = switch (aiProvider.toLowerCase()) {
            case "groq" -> groqClient;
            case "github-models" -> gitHubModelsClient;
            default -> openAiClient;
        };

        return aiClient.getSpecialtyAndPriority(request)
                .map(response -> {
                    String aiResponseContent = response.getChoices().get(0).getMessage().getContent();
                    log.info("AI_CONVERSATIONAL_RESPONSE [{}]: {}", aiTraceId, aiResponseContent);
                    
                    // Devolver un resultado conversacional
                    return IntelligentAnalysisResult.builder()
                            .especialidad(null) // No hay especialidad para mensajes conversacionales
                            .prioridad(null)
                            .tipoConsulta(null)
                            .hospitalRecomendado(null)
                            .razonRecomendacion(aiResponseContent.trim())
                            .precioEstimado(0.0)
                            .copagoEstimado(0.0)
                            .coberturaAplicada(0.0)
                            .recomendacionesAdicionales("CONVERSACIONAL")
                            .enfermedadesProbables(List.of())
                            .enfermedadMasProbable(null)
                            .resumenTriage("Mensaje conversacional")
                            .aiTraceId(aiTraceId)
                            .build();
                })
                .onErrorResume(ex -> {
                    log.error("AI_CONVERSATIONAL_ERROR [{}]: {}", aiTraceId, ex.getMessage());
                    // Fallback simple si la IA falla
                    String fallbackResponse = getFallbackConversationalResponse(message);
                    return Mono.just(IntelligentAnalysisResult.builder()
                            .especialidad(null)
                            .prioridad(null)
                            .tipoConsulta(null)
                            .hospitalRecomendado(null)
                            .razonRecomendacion(fallbackResponse)
                            .precioEstimado(0.0)
                            .copagoEstimado(0.0)
                            .coberturaAplicada(0.0)
                            .recomendacionesAdicionales("CONVERSACIONAL")
                            .enfermedadesProbables(List.of())
                            .enfermedadMasProbable(null)
                            .resumenTriage("Mensaje conversacional")
                            .aiTraceId(aiTraceId)
                            .build());
                });
    }

    /**
     * Respuestas conversacionales de fallback si la IA falla
     */
    private String getFallbackConversationalResponse(String message) {
        String messageLower = message.toLowerCase().trim();
        
        if (messageLower.equals("hola") || messageLower.startsWith("hola")) {
            return "Dr. PanIA: Hola, gracias por contactarme. Soy tu asistente médico. ¿En qué puedo ayudarte hoy?";
        } else if (messageLower.contains("gracias")) {
            return "Dr. PanIA: De nada. Estoy aquí para ayudarte con cualquier consulta médica que necesites.";
        } else if (messageLower.contains("cómo estás")) {
            return "Dr. PanIA: Estoy bien, gracias. Más importante, ¿cómo te sientes tú? ¿Hay algo en lo que pueda ayudarte?";
        } else if (messageLower.contains("ayuda")) {
            return "Dr. PanIA: Claro que sí te ayudo. ¿Podrías describirme qué síntomas tienes o qué te preocupa?";
        } else {
            return "Dr. PanIA: Hola, soy tu médico asistente. Si tienes algún síntoma o pregunta médica, estaré encantado de ayudarte.";
        }
    }
    
    private String detectSpecialtySimple(String message) {
        String messageLower = message.toLowerCase();
        
        // Detección simple basada en palabras clave críticas
        if (messageLower.contains("pecho") || messageLower.contains("cardi") || 
            messageLower.contains("palpitacion") || messageLower.contains("presion") ||
            messageLower.contains("infarto") || messageLower.contains("brazo izquierdo")) {
            return "Cardiologia";
        }
        
        if (messageLower.contains("cabeza") || messageLower.contains("migra") ||
            messageLower.contains("mareo") || messageLower.contains("vertigo") ||
            messageLower.contains("convulsion") || messageLower.contains("debilidad")) {
            return "Neurologia";
        }
        
        if (messageLower.contains("niño") || messageLower.contains("hijo") || 
            messageLower.contains("hija") || messageLower.contains("bebe") ||
            messageLower.contains("pediatric")) {
            return "Pediatria";
        }
        
        return "Medicina General";
    }

    private String detectSpecialtyFromKeywords(String message, List<EspecialidadSintomaDto> especialidades) {
        try {
            log.info("KEYWORD DETECTION - Starting analysis");
            log.info("Input message: {}", message);

            if (especialidades == null || especialidades.isEmpty()) {
                log.warn("No specialties available in database, defaulting to Medicina General");
                return "Medicina General";
            }

            // Extraer palabras clave de enfermedades del mensaje
            java.util.List<String> palabrasEncontradas = extraerPalabrasEnfermedades(message);
            log.info("Extracted keywords: {}", palabrasEncontradas);

            String messageLower = message.toLowerCase();
            java.util.Map<String, Integer> puntajeEspecialidades = new java.util.HashMap<>();

            log.info("Evaluating {} specialties against keywords...", especialidades.size());

            // Evaluar cada especialidad contra las palabras encontradas
            for (EspecialidadSintomaDto especialidad : especialidades) {
                int puntaje = 0;
                java.util.List<String> coincidencias = new java.util.ArrayList<>();
                log.info("Evaluating specialty: {} | Keywords: {}", especialidad.getEspecialidad(), especialidad.getKeywords());

                // Buscar coincidencias exactas en keywords usando coincidencia de palabras completas
                for (String keyword : especialidad.getKeywords()) {
                    String keywordLower = keyword.toLowerCase();
                    if (containsWord(messageLower, keywordLower)) {
                        puntaje += 10; // Puntaje alto para coincidencia exacta
                        coincidencias.add(keyword);
                    }
                }

                // Buscar coincidencias parciales con palabras extraídas (solo para palabras simples)
                for (String palabra : palabrasEncontradas) {
                    for (String keyword : especialidad.getKeywords()) {
                        String keywordLower = keyword.toLowerCase();
                        // Solo permitir coincidencias parciales para palabras simples, no frases
                        if (!keywordLower.contains(" ") && (palabra.equals(keywordLower) || keywordLower.equals(palabra))) {
                            puntaje += 5; // Puntaje medio para coincidencia parcial
                            if (!coincidencias.contains(keyword)) {
                                coincidencias.add(keyword);
                            }
                        }
                    }
                }

                if (puntaje > 0) {
                    puntajeEspecialidades.put(especialidad.getEspecialidad(), puntaje);
                    log.info("   {} - Score: {} | Matches: {}", especialidad.getEspecialidad(), puntaje, coincidencias);
                } else {
                    log.info("   {} - Score: 0 | No matches found", especialidad.getEspecialidad());
                }
            }

            // Seleccionar la especialidad con mayor puntaje
            String mejorEspecialidad = puntajeEspecialidades.entrySet().stream()
                    .max(java.util.Map.Entry.comparingByValue())
                    .map(java.util.Map.Entry::getKey)
                    .orElse("Medicina General");

            // Si no hay puntajes o todos son 0, devolver Medicina General
            if (puntajeEspecialidades.isEmpty()) {
                log.info("No specialty scores found, defaulting to Medicina General");
                mejorEspecialidad = "Medicina General";
            }

            log.info("FINAL RESULT - Selected specialty: {} | All scores: {}", mejorEspecialidad, puntajeEspecialidades);
            
            // Nunca devolver null
            return mejorEspecialidad != null ? mejorEspecialidad : "Medicina General";

        } catch (Exception e) {
            System.err.println("Error detectando especialidad: " + e.getMessage());
            return "Medicina General";
        }
    }

    private java.util.List<String> extraerPalabrasEnfermedades(String message) {
        // Palabras médicas comunes en español
        String[] palabrasMedicas = {
                "dolor", "fiebre", "tos", "cansancio", "fatiga", "nauseas", "vomito", "vómito",
                "diarrea", "estreñimiento", "dolor de cabeza", "jaqueca", "migraña", "mareo", "vértigo",
                "dolor de pecho", "dolor torácico", "palpitaciones", "dificultad para respirar", "respirar",
                "dolor de abdomen", "dolor abdominal", "acidez", "indigestión", "gastritis", "úlcera",
                "sangrado", "sangrar", "herida", "corte", "fractura", "hueso", "esguince",
                "quemadura", "picazón", "erupción", "sarpullido", "alergia",
                "presión alta", "hipertensión", "presión baja", "diabetes", "azúcar", "glucosa",
                "infarto", "ataque al corazón", "derrame", "accidente", "trauma", "emergencia",
                "visión borrosa", "vista", "ojos", "oído", "oídos", "sordera", "audición",
                "dolor de garganta", "garganta", "amígdalas", "anginas", "faringitis",
                "dolor de espalda", "espalda", "columna", "lumbalgia", "ciática",
                "dolor de articulaciones", "articulaciones", "artritis", "reumatismo", "hinchazón",
                "orina", "orinar", "infección", "bacterias", "virus", "resfriado", "gripe",
                "depresión", "ansiedad", "estrés", "insomnio", "dormir", "cansado"
        };

        java.util.List<String> palabrasEncontradas = new java.util.ArrayList<>();
        String messageLower = message.toLowerCase();

        for (String palabra : palabrasMedicas) {
            // Usar coincidencia de palabra completa para evitar falsos positivos
            if (containsWord(messageLower, palabra)) {
                palabrasEncontradas.add(palabra);
            }
        }

        return palabrasEncontradas;
    }

    private boolean containsWord(String text, String word) {
        // Para frases compuestas, buscar coincidencia exacta
        if (word.contains(" ")) {
            return text.contains(word);
        }
        
        // Para palabras simples, buscar coincidencia de palabra completa
        String[] words = text.split("\\s+");
        for (String w : words) {
            if (w.equals(word)) {
                return true;
            }
        }
        return false;
    }

    private String getPriorityForSpecialty(String especialidad, List<EspecialidadSintomaDto> especialidades) {
        try {
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

    private String getTipoConsultaForSpecialty(String especialidad, List<EspecialidadSintomaDto> especialidades) {
        try {
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

    private static class DetectedInfo {
        final String specialty;
        final String priority;
        final String tipoConsulta;

        DetectedInfo(String specialty, String priority, String tipoConsulta) {
            this.specialty = specialty;
            this.priority = priority;
            this.tipoConsulta = tipoConsulta;
        }
    }

    private String buildIntelligentPrompt(String message, PatientDto paciente, PlanCoberturaDto plan, List<HospitalDto> hospitales) {
        StringBuilder prompt = new StringBuilder();

        // Prompt de triaje: preguntar cuando falte info; decidir cuando sea suficiente
        prompt.append("Eres un médico experto haciendo TRIAJE COMPLETO. Sé empático, profesional y metódico.\n");
        prompt.append("Tu trabajo es analizar completamente el mensaje del paciente y determinar:\n");
        prompt.append("1. Si necesita más información (preguntar) o si tienes datos suficientes para diagnosticar\n");
        prompt.append("2. Identificar la especialidad médica adecuada según los síntomas\n");
        prompt.append("3. Determinar la criticidad/prioridad del caso\n");
        prompt.append("4. Diagnosticar enfermedades específicas (NUNCA especialidades)\n\n");

        prompt.append("REGLA CLAVE: si faltan datos mínimos (duración, intensidad, síntomas asociados, banderas rojas), NO diagnostiques; haz preguntas concretas.\n");
        prompt.append("Si ya hay suficiente información, entonces sí: entrega top 3 enfermedades probables, la más probable, especialidad, prioridad y tipoConsulta.\n\n");

        prompt.append("CONTEXTO DEL PACIENTE:\n");
        prompt.append("Plan: ").append(paciente.getPlan()).append("\n");
        prompt.append("Cobertura: Consulta ").append(plan.getCoberturaConsulta()).append("%, Emergencia ").append(plan.getCoberturaEmergencia()).append("%\n\n");

        prompt.append("CONVERSACIÓN ACTUAL:\n");
        prompt.append("Paciente dice: \"").append(message).append("\"\n\n");

        prompt.append("ANÁLISIS CLÍNICO REQUERIDO:\n");
        prompt.append("Basado en el mensaje del paciente, debes:\n");
        prompt.append("- Analizar todos los síntomas descritos\n");
        prompt.append("- Identificar patrones y combinaciones de síntomas\n");
        prompt.append("- Determinar la especialidad médica más apropiada\n");
        prompt.append("- Evaluar la criticidad (Baja/Media/Alta/Crítica)\n");
        prompt.append("- Diagnosticar enfermedades específicas\n\n");

        prompt.append("ESPECIALIDADES DISPONIBLES Y CUÁNDO USARLAS:\n");
        try {
            java.util.List<EspecialidadSintomaDto> especialidades = notionClient.getEspecialidades().block();
            if (especialidades != null && !especialidades.isEmpty()) {
                for (EspecialidadSintomaDto esp : especialidades) {
                    prompt.append("- ").append(esp.getEspecialidad()).append(": ").append(String.join(", ", esp.getKeywords())).append("\n");
                }
            }
        } catch (Exception e) {
            prompt.append("- Medicina General: fiebre, tos, malestar general, fatiga, nauseas, vomitos, diarrea, resfriado, gripe\n");
            prompt.append("- Neurología: dolor de cabeza, migraña, jaqueca, mareo, vértigo, dificultad para hablar, debilidad, rigidez de cuello, sensibilidad a la luz, vision borrosa\n");
            prompt.append("- Cardiología: dolor pecho, palpitaciones, presión alta, infarto\n");
        }
        prompt.append("\n");

        // Top 3 hospitales
        List<HospitalDto> mejoresHospitales = encontrarMejoresHospitales(hospitales, paciente.getPlan(), "Emergencia");
        prompt.append("MEJORES HOSPITALES (Costo, Servicio, Cobertura):\n");
        for (int i = 0; i < Math.min(3, mejoresHospitales.size()); i++) {
            HospitalDto hospital = mejoresHospitales.get(i);
            double precio = hospital.getPrecioEmergencia();
            double copago = precio * (1 - plan.getCoberturaEmergencia()/100.0);
            copago = Math.round(copago * 100.0) / 100.0; // Redondear a 2 decimales
            prompt.append(i+1).append(". ").append(hospital.getNombre());
            prompt.append(" - Precio: $").append(precio);
            prompt.append(", Copago: $").append(String.format("%.2f", copago));
            prompt.append(", Calificación: ").append(hospital.getCalificacion()).append("\n");
        }

        prompt.append("INSTRUCCIONES MÉDICAS DETALLADAS:\n");
        prompt.append("1. Si el mensaje es vago o incompleto, responde con necesitaMasInfo=true y 2-4 preguntas concretas en 'pregunta'.\n");
        prompt.append("2. Considera suficiente info cuando tengas: duración, intensidad, localización (si aplica), síntomas asociados y descarte básico de banderas rojas.\n");
        prompt.append("3. Si hay banderas rojas (déficit neurológico focal, rigidez de cuello, disnea intensa, dolor torácico opresivo), prioridad=Crítica y tipoConsulta=Emergencia.\n");
        prompt.append("4. CRÍTICO: 'enfermedadDetectada' y 'enfermedadesProbables' deben ser ENFERMEDADES reales (Migraña, Hipertensión, Neumonía, etc.), NUNCA especialidades médicas.\n");
        prompt.append("5. 'especialidad' debe ser la especialidad médica que trata esas enfermedades (Neurología para migrañas, Cardiología para hipertensión, etc.).\n");
        prompt.append("6. Analiza la criticidad basada en: intensidad del dolor, síntomas de alarma, evolución rápida, signos vitales comprometidos.\n");
        prompt.append("7. No recomiendes hospitales; enfócate en triaje clínico completo.\n\n");

        prompt.append("EJEMPLOS DE ANÁLISIS COMPLETO:\n");
        prompt.append("- Paciente: 'me duele la cabeza' → Doctor: '¿Desde cuándo? Es agudo o sordo? Tienes náuseas o sensibilidad a la luz?' (necesitaMasInfo: true)\n");
        prompt.append("- Paciente: 'dolor pecho opresivo que se irradia a brazo izquierdo con sudoración' → Doctor: especialidad='Cardiología', prioridad='Crítica', enfermedadDetectada='Infarto agudo de miocardio' (necesitaMasInfo: false)\n");
        prompt.append("- Paciente: 'fiebre y tos con flema desde hace 3 días, dolor torácico leve' → Doctor: especialidad='Medicina General', prioridad='Media', enfermedadDetectada='Bronquitis aguda' (necesitaMasInfo: false)\n");
        prompt.append("- Paciente: 'dolor cabeza pulsátil con náuseas y fotofobia desde hace 2 horas' → Doctor: especialidad='Neurología', prioridad='Media', enfermedadDetectada='Migraña' (necesitaMasInfo: false)\n\n");

        prompt.append("TIPOS DE RESPUESTA:\n");
        prompt.append("- Si necesitas más información: SIEMPRE usa necesitaMasInfo: true\n");
        prompt.append("- Si tienes suficiente información: usa necesitaMasInfo: false\n\n");

        prompt.append("Responde en formato JSON con tu ANÁLISIS COMPLETO:\n");
        prompt.append("{\n");
        prompt.append("  \"necesitaMasInfo\": true/false,\n");
        prompt.append("  \"pregunta\": \"si necesitaMasInfo=true: 2-4 preguntas concretas en un solo texto (con saltos de línea)\",\n");
        prompt.append("  \"especialidad\": \"especialidad médica (Medicina General, Neurología, Cardiología, etc.)\",\n");
        prompt.append("  \"prioridad\": \"Baja/Media/Alta/Crítica\",\n");
        prompt.append("  \"tipoConsulta\": \"General/Especialista/Emergencia\",\n");
        prompt.append("  \"enfermedadesProbables\": [\"enfermedad1\", \"enfermedad2\", \"enfermedad3\"],\n");
        prompt.append("  \"enfermedadDetectada\": \"diagnóstico más probable (ENFERMEDAD, no especialidad)\",\n");
        prompt.append("  \"sintomasClave\": \"síntomas principales identificados\",\n");
        prompt.append("  \"analisisCriticidad\": \"explicación de por qué esta criticidad\",\n");
        prompt.append("  \"recomendacionesMedicas\": \"consejo médico personalizado\"\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    private boolean shouldAskClarifyingQuestions(String message) {
        if (message == null) return true;
        String m = message.trim().toLowerCase(Locale.ROOT);
        if (m.isEmpty()) return true;

        log.info("🔍 AI_FLOW [{}] Checking if message needs clarification: '{}'", UUID.randomUUID().toString().substring(0, 8), m);

        // Muy corto = casi seguro insuficiente
        int len = m.length();
        if (len < 15) {
            log.info("📏 Message too short ({} chars), needs clarification", len);
            return true;
        }

        // Muy pocas palabras = probablemente insuficiente
        String[] words = m.split("\\s+");
        if (words.length < 4) {
            log.info("📝 Too few words ({}), needs clarification", words.length);
            return true;
        }

        // Señales de contexto clínico detallado
        boolean hasDuration = m.contains("desde ") || m.contains("hace ") || m.contains("ayer") || m.contains("hoy") || 
                             m.contains("días") || m.contains("dias") || m.contains("semanas") || m.contains("meses") ||
                             m.matches(".*\\d+\\s*(día|dias|semana|semanas|mes|meses).*");
        
        boolean hasIntensity = m.matches(".*\\b(\\d{1,2})\\s*/\\s*10\\b.*") || m.contains("leve") || m.contains("moderad") || 
                              m.contains("fuerte") || m.contains("intenso") || m.contains("severo") || m.contains("molesto");
        
        boolean hasAssociated = m.contains(" con ") || m.contains(" y ") || m.contains(" además") || m.contains("ademas") ||
                               m.contains(" también") || m.contains("tambien") || m.contains(" además de");
        
        boolean hasLocation = m.contains("cabeza") || m.contains("pecho") || m.contains("abdomen") || m.contains("estómago") || 
                             m.contains("estomago") || m.contains("garganta") || m.contains("espalda") || m.contains("brazo") ||
                             m.contains("pierna") || m.contains("ojo") || m.contains("oido");
        
        boolean hasQuality = m.contains("punzante") || m.contains("opresivo") || m.contains("ardiente") || m.contains("sordo") ||
                            m.contains("agudo") || m.contains("continuo") || m.contains("intermitente");
        
        boolean hasFrequency = m.contains("siempre") || m.contains("a veces") || m.contains("ocasionalmente") ||
                              m.contains("frecuentemente") || m.contains("rara vez");

        // Evaluar señales
        int signals = 0;
        if (hasDuration) signals++;
        if (hasIntensity) signals++;
        if (hasAssociated) signals++;
        if (hasLocation) signals++;
        if (hasQuality) signals++;
        if (hasFrequency) signals++;

        log.info("📊 Clinical signals - Duration: {}, Intensity: {}, Associated: {}, Location: {}, Quality: {}, Frequency: {} (Total: {})", 
            hasDuration, hasIntensity, hasAssociated, hasLocation, hasQuality, hasFrequency, signals);

        // Caso típico: "tengo dolor de cabeza" -> 2 señales (dolor+location) pero sigue incompleto
        boolean looksLikeSingleSymptom = !m.contains(".") && !m.contains(",") && len < 45;
        if (signals <= 1) return true;
        return looksLikeSingleSymptom && !hasDuration;
    }

    private String buildClarifyingQuestions(String message) {
        String m = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        List<String> qs = new ArrayList<>();

        // Preguntas base (sirven para casi cualquier síntoma)
        qs.add("¿Desde cuándo empezaron los síntomas y cómo han evolucionado (mejorando/empeorando)?");
        qs.add("¿Qué intensidad tiene (1-10) y qué lo empeora o mejora?");

        // Preguntas específicas según el síntoma mencionado
        if (m.contains("cabeza") || m.contains("migra") || m.contains("jaquec")) {
            qs.add("En el dolor de cabeza: ¿dónde duele (frente, sienes, detrás de los ojos) y es pulsátil o constante?");
            qs.add("¿Tienes náuseas/vómitos, sensibilidad a la luz/ruido, fiebre, rigidez de cuello o debilidad/alteración del habla?");
        } else if (m.contains("pecho") || m.contains("torác") || m.contains("torac")) {
            qs.add("En el dolor de pecho: ¿es opresivo o punzante? ¿se irradia a brazo/mandíbula/espalda?");
            qs.add("¿Hay falta de aire, sudor frío, mareo/desmayo o palpitaciones?");
        } else if (m.contains("abdomen") || m.contains("estóm") || m.contains("estom") || m.contains("panza")) {
            qs.add("En el dolor abdominal: ¿dónde exactamente duele y es constante o por cólicos?");
            qs.add("¿Hay vómitos, diarrea, fiebre, sangre en heces/orina o dolor al orinar?");
        } else {
            qs.add("¿Qué otros síntomas tienes además de esto (fiebre, tos, falta de aire, mareo, vómitos, diarrea, etc.)?");
        }

        // Devolver 3-4 preguntas máximo
        StringBuilder out = new StringBuilder("Para orientarte bien necesito estos datos:\n");
        for (int i = 0; i < Math.min(4, qs.size()); i++) {
            out.append("- ").append(qs.get(i)).append("\n");
        }
        return out.toString().trim();
    }

    private IntelligentAnalysisResult parseIntelligentResponse(String aiResponse, String originalMessage, PatientDto paciente, PlanCoberturaDto plan, List<HospitalDto> hospitales, String aiTraceId) {
        try {
            log.info("AI_FLOW [{}] parsing AI response length={}", aiTraceId, aiResponse == null ? 0 : aiResponse.length());

            // Parsear respuesta JSON de OpenAI
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode jsonNode = mapper.readTree(aiResponse);

            // Log de valores clave parseados
            String especialidad = jsonNode.has("especialidad") ? jsonNode.get("especialidad").asText() : "NO_DETECTED";
            String enfermedadDetectada = jsonNode.has("enfermedadDetectada") ? jsonNode.get("enfermedadDetectada").asText() : "NO_DETECTED";
            String prioridad = jsonNode.has("prioridad") ? jsonNode.get("prioridad").asText() : "NO_DETECTED";
            String analisisCriticidad = jsonNode.has("analisisCriticidad") ? jsonNode.get("analisisCriticidad").asText() : "NO_ANALYSIS";
            String sintomasClave = jsonNode.has("sintomasClave") ? jsonNode.get("sintomasClave").asText() : "NO_SYMPTOMS";
            
            log.info("AI_FLOW [{}] IA ANÁLISIS COMPLETO:", aiTraceId);
            log.info("AI_FLOW [{}] - Especialidad detectada: {}", aiTraceId, especialidad);
            log.info("AI_FLOW [{}] - Enfermedad diagnosticada: {}", aiTraceId, enfermedadDetectada);
            log.info("AI_FLOW [{}] - Prioridad asignada: {}", aiTraceId, prioridad);
            log.info("AI_FLOW [{}] - Análisis de criticidad: {}", aiTraceId, analisisCriticidad);
            log.info("AI_FLOW [{}] - Síntomas clave identificados: {}", aiTraceId, sintomasClave);

            // Verificar si la IA necesita más información
            boolean necesitaMasInfo = jsonNode.has("necesitaMasInfo") && jsonNode.get("necesitaMasInfo").asBoolean(false);

            if (necesitaMasInfo) {
                // IA necesita hacer más preguntas diagnósticas
                String pregunta = jsonNode.has("pregunta") ? jsonNode.get("pregunta").asText("¿Puedes describir mejor tus síntomas?") : "¿Puedes describir mejor tus síntomas?";

                return IntelligentAnalysisResult.builder()
                        .especialidad(null)
                        .prioridad(null)
                        .tipoConsulta(null)
                        .hospitalRecomendado(null)
                        .razonRecomendacion(pregunta)
                        .precioEstimado(0.0)
                        .copagoEstimado(0.0)
                        .coberturaAplicada(0.0)
                        .recomendacionesAdicionales("NECESITA_MAS_INFO")
                        .enfermedadesProbables(List.of())
                        .enfermedadMasProbable(null)
                        .resumenTriage("IA solicitó más información")
                        .aiTraceId(aiTraceId)
                        .build();
            } else {
                // IA tiene suficiente información para diagnóstico
                especialidad = jsonNode.has("especialidad") ? jsonNode.get("especialidad").asText("Medicina General") : "Medicina General";
                prioridad = jsonNode.has("prioridad") ? jsonNode.get("prioridad").asText("Media") : "Media";
                String tipoConsulta = jsonNode.has("tipoConsulta") ? jsonNode.get("tipoConsulta").asText("General") : "General";
                enfermedadDetectada = jsonNode.has("enfermedadDetectada") ? jsonNode.get("enfermedadDetectada").asText("Síntoma general") : "Síntoma general";
                sintomasClave = jsonNode.has("sintomasClave") ? jsonNode.get("sintomasClave").asText("Síntomas identificados") : "Síntomas identificados";
                analisisCriticidad = jsonNode.has("analisisCriticidad") ? jsonNode.get("analisisCriticidad").asText("Análisis no proporcionado") : "Análisis no proporcionado";
                String recomendacionesMedicas = jsonNode.has("recomendacionesMedicas") ? jsonNode.get("recomendacionesMedicas").asText("") : "";
                List<String> enfermedadesProbables = extractProbableDiseases(jsonNode, enfermedadDetectada, originalMessage);

                // Validación crítica: especialidad vs enfermedad
                if (especialidad.equals(enfermedadDetectada)) {
                    log.warn("AI_FLOW [{}] ❌ ERROR CRÍTICO: IA devolvió mismo valor para especialidad y enfermedad: {} - ¡Esto es incorrecto!", aiTraceId, especialidad);
                } else {
                    log.info("AI_FLOW [{}] ✅ CORRECTO: IA diferenció correctamente especialidad={} de enfermedad={}", aiTraceId, especialidad, enfermedadDetectada);
                }

                // Validación del análisis de criticidad
                if (analisisCriticidad.equals("Análisis no proporcionado")) {
                    log.warn("AI_FLOW [{}] ⚠️ ADVERTENCIA: IA no proporcionó análisis de criticidad", aiTraceId);
                } else {
                    log.info("AI_FLOW [{}] 📊 Análisis de criticidad proporcionado por IA: {}", aiTraceId, analisisCriticidad);
                }

                if (hasRedFlagSymptoms(originalMessage)) {
                    prioridad = "Crítica";
                    tipoConsulta = "Emergencia";
                }

                // Lógica de negocio: encontrar el mejor hospital para esta especialidad
                HospitalDto mejorHospital = encontrarMejorHospitalParaEspecialidad(hospitales, especialidad, tipoConsulta, plan, paciente);

                // Calcular cobertura y copago según el plan
                double coberturaAplicada = getCoberturaPorTipoConsulta(plan, tipoConsulta);
                double precioBase = getPrecioPorTipoConsulta(mejorHospital, tipoConsulta);
                double copagoEstimado = precioBase * (1 - coberturaAplicada/100.0);
                copagoEstimado = Math.round(copagoEstimado * 100.0) / 100.0; // Redondear a 2 decimales

                log.info("AI_FLOW [{}] 🏥 DIAGNÓSTICO FINAL DE IA:", aiTraceId);
                log.info("AI_FLOW [{}] - Especialidad: {} | Prioridad: {} | Tipo: {}", aiTraceId, especialidad, prioridad, tipoConsulta);
                log.info("AI_FLOW [{}] - Enfermedad principal: {}", aiTraceId, enfermedadDetectada);
                log.info("AI_FLOW [{}] - Enfermedades probables: {}", aiTraceId, enfermedadesProbables);
                log.info("AI_FLOW [{}] - Hospital recomendado: {} | Copago: ${}", aiTraceId, mejorHospital.getNombre(), copagoEstimado);
                log.info("AI_FLOW [{}] 🎯 La IA completó el análisis clínico completo del paciente", aiTraceId);

                return IntelligentAnalysisResult.builder()
                        .especialidad(especialidad)
                        .prioridad(prioridad)
                        .tipoConsulta(tipoConsulta)
                        .hospitalRecomendado(mejorHospital.getNombre())
                        .razonRecomendacion("Basado en tu diagnóstico de " + enfermedadDetectada + ", te recomiendo " + mejorHospital.getNombre() + " por su cobertura y costo con tu plan " + paciente.getPlan())
                        .precioEstimado(precioBase)
                        .copagoEstimado(copagoEstimado)
                        .coberturaAplicada(coberturaAplicada)
                        .recomendacionesAdicionales(recomendacionesMedicas)
                        .enfermedadesProbables(enfermedadesProbables)
                        .enfermedadMasProbable(enfermedadDetectada)
                        .resumenTriage(sintomasClave)
                        .aiTraceId(aiTraceId)
                        .build();
            }
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("AI_FLOW [{}] parse failure, fallback analysis: {}", aiTraceId, e.getMessage());
            return createFallbackAnalysis("", paciente, plan, hospitales, aiTraceId);
        }
    }

    private IntelligentAnalysisResult createFallbackAnalysis(String message, PatientDto paciente, PlanCoberturaDto plan, List<HospitalDto> hospitales, String aiTraceId) {
        // Análisis simple como fallback - usar cobertura real del plan
        double coberturaReal = plan.getCoberturaConsulta();
        String patientPlan = paciente == null ? "N/D" : paciente.getPlan();
        String resumen = (message == null || message.isBlank())
            ? "Fallback por error de análisis de IA"
            : "Fallback por error de análisis de IA para síntoma reportado";

        log.warn("AI_FLOW [{}] using deterministic fallback with coverage={} plan={}", aiTraceId, coberturaReal, patientPlan);

        // Calcular copago real antes del builder
        double copago = 50.0 * (1 - coberturaReal/100); // Calcular copago real
        copago = Math.round(copago * 100.0) / 100.0; // Redondear a 2 decimales
        
        return IntelligentAnalysisResult.builder()
                .especialidad("Medicina General")
                .prioridad("Media")
                .tipoConsulta("General")
                .hospitalRecomendado(hospitales.isEmpty() ? "Hospital Vozandes" : hospitales.get(0).getNombre())
                .razonRecomendacion("Análisis basado en disponibilidad y cobertura del plan")
                .precioEstimado(50.0)
                .copagoEstimado(copago)
                .coberturaAplicada(coberturaReal)
                .recomendacionesAdicionales("Consultar con médico para evaluación detallada")
                .enfermedadesProbables(List.of("Síndrome viral", "Migraña", "Cefalea tensional"))
                .enfermedadMasProbable("Síndrome viral")
                .resumenTriage(resumen)
                .aiTraceId(aiTraceId)
                .build();
    }

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

    private IntelligentAnalysisResult createAnalysisWithDetectedSpecialty(String especialidad, String prioridad, String tipoConsulta, PatientDto paciente, PlanCoberturaDto plan, List<HospitalDto> hospitales, String aiTraceId) {
        // Usar especialidad detectada por Notion con cobertura real del plan
        double cobertura = getCoberturaPorTipoConsulta(plan, tipoConsulta);
        // Precio referencial por defecto basado en tipo de consulta
        double precioReferencial = getPrecioReferencialPorTipoConsulta(tipoConsulta);
        double copago = precioReferencial * (1 - cobertura/100);
        copago = Math.round(copago * 100.0) / 100.0; // Redondear a 2 decimales

        String patientPlan = paciente == null ? "N/D" : paciente.getPlan();
        log.info("AI_FLOW [{}] fallback by detected specialty={} priority={} cobertura={} plan={}", aiTraceId, especialidad, prioridad, cobertura, patientPlan);

        // Generar enfermedades probables basadas en la especialidad detectada
        List<String> enfermedadesProbables = deriveDiseasesForSpecialty(especialidad);
        String enfermedadMasProbable = enfermedadesProbables.isEmpty() ? "Síntoma general" : enfermedadesProbables.get(0);

        return IntelligentAnalysisResult.builder()
                .especialidad(especialidad)
                .prioridad(prioridad)
                .tipoConsulta(tipoConsulta)
                .hospitalRecomendado(hospitales.isEmpty() ? "Hospital Vozandes" : hospitales.get(0).getNombre())
                .razonRecomendacion("Recomendación basada en especialidad detectada y cobertura del plan")
                .precioEstimado(precioReferencial)
                .copagoEstimado(copago)
                .coberturaAplicada(cobertura)
                .recomendacionesAdicionales("Buscar atención médica según prioridad detectada")
                .enfermedadesProbables(enfermedadesProbables)
                .enfermedadMasProbable(enfermedadMasProbable)
                .resumenTriage("Clasificación por fallback de reglas para plan " + patientPlan)
                .aiTraceId(aiTraceId)
                .build();
    }

    private boolean hasRedFlagSymptoms(String message) {
        if (message == null) {
            return false;
        }
        String m = message.toLowerCase(Locale.ROOT);
        boolean neuroDeficit = m.contains("dificultad para hablar") || m.contains("debilidad") || m.contains("brazo izquierdo") || m.contains("alteración del habla") || m.contains("vision borrosa") || m.contains("visión borrosa");
        boolean meningeal = m.contains("rigidez de cuello") || m.contains("cuello rígido") || m.contains("cuello rigido");
        boolean severeHeadache = (m.contains("dolor de cabeza") || m.contains("jaqueca") || m.contains("migraña"))
                && (m.contains("9/10") || m.contains("10/10") || m.contains("muy fuerte") || m.contains("empeorando"));
        boolean cardioResp = (m.contains("dolor pecho") || m.contains("dolor de pecho") || m.contains("disnea") || m.contains("falta de aire"))
                && (m.contains("sudor") || m.contains("mareo") || m.contains("desmayo"));

        return (neuroDeficit && severeHeadache) || (meningeal && severeHeadache) || cardioResp;
    }

    private IntelligentAnalysisResult createEmergencyAnalysisFromRedFlags(String message, PatientDto paciente, PlanCoberturaDto plan, List<HospitalDto> hospitales, String aiTraceId) {
        String tipoConsulta = "Emergencia";
        double cobertura = getCoberturaPorTipoConsulta(plan, tipoConsulta);
        HospitalDto hospital = hospitales == null || hospitales.isEmpty() ? null : hospitales.get(0);
        String hospitalNombre = hospital == null ? "Hospital de Emergencia más cercano" : hospital.getNombre();
        double precio = hospital == null ? 120.0 : getPrecioPorTipoConsulta(hospital, tipoConsulta);
        double copago = precio * (1 - cobertura / 100.0);
        copago = Math.round(copago * 100.0) / 100.0; // Redondear a 2 decimales

        List<String> candidatos = List.of(
                "Accidente cerebrovascular (ACV)",
                "Meningitis",
                "Migraña complicada"
        );

        String patientLabel = paciente == null ? "paciente" : (paciente.getNombre() == null || paciente.getNombre().isBlank() ? "paciente" : paciente.getNombre());
        String resumen = (message == null || message.isBlank())
            ? "Banderas rojas neurológicas detectadas"
            : "Banderas rojas neurológicas detectadas en mensaje clínico";

        return IntelligentAnalysisResult.builder()
                .especialidad("Neurología")
                .prioridad("Crítica")
                .tipoConsulta(tipoConsulta)
                .hospitalRecomendado(hospitalNombre)
                .razonRecomendacion("Síntomas de alarma detectados: requiere evaluación de emergencia inmediata")
                .precioEstimado(precio)
                .copagoEstimado(copago)
                .coberturaAplicada(cobertura)
                .recomendacionesAdicionales(patientLabel + ": acude de inmediato a emergencias o llama a servicios médicos de urgencia.")
                .enfermedadesProbables(candidatos)
                .enfermedadMasProbable(candidatos.get(0))
                .resumenTriage(resumen)
                .aiTraceId(aiTraceId)
                .build();
    }

    private List<String> extractProbableDiseases(com.fasterxml.jackson.databind.JsonNode jsonNode, String enfermedadDetectada, String originalMessage) {
        List<String> candidates = new ArrayList<>();

        com.fasterxml.jackson.databind.JsonNode diseasesNode = jsonNode.get("enfermedadesProbables");
        if (diseasesNode != null && diseasesNode.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode node : diseasesNode) {
                if (node != null && node.isTextual() && !node.asText().isBlank()) {
                    candidates.add(node.asText().trim());
                }
            }
        }

        if (enfermedadDetectada != null && !enfermedadDetectada.isBlank()) {
            candidates.add(enfermedadDetectada.trim());
        }

        if (candidates.isEmpty()) {
            candidates.addAll(deriveDefaultDiseaseCandidates(originalMessage));
        }

        return candidates.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .limit(3)
                .collect(Collectors.toList());
    }

    private List<String> deriveDefaultDiseaseCandidates(String message) {
        String m = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (m.contains("dolor de cabeza") || m.contains("migra") || m.contains("náuse") || m.contains("nause")) {
            return List.of("Migraña", "Cefalea tensional", "Sinusitis aguda");
        }
        if (m.contains("dolor pecho") || m.contains("dolor de pecho")) {
            return List.of("Angina", "Costocondritis", "Reflujo gastroesofágico");
        }
        if (m.contains("fiebre") || m.contains("tos")) {
            return List.of("Infección respiratoria alta", "Gripe", "Bronquitis");
        }
        return List.of("Síndrome viral", "Trastorno inespecífico", "Dolor de causa funcional");
    }

    private List<String> deriveDiseasesForSpecialty(String especialidad) {
        return switch (especialidad.toLowerCase()) {
            case "medicina general" -> List.of("Infección respiratoria alta", "Gripe común", "Síndrome viral");
            case "neurología" -> List.of("Migraña", "Cefalea tensional", "Neuralgia");
            case "cardiología" -> List.of("Hipertensión arterial", "Arritmia cardíaca", "Angina de pecho");
            case "cirugía" -> List.of("Apendicitis aguda", "Colecistitis", "Hernia abdominal");
            case "pediatría" -> List.of("Bronquiolitis", "Otitis media", "Gastroenteritis infantil");
            case "ginecología" -> List.of("Infección vaginal", "Síndrome premenstrual", "Endometriosis");
            case "oftalmología" -> List.of("Conjuntivitis", "Blefaritis", "Ojo seco");
            case "otorrinolaringología" -> List.of("Rinitis alérgica", "Sinusitis", "Amigdalitis");
            case "traumatología" -> List.of("Esguince de tobillo", "Fractura de muñeca", "Lumbalgia aguda");
            case "dermatología" -> List.of("Dermatitis atópica", "Acné vulgar", "Infección cutánea");
            default -> List.of("Síntoma general", "Trastorno inespecífico", "Dolor de causa funcional");
        };
    }

    private double getPrecioReferencialPorTipoConsulta(String tipoConsulta) {
        return switch (tipoConsulta) {
            case "General" -> 50.0;
            case "Especialista" -> 85.0;
            case "Emergencia" -> 120.0;
            case "Exámenes", "Examenes" -> 75.0;
            case "Cirugía", "Cirugia" -> 200.0;
            case "Hospitalización", "Hospitalizacion" -> 500.0;
            default -> 50.0;
        };
    }

    private List<HospitalDto> encontrarMejoresHospitales(List<HospitalDto> hospitales, String plan, String tipoConsulta) {
        return hospitales.stream()
                // 1) Filtrar hospitales que acepten el plan
                .filter(hospital -> hospital.getPlanesAceptados().stream()
                        .anyMatch(p -> p.equalsIgnoreCase(plan)))
                // 2) Ordenar por: menor copago, mejor calificación, menor precio
                .sorted((h1, h2) -> {
                    double precio1 = getPrecioPorTipoConsulta(h1, tipoConsulta);
                    double precio2 = getPrecioPorTipoConsulta(h2, tipoConsulta);

                    // Calcular copago estimado (asumimos 90% cobertura general)
                    double copago1 = precio1 * 0.1;
                    double copago2 = precio2 * 0.1;
                    copago1 = Math.round(copago1 * 100.0) / 100.0; // Redondear a 2 decimales
                    copago2 = Math.round(copago2 * 100.0) / 100.0; // Redondear a 2 decimales

                    // Prioridad 1: Menor copago
                    if (copago1 != copago2) {
                        return Double.compare(copago1, copago2);
                    }

                    // Prioridad 2: Mejor calificación
                    if (h1.getCalificacion() != h2.getCalificacion()) {
                        return Double.compare(h2.getCalificacion(), h1.getCalificacion());
                    }

                    // Prioridad 3: Menor precio base
                    return Double.compare(precio1, precio2);
                })
                .limit(3)
                .collect(java.util.stream.Collectors.toList());
    }

    private double getPrecioPorTipoConsulta(HospitalDto hospital, String tipoConsulta) {
        return switch (tipoConsulta) {
            case "General" -> hospital.getPrecioConsultaGeneral();
            case "Especialista" -> hospital.getPrecioConsultaEspecialista();
            case "Emergencia" -> hospital.getPrecioEmergencia();
            default -> hospital.getPrecioConsultaGeneral();
        };
    }

    /**
     * Lógica de negocio para encontrar el mejor hospital para una especialidad específica
     * Considera: cobertura del plan, costo, calificación, distancia
     */
    private HospitalDto encontrarMejorHospitalParaEspecialidad(List<HospitalDto> hospitales, String especialidad, String tipoConsulta, PlanCoberturaDto plan, PatientDto paciente) {
        return hospitales.stream()
                // 1) Filtrar hospitales que acepten el plan y tengan la especialidad
                .filter(hospital ->
                        hospital.getPlanesAceptados().stream().anyMatch(p -> p.equalsIgnoreCase(paciente.getPlan())) &&
                                hospital.getEspecialidades().stream().anyMatch(e -> e.equalsIgnoreCase(especialidad))
                )
                // 2) Ordenar por: menor copago, mejor calificación, menor precio
                .sorted((h1, h2) -> {
                    double precio1 = getPrecioPorTipoConsulta(h1, tipoConsulta);
                    double precio2 = getPrecioPorTipoConsulta(h2, tipoConsulta);

                    // Calcular copago según cobertura del plan
                    double cobertura = getCoberturaPorTipoConsulta(plan, tipoConsulta);
                    double copago1 = precio1 * (1 - cobertura/100.0);
                    double copago2 = precio2 * (1 - cobertura/100.0);
                    copago1 = Math.round(copago1 * 100.0) / 100.0; // Redondear a 2 decimales
                    copago2 = Math.round(copago2 * 100.0) / 100.0; // Redondear a 2 decimales

                    // Prioridad 1: Menor copago para el paciente
                    if (Math.abs(copago1 - copago2) > 0.01) {
                        return Double.compare(copago1, copago2);
                    }

                    // Prioridad 2: Mejor calificación del hospital
                    if (Math.abs(h1.getCalificacion() - h2.getCalificacion()) > 0.1) {
                        return Double.compare(h2.getCalificacion(), h1.getCalificacion());
                    }

                    // Prioridad 3: Menor precio base
                    return Double.compare(precio1, precio2);
                })
                .findFirst()
                // 3) Si no hay hospital perfecto, tomar el mejor disponible
                .orElseGet(() -> {
                    // Fallback: primer hospital que acepte el plan
                    return hospitales.stream()
                            .filter(h -> h.getPlanesAceptados().stream().anyMatch(p -> p.equalsIgnoreCase(paciente.getPlan())))
                            .findFirst()
                            .orElse(hospitales.get(0)); // Último recurso: cualquier hospital
                });
    }
}
