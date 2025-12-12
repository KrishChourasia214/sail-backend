package com.sail.repository;

import com.sail.model.DeploymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeploymentHistoryRepository extends JpaRepository<DeploymentHistory, String> {
}

