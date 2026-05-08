package com.hackathon.copayagent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@lombok.Builder
@NoArgsConstructor
@AllArgsConstructor
public class HospitalDto {
    private String hospitalId;
    private String nombre;
    private String tipo;
    private String ciudad;
    private String direccion;
    private String telefono;
    private List<String> planesAceptados;
    private List<String> especialidades;
    private double precioConsultaGeneral;
    private double precioConsultaEspecialista;
    private double precioEmergencia;
    private double calificacion;
    private String tiempoEsperaPromedio;
}
