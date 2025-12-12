package com.sail.utils;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@Component
public class ProjectDetector {

    public String detectProjectType(String extractedPath) throws IOException {
        Path projectPath = Paths.get(extractedPath);
        
        // Check for Spring Boot indicators
        if (hasPomXml(projectPath) || hasSpringBootStructure(projectPath)) {
            return "SPRINGBOOT";
        }
        
        // Check for static website indicators
        if (hasAnyHtmlFile(projectPath)) {
            return "STATIC";
        }
        
        return "UNKNOWN";
    }

    private boolean hasPomXml(Path path) throws IOException {
        return Files.walk(path)
                .anyMatch(p -> p.getFileName().toString().equals("pom.xml"));
    }

    private boolean hasSpringBootStructure(Path path) throws IOException {
        Path mainJava = path.resolve("src/main/java");
        return Files.exists(mainJava) && Files.isDirectory(mainJava);
    }

    private boolean hasAnyHtmlFile(Path path) throws IOException {
        return Files.walk(path)
                .filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString().toLowerCase())
                .anyMatch(name -> name.endsWith(".html") || name.endsWith(".htm"));
    }
    
    private boolean hasHtmlAtRoot(Path path) throws IOException {
        try (Stream<Path> paths = Files.list(path)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString().toLowerCase())
                    .anyMatch(name -> name.endsWith(".html") || name.endsWith(".htm"));
        }
    }

    public String findRootFolder(String extractedPath) throws IOException {
        Path projectPath = Paths.get(extractedPath);

        if (hasHtmlAtRoot(projectPath)) {
            return projectPath.toString();
        }

        if (Files.exists(projectPath.resolve("pom.xml"))) {
            return projectPath.toString();
        }

        // If extracted to a single subdirectory, return that
        try (Stream<Path> paths = Files.list(projectPath)) {
            long dirCount = paths.filter(Files::isDirectory).count();
            if (dirCount == 1) {
                return Files.list(projectPath)
                        .filter(Files::isDirectory)
                        .findFirst()
                        .map(Path::toString)
                        .orElse(extractedPath);
            }
        }

        return extractedPath;
    }
}

