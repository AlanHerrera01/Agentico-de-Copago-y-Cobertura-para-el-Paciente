package com.hackathon.copayagent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiRequest {
    private String model;
    private List<Message> messages;
    @JsonProperty("max_tokens")
    private int maxTokens;
    private double temperature;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
    }
}
