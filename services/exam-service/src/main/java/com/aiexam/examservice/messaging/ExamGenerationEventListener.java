package com.aiexam.examservice.messaging;

import com.aiexam.commonevents.ExamGenerationCompletedEvent;
import com.aiexam.commonevents.ExamGenerationFailedEvent;
import com.aiexam.examservice.config.RabbitMQConfig;
import com.aiexam.examservice.service.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExamGenerationEventListener {

    private final ExamService examService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_COMPLETED)
    public void handleCompleted(ExamGenerationCompletedEvent event) {
        examService.completeExam(event.examId(), event.questions());
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_FAILED)
    public void handleFailed(ExamGenerationFailedEvent event) {
        examService.failExam(event.examId(), event.reason(), event.message());
    }
}
