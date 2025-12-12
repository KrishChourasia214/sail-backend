package com.sail.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "deployment_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String deploymentType; // STATIC or SPRINGBOOT

    private String url;
    private String bucket;
    private String lambdaName;
    private String apiUrl;
    private String region;
    private String status; // SUCCESS or FAILED
    @Lob
    @Column(name = "error_message")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }
}

