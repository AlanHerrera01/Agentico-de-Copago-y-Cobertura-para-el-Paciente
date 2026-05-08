package com.hackathon.copayagent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Modelo de dominio para cobertura de póliza.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Coverage {
    @NotBlank(message = "El nombre de la cobertura es obligatorio")
    private String name;

    @NotNull(message = "El porcentaje de cobertura es obligatorio")
    @Positive(message = "El porcentaje debe ser positivo")
    private Double percentage;
}
