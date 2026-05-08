package com.hackathon.copayagent.client;

import com.hackathon.copayagent.dto.OpenAiRequest;
import com.hackathon.copayagent.dto.OpenAiResponse;
import reactor.core.publisher.Mono;

public interface AiClient {
    Mono<OpenAiResponse> getSpecialtyAndPriority(OpenAiRequest request);
}
