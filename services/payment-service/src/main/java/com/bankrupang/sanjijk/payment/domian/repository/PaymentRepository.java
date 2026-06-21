package com.bankrupang.sanjijk.payment.domian.repository;

import com.bankrupang.sanjijk.payment.domian.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
}
