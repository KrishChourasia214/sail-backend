package com.sail.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CostResult {
    private String projectType;
    private Double lambdaCost;
    private Double apiGatewayCost;
    private Double s3Cost;
    private Double total;
    private String currency = "USD";
    private String period = "monthly";
}

