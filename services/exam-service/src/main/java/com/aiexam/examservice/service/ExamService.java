package com.aiexam.examservice.service;

import com.aiexam.commonevents.ExamGenerationRequestedEvent;
import com.aiexam.commonevents.FailureReason;
import com.aiexam.commonevents.GeneratedQuestion;
import com.aiexam.examservice.config.RabbitMQConfig;
import com.aiexam.examservice.dto.CreateExamRequest;
import com.aiexam.examservice.dto.ExamQuestionResponse;
import com.aiexam.examservice.dto.ExamResponse;
import com.aiexam.examservice.entity.Exam;
import com.aiexam.examservice.entity.ExamQuestion;
import com.aiexam.examservice.entity.ExamStatus;
import com.aiexam.examservice.exception.ExamNotFoundException;
import com.aiexam.examservice.repository.ExamRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final RabbitTemplate rabbitTemplate;

    public ExamResponse createExam(CreateExamRequest request, String createdByEmail) {
        Exam exam =
                Exam.builder()
                        .theme(request.theme())
                        .questionCount(request.questionCount())
                        .difficulty(request.difficulty())
                        .durationMinutes(request.durationMinutes())
                        .status(ExamStatus.PENDING)
                        .createdByEmail(createdByEmail)
                        .build();
        Exam saved = examRepository.save(exam);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_REQUESTED,
                new ExamGenerationRequestedEvent(
                        saved.getId(), saved.getTheme(), saved.getQuestionCount(), saved.getDifficulty()));

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ExamResponse getExam(UUID id) {
        return toResponse(findExamOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> listExams(String requesterEmail, boolean isAdmin) {
        List<Exam> exams =
                isAdmin ? examRepository.findAll() : examRepository.findByCreatedByEmail(requesterEmail);
        return exams.stream().map(this::toResponse).toList();
    }

    @Transactional
    public void completeExam(UUID examId, List<GeneratedQuestion> questions) {
        Exam exam = findExamOrThrow(examId);
        List<ExamQuestion> entities =
                questions.stream()
                        .map(
                                q ->
                                        ExamQuestion.builder()
                                                .exam(exam)
                                                .statement(q.statement())
                                                .options(q.options())
                                                .correctOptionIndex(q.correctOptionIndex())
                                                .build())
                        .toList();
        exam.markReady(entities);
    }

    @Transactional
    public void failExam(UUID examId, FailureReason reason, String message) {
        findExamOrThrow(examId).markFailed(reason, message);
    }

    private Exam findExamOrThrow(UUID id) {
        return examRepository.findById(id).orElseThrow(() -> new ExamNotFoundException(id));
    }

    private ExamResponse toResponse(Exam exam) {
        List<ExamQuestionResponse> questions =
                exam.getQuestions().stream()
                        .map(q -> new ExamQuestionResponse(q.getStatement(), q.getOptions(), q.getCorrectOptionIndex()))
                        .toList();
        return new ExamResponse(
                exam.getId(),
                exam.getTheme(),
                exam.getQuestionCount(),
                exam.getDifficulty(),
                exam.getStatus(),
                exam.getFailureReason(),
                exam.getFailureMessage(),
                exam.getDurationMinutes(),
                exam.getCreatedAt(),
                questions);
    }
}
