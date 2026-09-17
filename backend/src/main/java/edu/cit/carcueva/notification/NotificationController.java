package edu.cit.carcueva.notification;

import java.util.Comparator;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class NotificationController {

    private final NotificationRepository notificationRepository;

    NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/api/notifications")
    List<NotificationView> getNotifications() {
        return notificationRepository.findAll().stream()
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .map(n -> new NotificationView(n.getNotificationId(), n.getMessage(), n.getCreatedAt()))
                .toList();
    }
}
