package com.hackathon.copayagent.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatResponse {
    private String specialty;
    private String coverage;
    private Double estimatedCopay;
    private String recommendedHospital;
    private String priority;
    private List<String> probableDiseases;
    private String selectedDisease;
    private String triageSummary;
    private String aiTraceId;
}
