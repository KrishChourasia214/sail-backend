package com.sail.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistoryEntry {
    private String projectId;
    private String fileName;
    private String deploymentType;
    private String url;
    private String status;
    private LocalDateTime timestamp;
    private String region;
}

