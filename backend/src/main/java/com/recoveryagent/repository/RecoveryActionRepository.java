package com.recoveryagent.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.recoveryagent.entity.RecoveryAction;
import com.recoveryagent.entity.ExecutionStatus;
import com.recoveryagent.entity.RecoveryChannel;

@Repository
public interface RecoveryActionRepository extends JpaRepository<RecoveryAction, Long> {

    Optional<RecoveryAction> findByAiAgentAnalysisId(Long aiAgentAnalysisId);

    List<RecoveryAction> findByExecutionStatusOrderByTriggeredAtDesc(ExecutionStatus executionStatus);

    List<RecoveryAction> findByPaymentEventIdOrderByTriggeredAtDesc(Long paymentEventId);

    List<RecoveryAction> findByActionTypeOrderByTriggeredAtDesc(RecoveryChannel actionType);
}
