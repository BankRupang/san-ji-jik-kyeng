package com.bankrupang.sanjijk.payment.domian.repository;

import com.bankrupang.sanjijk.payment.domian.entity.Payment;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentStatus;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    // confirm API용 - tossOrderId로 조회
    Optional<Payment> findByTossOrderId(String tossOrderId);

    // 낙찰 실패자 환불용
    List<Payment> findByAuctionIdAndPaymentTypeAndStatusAndUserIdNot(
            UUID auctionId, PaymentType paymentType, PaymentStatus status, UUID winnerId);

    // 유찰(입찰 0건) 환불용
    List<Payment> findByAuctionIdAndPaymentTypeAndStatus(
            UUID auctionId, PaymentType paymentType, PaymentStatus status);

    // 잔금 재결제용 - ABORTED 상태 NORMAL Payment 조회
    Optional<Payment> findByOrderIdAndPaymentTypeAndStatus(
            UUID orderId, PaymentType paymentType, PaymentStatus status);

    // 재결제 멱등성 체크용 - 이미 생성된 WINNING_REPAY READY Payment가 있는지 확인
    List<Payment> findAllByOrderIdAndPaymentTypeAndStatus(
            UUID orderId, PaymentType paymentType, PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdWithLock(@Param("id") UUID id);

    // 만료 Scheduler용 - READY 상태이면서 requestedAt이 기준 시간 이전인 Payment 조회
    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.requestedAt < :expiredBefore")
    List<Payment> findExpiredPayments(
            @Param("status") PaymentStatus status,
            @Param("expiredBefore") LocalDateTime expiredBefore,
            Pageable pageable);

    // 프론트로 넘길 낙찰금-보증금 (READY 상태만 - 같은 orderId라도 가장 최근 생성된 1건만 반환)
    @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId AND p.status = :status ORDER BY p.createdAt DESC")
    List<Payment> findAllByOrderIdAndStatusOrderByCreatedAtDesc(
            @Param("orderId") UUID orderId, @Param("status") PaymentStatus status, Pageable pageable);
}
