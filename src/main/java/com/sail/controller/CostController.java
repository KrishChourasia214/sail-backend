package com.sail.controller;

import com.sail.dto.CostResult;
import com.sail.service.CostService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cost")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
public class CostController {

    private final CostService costService;

    public CostController(CostService costService) {
        this.costService = costService;
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<CostResult> getCost(@PathVariable String projectId) {
        try {
            CostResult result = costService.calculateCost(projectId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}

