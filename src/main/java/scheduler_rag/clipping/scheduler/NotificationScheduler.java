package scheduler_rag.clipping.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationScheduler {

    @Scheduled(cron = "${clipping.schedule-cron}")
    public void run() {

    }
}
