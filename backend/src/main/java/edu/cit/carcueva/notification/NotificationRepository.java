package edu.cit.carcueva.notification;

import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationRepository extends JpaRepository<Notification, Long> {
}
