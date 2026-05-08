package com.hackathon.copayagent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@lombok.Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientDto {
    private String clientId;
    private String nombre;
    private String cedula;
    private int edad;
    private String plan;
    private String poliza;
    private String fechaInicio;
    private String fechaFin;
    private double deducibleAnual;
    private double deducibleUsado;
    private String ciudad;
    private String estado;
}
