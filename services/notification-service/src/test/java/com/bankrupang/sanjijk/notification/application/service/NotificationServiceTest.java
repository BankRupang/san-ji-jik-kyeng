package com.bankrupang.sanjijk.notification.application.service;

import com.bankrupang.sanjijk.common.exception.BaseException;
import com.bankrupang.sanjijk.notification.domain.entity.NotificationLog;
import com.bankrupang.sanjijk.notification.domain.enums.NotificationType;
import com.bankrupang.sanjijk.notification.domain.repository.NotificationLogRepository;
import com.bankrupang.sanjijk.notification.exception.NotificationErrorCode;
import com.bankrupang.sanjijk.notification.presentation.dto.response.NotificationResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Nested
    @DisplayName("내 알림 목록 조회")
    class GetMyNotifications {

        @Test
        @DisplayName("성공")
        void success() {
            // given
            UUID userId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 10);
            NotificationLog log = createLog(userId, UUID.randomUUID());
            given(notificationLogRepository.findByUserId(userId, pageable))
                    .willReturn(new PageImpl<>(List.of(log)));

            // when
            Page<NotificationResponseDto> result = notificationService.getMyNotifications(userId, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getUserId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("알림이 없으면 빈 페이지 반환")
        void emptyResult() {
            // given
            UUID userId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 10);
            given(notificationLogRepository.findByUserId(userId, pageable))
                    .willReturn(new PageImpl<>(List.of()));

            // when
            Page<NotificationResponseDto> result = notificationService.getMyNotifications(userId, pageable);

            // then
            assertThat(result.getContent()).isEmpty();
        }
    }
    @Nested
    @DisplayName("알림 단건 조회")
    class GetNotification {

        @Test
        @DisplayName("성공")
        void success() {
            // given
            UUID userId = UUID.randomUUID();
            UUID notificationId = UUID.randomUUID();
            NotificationLog log = createLog(userId, notificationId);
            given(notificationLogRepository.findById(notificationId))
                    .willReturn(Optional.of(log));

            // when
            NotificationResponseDto result = notificationService.getNotification(userId, notificationId);

            // then
            assertThat(result.getId()).isEqualTo(notificationId);
            assertThat(result.getUserId()).isEqualTo(userId);
        }
        @Test
        @DisplayName("존재하지 않는 알림이면 예외 발생")
        void notFound() {
            // given
            UUID userId = UUID.randomUUID();
            UUID notificationId = UUID.randomUUID();
            given(notificationLogRepository.findById(notificationId))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> notificationService.getNotification(userId, notificationId))
                    .isInstanceOf(BaseException.class)
                    .hasMessage(NotificationErrorCode.NOTIFICATION_NOT_FOUND.getMessage());
        }
        @Test
        @DisplayName("다른 사용자의 알림이면 예외 발생")
        void accessDenied() {
            // given
            UUID ownerId = UUID.randomUUID();
            UUID otherId = UUID.randomUUID();
            UUID notificationId = UUID.randomUUID();
            NotificationLog log = createLog(ownerId, notificationId);
            given(notificationLogRepository.findById(notificationId))
                    .willReturn(Optional.of(log));

            // when & then
            assertThatThrownBy(() -> notificationService.getNotification(otherId, notificationId))
                    .isInstanceOf(BaseException.class)
                    .hasMessage(NotificationErrorCode.NOTIFICATION_ACCESS_DENIED.getMessage());
        }
    }
    @Nested
    @DisplayName("전체 알림 목록 조회 (어드민)")
    class GetAllNotifications {

        @Test
        @DisplayName("성공")
        void success() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            NotificationLog log = createLog(UUID.randomUUID(), UUID.randomUUID());
            given(notificationLogRepository.findAll(pageable))
                    .willReturn(new PageImpl<>(List.of(log)));

            // when
            Page<NotificationResponseDto> result = notificationService.getAllNotifications(pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("알림이 없으면 빈 페이지 반환")
        void emptyResult() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            given(notificationLogRepository.findAll(pageable))
                    .willReturn(new PageImpl<>(List.of()));

            // when
            Page<NotificationResponseDto> result = notificationService.getAllNotifications(pageable);

            // then
            assertThat(result.getContent()).isEmpty();
        }
    }
    private NotificationLog createLog(UUID userId, UUID notificationId) {
        NotificationLog log = NotificationLog.create(
                userId, NotificationType.AUCTION_WON,
                "낙찰 확정!", "메시지 내용",
                UUID.randomUUID(), "AUCTION", "U_TEST_SLACK"
        );
        ReflectionTestUtils.setField(log, "id", notificationId);
        return log;
    }
}