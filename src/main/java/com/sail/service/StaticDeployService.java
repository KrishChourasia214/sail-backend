package com.sail.service;

import com.sail.aws.S3Service;
import com.sail.dto.DeployResult;
import com.sail.model.ProjectInfo;
import com.sail.repository.ProjectInfoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StaticDeployService {

    private final S3Service s3Service;
    private final ProjectInfoRepository projectInfoRepository;
    private final String region;

    public StaticDeployService(S3Service s3Service,
                               ProjectInfoRepository projectInfoRepository,
                               @Value("${aws.region}") String region) {
        this.s3Service = s3Service;
        this.projectInfoRepository = projectInfoRepository;
        this.region = region;
    }

    public DeployResult deployStatic(String projectId) {
        try {
            ProjectInfo projectInfo = projectInfoRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

            if (!"STATIC".equals(projectInfo.getProjectType())) {
                throw new RuntimeException("Project is not a static website");
            }

            // Generate bucket name
            String bucketName = s3Service.generateBucketName();

            // Create bucket (also configures website + public policy)
            s3Service.createBucket(bucketName);

            // Upload static site (index.html + css/js) from the root folder
            s3Service.uploadStaticSite(bucketName, projectInfo.getExtractedPath());

            // Get website URL
            String websiteUrl = s3Service.getWebsiteUrl(bucketName);

            // Update project status (optional: save URL/bucket if you have fields)
            projectInfo.setStatus("DEPLOYED");
            projectInfoRepository.save(projectInfo);

            // Return result
            DeployResult result = new DeployResult();
            result.setDeploymentType("STATIC");
            result.setUrl(websiteUrl);
            result.setBucket(bucketName);
            result.setRegion(region);
            result.setStatus("SUCCESS");

            return result;

        } catch (Exception e) {
            DeployResult result = new DeployResult();
            result.setDeploymentType("STATIC");
            result.setStatus("FAILED");
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }
}