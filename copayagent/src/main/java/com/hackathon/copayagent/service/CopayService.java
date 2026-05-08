package com.hackathon.copayagent.service;

import org.springframework.stereotype.Service;

@Service
public class CopayService {
    /**
     * Calcula el copago estimado para el paciente considerando deducible y cobertura.
     * Si deducibleUsado < deducibleAnual, primero se descuenta el deducible restante.
     * Fórmula: copago = precioConsulta * (1 - (cobertura / 100))
     * @param precioConsulta Precio de la consulta (double)
     * @param cobertura Porcentaje de cobertura (ej: 90 para 90%)
     * @param deducibleAnual Deducible anual del paciente
     * @param deducibleUsado Deducible ya usado por el paciente
     * @return Copago estimado (double)
     */
    public double calcularCopago(double precioConsulta, double cobertura, double deducibleAnual, double deducibleUsado) {
        double deducibleRestante = Math.max(0, deducibleAnual - deducibleUsado);
        double montoCubierto = precioConsulta * (cobertura / 100.0);
        double copago;
        if (deducibleRestante > 0) {
            // El paciente debe cubrir el deducible restante primero
            if (precioConsulta <= deducibleRestante) {
                copago = precioConsulta; // Todo lo paga el paciente
            } else {
                // Paga el deducible y el resto se calcula con cobertura
                double restante = precioConsulta - deducibleRestante;
                double montoCubiertoRestante = restante * (cobertura / 100.0);
                copago = deducibleRestante + (restante - montoCubiertoRestante);
            }
        } else {
            // Solo aplica cobertura
            copago = precioConsulta - montoCubierto;
        }
        // Redondear a 2 decimales para evitar valores como 9.99999999
        return Math.round(copago * 100.0) / 100.0;
    }
}
