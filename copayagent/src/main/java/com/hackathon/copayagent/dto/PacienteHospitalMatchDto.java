package com.hackathon.copayagent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PacienteHospitalMatchDto {
    private String hospitalId;
    private String nombreHospital;
    private String planPaciente;
    private String especialidad;
    private double precioConsulta;
    private double copagoCalculado;
    private boolean aceptado;
}
