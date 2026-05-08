package com.hackathon.copayagent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Modelo de dominio para hospital afiliado.
 * Incluye validaciones para asegurar integridad de datos.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Hospital {
    @NotBlank(message = "El ID del hospital es obligatorio")
    private String id;

    @NotBlank(message = "El nombre del hospital es obligatorio")
    private String name;

    @NotBlank(message = "La especialidad es obligatoria")
    private String specialty;

    @NotNull(message = "Debe indicar si está afiliado")
    private Boolean affiliated;

    @NotNull(message = "El costo de consulta es obligatorio")
    @Positive(message = "El costo de consulta debe ser positivo")
    private Double consultationCost;
}
