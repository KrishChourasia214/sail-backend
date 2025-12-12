package com.sail.service;

import com.sail.dto.DeployResult;
import org.springframework.stereotype.Service;

@Service
public class DeployService {

    private final StaticDeployService staticDeployService;
    private final SpringDeployService springDeployService;

    public DeployService(StaticDeployService staticDeployService,
                         SpringDeployService springDeployService) {
        this.staticDeployService = staticDeployService;
        this.springDeployService = springDeployService;
    }

    public DeployResult deploy(String projectId, String deploymentType) {
        if ("STATIC".equals(deploymentType)) {
            return staticDeployService.deployStatic(projectId);
        } else if ("SPRINGBOOT".equals(deploymentType)) {
            return springDeployService.deploySpringBoot(projectId);
        } else {
            DeployResult result = new DeployResult();
            result.setStatus("FAILED");
            result.setErrorMessage("Unknown deployment type: " + deploymentType);
            return result;
        }
    }
}

