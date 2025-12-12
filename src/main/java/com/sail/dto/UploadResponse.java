package com.sail.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {
    private String projectId;
    private String fileName;
    private Double sizeMB;
    private String status;
}

