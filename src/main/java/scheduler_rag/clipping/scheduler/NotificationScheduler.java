package scheduler_rag.clipping.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private final SnsClient snsClient;

    @Scheduled(cron = "${clipping.schedule-cron}")
    public void run() {

    }
}
