package com.sail.controller;

import com.sail.dto.DeployResult;
import com.sail.model.DeploymentHistory;
import com.sail.model.ProjectInfo;
import com.sail.repository.ProjectInfoRepository;
import com.sail.service.DeployService;
import com.sail.service.HistoryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deploy")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
public class DeployController {

    private final DeployService deployService;
    private final HistoryService historyService;
    private final ProjectInfoRepository projectInfoRepository;
    private final String region;

    public DeployController(DeployService deployService,
                           HistoryService historyService,
                           ProjectInfoRepository projectInfoRepository,
                           @Value("${aws.region}") String region) {
        this.deployService = deployService;
        this.historyService = historyService;
        this.projectInfoRepository = projectInfoRepository;
        this.region = region;
    }

    @PostMapping("/static/{projectId}")
    public ResponseEntity<DeployResult> deployStatic(@PathVariable String projectId) {
        DeployResult result = deployService.deploy(projectId, "STATIC");
        saveHistory(projectId, result, "STATIC");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/spring/{projectId}")
    public ResponseEntity<DeployResult> deploySpring(@PathVariable String projectId) {
        DeployResult result = deployService.deploy(projectId, "SPRINGBOOT");
        saveHistory(projectId, result, "SPRINGBOOT");
        return ResponseEntity.ok(result);
    }

    private void saveHistory(String projectId, DeployResult result, String deploymentType) {
        ProjectInfo projectInfo = projectInfoRepository.findById(projectId).orElse(null);
        DeploymentHistory history = new DeploymentHistory();
        history.setProjectId(projectId);
        history.setFileName(projectInfo != null ? projectInfo.getFileName() : "unknown");
        history.setDeploymentType(deploymentType);
        history.setUrl(result.getUrl());
        history.setBucket(result.getBucket());
        history.setLambdaName(result.getLambdaName());
        history.setApiUrl(result.getApiUrl());
        history.setRegion(region);
        history.setStatus(result.getStatus());
        history.setErrorMessage(result.getErrorMessage());
        historyService.saveDeploymentHistory(history);
    }
}

