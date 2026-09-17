package edu.cit.carcueva.notification;

import java.time.Instant;

public record NotificationView(Long notificationId, String message, Instant createdAt) {
}
