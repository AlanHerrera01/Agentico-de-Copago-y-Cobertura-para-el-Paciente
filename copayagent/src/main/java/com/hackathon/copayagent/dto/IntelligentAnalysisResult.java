package com.hackathon.copayagent.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntelligentAnalysisResult {
    private String especialidad;
    private String prioridad;
    private String tipoConsulta;
    private String hospitalRecomendado;
    private String razonRecomendacion;
    private Double precioEstimado;
    private Double copagoEstimado;
    private Double coberturaAplicada;
    private String recomendacionesAdicionales;
    private List<String> enfermedadesProbables;
    private String enfermedadMasProbable;
    private String resumenTriage;
    private String aiTraceId;
}
