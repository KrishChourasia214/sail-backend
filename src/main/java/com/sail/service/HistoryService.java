package com.sail.service;

import com.sail.dto.HistoryEntry;
import com.sail.model.DeploymentHistory;
import com.sail.repository.DeploymentHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class HistoryService {

    private final DeploymentHistoryRepository historyRepository;

    public HistoryService(DeploymentHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    public List<HistoryEntry> getAllHistory() {
        return historyRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public void saveDeploymentHistory(DeploymentHistory history) {
        historyRepository.save(history);
    }

    private HistoryEntry convertToDto(DeploymentHistory history) {
        HistoryEntry entry = new HistoryEntry();
        entry.setProjectId(history.getProjectId());
        entry.setFileName(history.getFileName());
        entry.setDeploymentType(history.getDeploymentType());
        entry.setUrl(history.getUrl());
        entry.setStatus(history.getStatus());
        entry.setTimestamp(history.getTimestamp());
        entry.setRegion(history.getRegion());
        return entry;
    }
}

