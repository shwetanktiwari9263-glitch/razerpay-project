package com.recoveryagent.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.recoveryagent.entity.AiAgentAnalysis;
import com.recoveryagent.entity.RecoveryChannel;

@Repository
public interface AiAgentAnalysisRepository extends JpaRepository<AiAgentAnalysis, Long> {

    Optional<AiAgentAnalysis> findByPaymentEventId(Long paymentEventId);

    List<AiAgentAnalysis> findBySuggestedChannelOrderByConfidenceScoreDesc(RecoveryChannel suggestedChannel);

    List<AiAgentAnalysis> findByPaymentEventIdInOrderByCreatedAtDesc(List<Long> paymentEventIds);
}
