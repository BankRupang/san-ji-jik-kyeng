package com.bankrupang.sanjijk.user.domain.repository;

import com.bankrupang.sanjijk.user.domain.UserRole;
import com.bankrupang.sanjijk.user.domain.UserStatus;
import com.bankrupang.sanjijk.user.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

//    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByBusinessNumber(String businessNumber);

    Optional<User> findByUsername(String username);

    List<User> findAllByStatus(UserStatus status);
}
