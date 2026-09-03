package com.recoveryagent.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.recoveryagent.entity.PaymentEvent;
import com.recoveryagent.entity.PaymentStatus;

@Repository
public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    Optional<PaymentEvent> findByEventId(String eventId);

    Optional<PaymentEvent> findByPaymentId(String paymentId);

    List<PaymentEvent> findByStatus(PaymentStatus status);

    Page<PaymentEvent> findByStatus(PaymentStatus status, Pageable pageable);

    List<PaymentEvent> findByStatusAndCreatedAtAfter(PaymentStatus status, LocalDateTime createdAt);

    List<PaymentEvent> findByCustomerId(String customerId);

    List<PaymentEvent> findByOrderId(String orderId);
}
