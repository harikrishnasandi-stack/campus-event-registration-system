package com.campus.events.scheduler;

import com.campus.events.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job to check for event registration deadlines and notify students.
 * Cron expression is configurable via app.notifications.cron property.
 */
@Component
public class DeadlineNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeadlineNotificationScheduler.class);

    @Autowired
    private NotificationService notificationService;

    /**
     * Executes according to the configured schedule (default: 8:00 AM daily).
     */
    @Scheduled(cron = "${app.notifications.cron:0 0 8 * * *}")
    public void runDeadlineCheck() {
        log.info("Running scheduled event deadline notification check...");
        try {
            int created = notificationService.processDeadlineNotifications();
            log.info("Scheduled deadline check finished. Created {} notifications.", created);
        } catch (Exception e) {
            log.error("Error during scheduled deadline notification check: {}", e.getMessage(), e);
        }
    }
}

