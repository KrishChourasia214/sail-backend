package com.sail.controller;

import com.sail.dto.HistoryEntry;
import com.sail.service.HistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/history")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public ResponseEntity<List<HistoryEntry>> getHistory() {
        List<HistoryEntry> history = historyService.getAllHistory();
        return ResponseEntity.ok(history);
    }
}

