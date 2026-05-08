package com.hackathon.copayagent;

import com.hackathon.copayagent.config.GroqConfig;
import com.hackathon.copayagent.config.SSLTrustManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GroqConfig.class)
public class CopayagentApplication {

	public static void main(String[] args) {
		// Deshabilitar verificación SSL para desarrollo local
		SSLTrustManager.disableSSLVerification();
		SpringApplication.run(CopayagentApplication.class, args);
	}

}
