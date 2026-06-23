package com.bankrupang.sanjijk.user.domain.entity;

import com.bankrupang.sanjijk.common.entity.BaseEntity;
import com.bankrupang.sanjijk.user.domain.UserRole;
import com.bankrupang.sanjijk.user.domain.UserStatus;
import com.bankrupang.sanjijk.user.domain.exception.UserDeletedException;
import com.bankrupang.sanjijk.user.domain.exception.UserNotSuspendedException;
import com.bankrupang.sanjijk.user.domain.exception.UserSuspendedException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "p_users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(nullable = false, updatable = false, unique = true, length = 20)
    private String username;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(nullable = false, updatable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 30)
    private String phone;

    // 관리자(MANAGER, MASTER)는 사업자번호가 없을 수 있음
    @Column(unique = true)
    private String businessNumber;

    @Column(name = "slack_id", nullable = false)
    private String slackId;

    @Column(name = "notification_allow")
    private boolean notificationAllow;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @Builder(access = AccessLevel.PRIVATE)
    public User(UUID userId, String username, String name, String email, String phone,
                String businessNumber, String slackId, boolean notificationAllow,
                UserRole role, UserStatus status) {
        if (userId != null) {
            this.assignId(userId);
        }
        this.username = username;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.businessNumber = businessNumber;
        this.slackId = slackId;
        this.notificationAllow = notificationAllow;
        this.role = role;
        this.status = status;
    }

    public static User create(UUID keycloakId, String username, String name, String email,
                              String phone, String businessNumber, String slackId,
                              boolean notificationAllow, UserRole role) {
        return User.builder()
                .userId(keycloakId)
                .username(username)
                .name(name)
                .email(email)
                .phone(phone)
                .businessNumber(businessNumber)
                .slackId(slackId)
                .notificationAllow(notificationAllow)
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
    }

    public void updateUserInfo (String name, String phone, String slackId) {
        if (name != null) this.name = name;
        if (phone != null) this.phone = phone;
        if (slackId != null) this.slackId = slackId;
    }

    public void updateBusinessNumber(String businessNumber) {
        if (businessNumber != null) this.businessNumber = businessNumber;
    }

    public void validateStatusForLogin() {
        if (status == UserStatus.SUSPENDED) {
            throw new UserSuspendedException();
        }
        if (status == UserStatus.DELETED) {
            throw new UserDeletedException();
        }
    }

    public void suspendUser() {
        if (status == UserStatus.DELETED) {
            throw new UserDeletedException();
        }
        if (status == UserStatus.SUSPENDED) {
            throw new UserSuspendedException();
        }
        this.status = UserStatus.SUSPENDED;
    }

    public void unsuspendUser() {
        if (status == UserStatus.DELETED) {
            throw new UserDeletedException();
        }
        if (status != UserStatus.SUSPENDED) {
            throw new UserNotSuspendedException();
        }
        this.status = UserStatus.ACTIVE;
    }

    public void deleteUser() {
        super.softDelete(this.getId());
        this.status = UserStatus.DELETED;
    }
}
