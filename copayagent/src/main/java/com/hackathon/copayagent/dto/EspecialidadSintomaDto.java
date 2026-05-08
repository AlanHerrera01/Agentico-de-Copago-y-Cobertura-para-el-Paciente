package com.hackathon.copayagent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@lombok.Builder
@NoArgsConstructor
@AllArgsConstructor
public class EspecialidadSintomaDto {
    private String especialidad;
    private String tipoConsulta;
    private List<String> keywords;
    private String prioridad;
    private double precioReferencialUSD;
}
