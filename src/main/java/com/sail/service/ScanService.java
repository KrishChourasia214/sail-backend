package com.sail.service;

import com.sail.dto.ScanResult;
import com.sail.model.ProjectInfo;
import com.sail.repository.ProjectInfoRepository;
import com.sail.utils.EndpointScanner;
import com.sail.utils.ProjectDetector;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

@Service
public class ScanService {

    private final ProjectDetector projectDetector;
    private final EndpointScanner endpointScanner;
    private final ProjectInfoRepository projectInfoRepository;

    public ScanService(ProjectDetector projectDetector,
                       EndpointScanner endpointScanner,
                       ProjectInfoRepository projectInfoRepository) {
        this.projectDetector = projectDetector;
        this.endpointScanner = endpointScanner;
        this.projectInfoRepository = projectInfoRepository;
    }

    public ScanResult scanProject(String projectId) throws IOException {
        ProjectInfo projectInfo = projectInfoRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        String extractedPath = projectInfo.getExtractedPath();

        // 1. Normalize to the actual project folder (e.g. .../notes-webapp, .../taskmanager)
        String rootFolder = projectDetector.findRootFolder(extractedPath);

        // 2. Detect type based on that root folder
        String projectType = projectDetector.detectProjectType(rootFolder);

        ScanResult result = new ScanResult();
        result.setProjectType(projectType);
        result.setRootFolder(rootFolder);

        if ("STATIC".equals(projectType)) {
            scanStaticProject(rootFolder, result);
        } else if ("SPRINGBOOT".equals(projectType)) {
            scanSpringBootProject(rootFolder, result);
        }

        // Update project info
        projectInfo.setExtractedPath(rootFolder); 
        projectInfo.setProjectType(projectType);
        projectInfo.setStatus("SCANNED");
        projectInfoRepository.save(projectInfo);

        return result;
    }

    private void scanStaticProject(String extractedPath, ScanResult result) throws IOException {
        Path projectPath = Paths.get(extractedPath);
        
        // Find index.html
        String entryFile = findIndexHtml(projectPath);
        result.setEntryFile(entryFile != null ? entryFile : "index.html");

        // Count files
        int htmlCount = countFilesByExtension(projectPath, ".html");
        int jsCount = countFilesByExtension(projectPath, ".js");
        int cssCount = countFilesByExtension(projectPath, ".css");

        result.setHtmlFiles(htmlCount);
        result.setJsFiles(jsCount);
        result.setCssFiles(cssCount);
    }

    private void scanSpringBootProject(String extractedPath, ScanResult result) throws IOException {
        // Find main class
        String mainClass = endpointScanner.findMainClass(extractedPath);
        result.setMainClass(mainClass);

        // Scan endpoints
        List<String> endpoints = endpointScanner.scanEndpoints(extractedPath);
        result.setEndpoints(endpoints);
    }

    private String findIndexHtml(Path projectPath) throws IOException {
        try (Stream<Path> paths = Files.walk(projectPath)) {
            return paths.filter(p -> p.getFileName().toString().equals("index.html"))
                    .map(p -> projectPath.relativize(p).toString())
                    .findFirst()
                    .orElse(null);
        }
    }

    private int countFilesByExtension(Path projectPath, String extension) throws IOException {
        try (Stream<Path> paths = Files.walk(projectPath)) {
            return (int) paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(extension))
                    .count();
        }
    }
}

