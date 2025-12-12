package com.sail.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeployResult {
    private String deploymentType; // STATIC or SPRINGBOOT
    private String url;
    private String bucket; // For static
    private String lambdaName; // For Spring Boot
    private String apiUrl; // For Spring Boot
    private String region;
    private String status; // SUCCESS or FAILED
    private String errorMessage; // If failed
}

