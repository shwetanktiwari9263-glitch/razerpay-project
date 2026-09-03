package com.recoveryagent.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.recoveryagent.entity.RecoveryOutcome;
import com.recoveryagent.entity.OutcomeStatus;

@Repository
public interface RecoveryOutcomeRepository extends JpaRepository<RecoveryOutcome, Long> {

    Optional<RecoveryOutcome> findByRecoveryActionId(Long recoveryActionId);

    Optional<RecoveryOutcome> findByOriginalPaymentEventId(Long originalPaymentEventId);

    List<RecoveryOutcome> findByRecoverySuccessTrueOrderByCreatedAtDesc();

    List<RecoveryOutcome> findByOutcomeStatusOrderByCreatedAtDesc(OutcomeStatus outcomeStatus);
}
