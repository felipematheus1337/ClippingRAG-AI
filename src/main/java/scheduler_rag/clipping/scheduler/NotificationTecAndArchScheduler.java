package scheduler_rag.clipping.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import scheduler_rag.clipping.llms.OpenAIImpl;
import scheduler_rag.clipping.rag.RagNotificationService;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationTecAndArchScheduler {

    private final SnsClient snsClient;
    private final OpenAIImpl llmClient;
    private final RagNotificationService ragNotificationService;

    @Value("${clipping.sns-topic-arn}")
    private String topicArn;

    @Scheduled(cron = "${clipping.schedule-cron}")
    public void run() {

        var ids = ragNotificationService.ingestPDF();

        log.info("Starting scheduled task to send notifications...");

        var result =  llmClient.call("RAG");

        PublishRequest request = PublishRequest
                .builder()
                .message(result)
                .messageGroupId(UUID.randomUUID().toString())
                .topicArn(topicArn)
                .build();

        snsClient.publish(request);

        log.info("Notification sent successfully with message: {}", result);

        ragNotificationService.clearDocuments(ids);


    }
}
