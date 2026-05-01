package com.hyun.eventpipeline.consumer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.springframework.http.HttpMethod;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@ToString
public class ApiLog {
    private String userId;
    private String agent;
    private String targetId;
    private EventType eventType;
    private HttpMethod httpMethod;
    private String endpoint;
    private int statusCode;
    private int responseTimeMs;
    private String errorCode;
    private LocalDateTime callAt;
}
