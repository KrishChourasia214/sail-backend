package com.sail.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "project_info")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectInfo {
    @Id
    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false)
    private String fileName;

    private Double sizeMB;
    private String projectType; // STATIC or SPRINGBOOT
    private String extractedPath;
    private String status; // RECEIVED, SCANNED, DEPLOYED, FAILED
}

