package com.hackathon.copayagent.config;

import com.hackathon.copayagent.client.NotionClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/debug")
public class DebugController {

    @Autowired
    private NotionClient notionClient;

    @GetMapping("/notion-test")
    public ResponseEntity<String> testNotionConnection() {
        try {
            System.out.println("DEBUG - Iniciando test de Notion con 'dolor'");
            String result = notionClient.getEspecialidadPorSintoma("dolor")
                    .map(dto -> {
                        System.out.println("DEBUG - DTO recibido: " + dto);
                        return dto != null ? dto.toString() : "NULL";
                    })
                    .block();
            return ResponseEntity.ok("Notion test result: " + result);
        } catch (Exception e) {
            System.out.println("DEBUG - Error en test: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok("Notion test error: " + e.getMessage());
        }
    }

    @GetMapping("/test-patient")
    public ResponseEntity<String> testPatientConnection() {
        try {
            System.out.println("DEBUG - Iniciando test de paciente con 'PAC-005'");
            String result = notionClient.getPacienteByClientId("PAC-005")
                    .map(dto -> {
                        System.out.println("DEBUG - Paciente DTO: " + dto);
                        return dto != null ? dto.toString() : "NULL";
                    })
                    .block();
            return ResponseEntity.ok("Patient test result: " + result);
        } catch (Exception e) {
            System.out.println("DEBUG - Error en test paciente: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok("Patient test error: " + e.getMessage());
        }
    }
}
