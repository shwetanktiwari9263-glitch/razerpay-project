package com.recoveryagent.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.recoveryagent.entity.SystemInsight;
import com.recoveryagent.entity.DegradationStatus;
import com.recoveryagent.entity.RootCauseCategory;

@Repository
public interface SystemInsightRepository extends JpaRepository<SystemInsight, Long> {

    Optional<SystemInsight> findByPaymentEventId(Long paymentEventId);

    List<SystemInsight> findByDegradationStatusOrderByFailureProbabilityDesc(DegradationStatus degradationStatus);

    List<SystemInsight> findByRootCauseCategoryOrderByModelConfidenceDesc(RootCauseCategory rootCauseCategory);
}
