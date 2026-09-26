package com.aiexam.examservice.messaging;

import com.aiexam.commonevents.ExamGenerationCompletedEvent;
import com.aiexam.commonevents.ExamGenerationFailedEvent;
import com.aiexam.examservice.config.RabbitMQConfig;
import com.aiexam.examservice.service.ExamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExamGenerationEventListener {

    private final ExamService examService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_COMPLETED)
    public void handleCompleted(ExamGenerationCompletedEvent event) {
        log.info("Received exam generation completed event for exam {}", event.examId());
        examService.completeExam(event.examId(), event.questions());
        log.info("Exam {} marked as READY", event.examId());
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_FAILED)
    public void handleFailed(ExamGenerationFailedEvent event) {
        log.warn(
                "Received exam generation failed event for exam {}: reason={}, message={}",
                event.examId(),
                event.reason(),
                event.message());
        examService.failExam(event.examId(), event.reason(), event.message());
    }
}
