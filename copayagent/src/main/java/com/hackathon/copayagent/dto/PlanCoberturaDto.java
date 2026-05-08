package com.hackathon.copayagent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@lombok.Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanCoberturaDto {
    private String plan;
    private int coberturaConsulta;
    private int coberturaEmergencia;
    private int coberturaExamenes;
    private int coberturaCirugia;
    private int coberturaHospitalizacion;
    private double deducibleAnual;
    private double limiteAnualUSD;
    private List<String> especialidadesExcluidas;
    private List<String> requiereAutorizacion;
}
