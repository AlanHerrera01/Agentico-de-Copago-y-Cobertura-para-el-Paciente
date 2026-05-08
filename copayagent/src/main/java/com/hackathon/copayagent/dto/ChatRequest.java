package com.hackathon.copayagent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    @NotBlank(message = "clientId is required")
    private String clientId;
    @NotBlank(message = "message is required")
    private String message;
}
