package com.sail.service;

import com.sail.dto.UploadResponse;
import com.sail.model.ProjectInfo;
import com.sail.repository.ProjectInfoRepository;
import com.sail.utils.FileUtils;
import com.sail.utils.ZipExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class UploadService {

    private final ZipExtractor zipExtractor;
    private final FileUtils fileUtils;
    private final ProjectInfoRepository projectInfoRepository;
    private final String uploadDir;
    private final String extractedDir;

    public UploadService(ZipExtractor zipExtractor,
                         FileUtils fileUtils,
                         ProjectInfoRepository projectInfoRepository,
                         @Value("${sail.temp.upload.dir}") String uploadDir,
                         @Value("${sail.temp.extracted.dir}") String extractedDir) {
        this.zipExtractor = zipExtractor;
        this.fileUtils = fileUtils;
        this.projectInfoRepository = projectInfoRepository;
        this.uploadDir = uploadDir;
        this.extractedDir = extractedDir;
    }

    public UploadResponse uploadProject(MultipartFile file) throws IOException {
        // Generate project ID
        String projectId = UUID.randomUUID().toString();

        // Create directories
        Path uploadPath = Paths.get(uploadDir, projectId);
        Path extractedPath = Paths.get(extractedDir, projectId);
        fileUtils.createDirectories(uploadPath);
        fileUtils.createDirectories(extractedPath);

        // Save uploaded file
        Path savedPath = uploadPath.resolve(file.getOriginalFilename());

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, savedPath, StandardCopyOption.REPLACE_EXISTING);
        }

        File savedFile = savedPath.toFile();

        // Extract ZIP
        String extractedPathStr = zipExtractor.extractZip(savedFile, extractedPath.toString());

        // Calculate size
        double sizeMB = fileUtils.getFileSizeMB(savedFile);

        // Save project info
        ProjectInfo projectInfo = new ProjectInfo();
        projectInfo.setProjectId(projectId);
        projectInfo.setFileName(file.getOriginalFilename());
        projectInfo.setSizeMB(sizeMB);
        projectInfo.setExtractedPath(extractedPathStr);
        projectInfo.setStatus("RECEIVED");
        projectInfoRepository.save(projectInfo);

        // Return response
        UploadResponse response = new UploadResponse();
        response.setProjectId(projectId);
        response.setFileName(file.getOriginalFilename());
        response.setSizeMB(sizeMB);
        response.setStatus("RECEIVED");

        return response;
    }

    public ProjectInfo getProjectInfo(String projectId) {
        return projectInfoRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));
    }
}

