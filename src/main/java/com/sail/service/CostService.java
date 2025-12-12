package com.sail.service;

import com.sail.dto.CostResult;
import com.sail.model.ProjectInfo;
import com.sail.repository.ProjectInfoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class CostService {

    private final ProjectInfoRepository projectInfoRepository;
    private final double lambdaCostPerMillion;
    private final double apiGatewayCostPerMillion;
    private final double s3StoragePerGB;
    private final double s3TransferPerGB;

    public CostService(ProjectInfoRepository projectInfoRepository,
                       @Value("${cost.lambda.per.million.requests}") double lambdaCostPerMillion,
                       @Value("${cost.api.gateway.per.million.requests}") double apiGatewayCostPerMillion,
                       @Value("${cost.s3.storage.per.gb}") double s3StoragePerGB,
                       @Value("${cost.s3.transfer.per.gb}") double s3TransferPerGB) {
        this.projectInfoRepository = projectInfoRepository;
        this.lambdaCostPerMillion = lambdaCostPerMillion;
        this.apiGatewayCostPerMillion = apiGatewayCostPerMillion;
        this.s3StoragePerGB = s3StoragePerGB;
        this.s3TransferPerGB = s3TransferPerGB;
    }

    public CostResult calculateCost(String projectId) {
        ProjectInfo projectInfo = projectInfoRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        CostResult result = new CostResult();
        result.setProjectType(projectInfo.getProjectType());

        if ("STATIC".equals(projectInfo.getProjectType())) {
            calculateStaticCost(projectInfo, result);
        } else if ("SPRINGBOOT".equals(projectInfo.getProjectType())) {
            calculateSpringBootCost(result);
        }

        // Calculate total
        double total = (result.getLambdaCost() != null ? result.getLambdaCost() : 0) +
                      (result.getApiGatewayCost() != null ? result.getApiGatewayCost() : 0) +
                      (result.getS3Cost() != null ? result.getS3Cost() : 0);
        result.setTotal(total);

        return result;
    }

    private void calculateStaticCost(ProjectInfo projectInfo, CostResult result) {
        try {
            // Estimate storage size
            double sizeGB = calculateDirectorySize(projectInfo.getExtractedPath()) / 1024.0;
            
            // S3 storage cost (assuming 1GB storage)
            double storageCost = sizeGB * s3StoragePerGB;
            
            // S3 transfer cost (assuming 10GB transfer per month)
            double transferCost = 10.0 * s3TransferPerGB;
            
            result.setS3Cost(storageCost + transferCost);
            result.setLambdaCost(0.0);
            result.setApiGatewayCost(0.0);
        } catch (Exception e) {
            // Default estimates
            result.setS3Cost(0.25);
            result.setLambdaCost(0.0);
            result.setApiGatewayCost(0.0);
        }
    }

    private void calculateSpringBootCost(CostResult result) {
        // Estimate: 1 million requests per month
        double lambdaCost = lambdaCostPerMillion;
        double apiGatewayCost = apiGatewayCostPerMillion;
        
        result.setLambdaCost(lambdaCost);
        result.setApiGatewayCost(apiGatewayCost);
        result.setS3Cost(0.01); // Minimal S3 for logs
    }

    private double calculateDirectorySize(String directoryPath) throws IOException {
        Path path = Paths.get(directoryPath);
        return Files.walk(path)
                .filter(Files::isRegularFile)
                .mapToLong(p -> {
                    try {
                        return Files.size(p);
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .sum() / (1024.0 * 1024.0); // Convert to MB
    }
}

