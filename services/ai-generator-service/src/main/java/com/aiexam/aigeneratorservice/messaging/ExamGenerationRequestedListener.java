package com.aiexam.aigeneratorservice.messaging;

import com.aiexam.aigeneratorservice.config.RabbitMQConfig;
import com.aiexam.aigeneratorservice.service.ExamQuestionGenerationService;
import com.aiexam.commonevents.ExamGenerationRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExamGenerationRequestedListener {

    private final ExamQuestionGenerationService examQuestionGenerationService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_REQUESTED)
    public void handleRequested(ExamGenerationRequestedEvent event) {
        examQuestionGenerationService.generate(event);
    }
}
