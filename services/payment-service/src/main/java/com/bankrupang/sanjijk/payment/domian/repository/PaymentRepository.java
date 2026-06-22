package com.bankrupang.sanjijk.payment.domian.repository;

import com.bankrupang.sanjijk.payment.domian.entity.Payment;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentStatus;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
