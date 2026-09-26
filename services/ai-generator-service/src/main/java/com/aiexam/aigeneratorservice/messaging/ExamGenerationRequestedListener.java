package com.aiexam.aigeneratorservice.messaging;

import com.aiexam.aigeneratorservice.config.RabbitMQConfig;
import com.aiexam.aigeneratorservice.service.ExamQuestionGenerationService;
import com.aiexam.commonevents.ExamGenerationRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExamGenerationRequestedListener {

    private final ExamQuestionGenerationService examQuestionGenerationService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_REQUESTED)
    public void handleRequested(ExamGenerationRequestedEvent event) {
        log.info("Received exam generation request for exam {}", event.examId());
        examQuestionGenerationService.generate(event);
        log.info("Finished processing exam generation request for exam {}", event.examId());
    }
}
