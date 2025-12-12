package com.sail.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScanResult {
    private String projectType; // STATIC or SPRINGBOOT
    private String entryFile; // For static: index.html
    private String mainClass; // For Spring Boot: main application class
    private List<String> endpoints; // For Spring Boot: API endpoints
    private Integer htmlFiles; // For static
    private Integer jsFiles; // For static
    private Integer cssFiles; // For static
    private String rootFolder; // Root folder path
}

