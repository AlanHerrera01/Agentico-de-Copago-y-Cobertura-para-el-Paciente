package com.hackathon.copayagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyDto {
    @NotBlank(message = "El ID del cliente es obligatorio")
    private String clientId;

    @NotBlank(message = "El ID de la póliza es obligatorio")
    private String policyId;

    @NotBlank(message = "La cobertura es obligatoria")
    private String coverage;

    @NotNull(message = "El porcentaje de cobertura es obligatorio")
    @Positive(message = "El porcentaje de cobertura debe ser positivo")
    private Double coveragePercentage;

    @NotNull(message = "El costo de consulta es obligatorio")
    @Positive(message = "El costo de consulta debe ser positivo")
    private Double consultationCost;
}
