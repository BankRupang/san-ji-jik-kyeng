package com.bankrupang.sanjijk.user.domain.repository;

import com.bankrupang.sanjijk.user.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByBusinessNumber(String businessNumber);

    Optional<User> findByUsername(String username);
}
