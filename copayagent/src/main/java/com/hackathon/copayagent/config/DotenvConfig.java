package com.hackathon.copayagent.config;

import org.springframework.context.annotation.Configuration;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PostConstruct;

@Configuration
public class DotenvConfig {
    
    @PostConstruct
    public void configure() {
        try {
            // Cargar variables del archivo .env
            Dotenv dotenv = Dotenv.configure()
                    .directory(".")
                    .filename(".env")
                    .ignoreIfMissing()
                    .load();
            
            // Establecer variables de sistema para que Spring las lea
            dotenv.entries().forEach(entry -> {
                System.setProperty(entry.getKey(), entry.getValue());
                System.out.println("Cargando variable de entorno: " + entry.getKey());
            });
        } catch (Exception e) {
            System.err.println("Error cargando .env: " + e.getMessage());
        }
    }
}
