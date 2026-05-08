package com.hackathon.copayagent.service;

import com.hackathon.copayagent.dto.HospitalDto;
import org.springframework.stereotype.Service;
import java.util.Comparator;
import java.util.List;

@Service
public class HospitalRecommendationService {
    /**
     * Recomienda el hospital con menor copago para el paciente, considerando plan y especialidad.
     * @param hospitales Lista de hospitales compatibles (ya filtrados por plan y especialidad)
     * @param tipoConsulta Tipo de consulta ("General", "Especialista", "Emergencia")
     * @param cobertura Porcentaje de cobertura del plan
     * @param deducibleAnual Deducible anual del paciente
     * @param deducibleUsado Deducible usado del paciente
     * @param copayService Servicio para calcular copago
     * @return HospitalDto recomendado (menor copago)
     */
    public HospitalDto recomendarHospitalMenorCopago(List<HospitalDto> hospitales, String tipoConsulta, double cobertura, double deducibleAnual, double deducibleUsado, CopayService copayService) {
        return hospitales.stream()
                .min(Comparator.comparingDouble(h -> calcularCopagoHospital(h, tipoConsulta, cobertura, deducibleAnual, deducibleUsado, copayService)))
                .orElse(null);
    }

    /**
     * Calcula el copago estimado para un hospital según el tipo de consulta.
     * Los tipos de consulta reales son: "General" y "Especialista"
     */
    public double calcularCopagoHospital(HospitalDto h, String tipoConsulta, double cobertura, double deducibleAnual, double deducibleUsado, CopayService copayService) {
        double precio;
        switch (tipoConsulta) {
            case "General" -> precio = h.getPrecioConsultaGeneral();
            case "Especialista" -> precio = h.getPrecioConsultaEspecialista();
            case "Emergencia" -> precio = h.getPrecioEmergencia();
            default -> precio = h.getPrecioConsultaGeneral();
        }
        return copayService.calcularCopago(precio, cobertura, deducibleAnual, deducibleUsado);
    }
}
