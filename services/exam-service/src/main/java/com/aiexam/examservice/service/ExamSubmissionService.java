package com.aiexam.examservice.service;

import com.aiexam.examservice.dto.ExamSubmissionResponse;
import com.aiexam.examservice.dto.SubmitExamRequest;
import com.aiexam.examservice.entity.Exam;
import com.aiexam.examservice.entity.ExamQuestion;
import com.aiexam.examservice.entity.ExamStatus;
import com.aiexam.examservice.entity.ExamSubmission;
import com.aiexam.examservice.exception.ExamAlreadySubmittedException;
import com.aiexam.examservice.exception.ExamNotFoundException;
import com.aiexam.examservice.exception.InvalidExamStateException;
import com.aiexam.examservice.exception.ExamSubmissionNotFoundException;
import com.aiexam.examservice.exception.InvalidSubmissionException;
import com.aiexam.examservice.repository.ExamRepository;
import com.aiexam.examservice.repository.ExamSubmissionRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExamSubmissionService {

    private final ExamRepository examRepository;
    private final ExamSubmissionRepository examSubmissionRepository;
    private final ExamSessionService examSessionService;

    @Transactional
    public ExamSubmissionResponse submit(UUID examId, String studentEmail, SubmitExamRequest request) {
        Exam exam = examRepository.findById(examId).orElseThrow(() -> new ExamNotFoundException(examId));
        if (exam.getStatus() != ExamStatus.READY) {
            throw new InvalidExamStateException(examId, exam.getStatus());
        }
        if (examSubmissionRepository.findByExam_IdAndStudentEmail(examId, studentEmail).isPresent()) {
            throw new ExamAlreadySubmittedException(examId);
        }

        // Requires a still-active timed session: throws if the student never started one, or if it expired.
        examSessionService.getSession(examId, studentEmail);

        List<ExamQuestion> questions = exam.getQuestions();
        List<Integer> answers = request.selectedOptions();
        if (answers.size() != questions.size()) {
            throw new InvalidSubmissionException(
                    examId, "Expected " + questions.size() + " answers but got " + answers.size());
        }

        int correctCount = 0;
        for (int i = 0; i < questions.size(); i++) {
            if (Objects.equals(answers.get(i), questions.get(i).getCorrectOptionIndex())) {
                correctCount++;
            }
        }
        double scorePercentage = questions.isEmpty() ? 0.0 : (correctCount * 100.0) / questions.size();

        ExamSubmission submission =
                examSubmissionRepository.save(
                        ExamSubmission.builder()
                                .exam(exam)
                                .studentEmail(studentEmail)
                                .selectedOptions(answers)
                                .correctCount(correctCount)
                                .totalQuestions(questions.size())
                                .scorePercentage(scorePercentage)
                                .build());

        examSessionService.endSession(examId, studentEmail);

        return toResponse(submission);
    }

    @Transactional(readOnly = true)
    public ExamSubmissionResponse getSubmission(UUID examId, String studentEmail) {
        ExamSubmission submission =
                examSubmissionRepository
                        .findByExam_IdAndStudentEmail(examId, studentEmail)
                        .orElseThrow(() -> new ExamSubmissionNotFoundException(examId));
        return toResponse(submission);
    }

    private ExamSubmissionResponse toResponse(ExamSubmission submission) {
        return new ExamSubmissionResponse(
                submission.getExam().getId(),
                submission.getTotalQuestions(),
                submission.getCorrectCount(),
                submission.getScorePercentage(),
                submission.getSubmittedAt());
    }
}
