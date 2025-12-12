package com.sail.controller;

import com.sail.dto.ScanResult;
import com.sail.service.ScanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scan")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ScanResult> scanProject(@PathVariable String projectId) {
        try {
            ScanResult result = scanService.scanProject(projectId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            ScanResult errorResult = new ScanResult();
            errorResult.setProjectType("ERROR");
            return ResponseEntity.status(500).body(errorResult);
        }
    }
}

