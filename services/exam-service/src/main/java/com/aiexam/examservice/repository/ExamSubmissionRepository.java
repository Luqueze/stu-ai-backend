package com.aiexam.examservice.repository;

import com.aiexam.examservice.entity.ExamSubmission;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamSubmissionRepository extends JpaRepository<ExamSubmission, UUID> {
    boolean existsByExam_IdAndStudentEmail(UUID examId, String studentEmail);

    Optional<ExamSubmission> findFirstByExam_IdAndStudentEmailOrderBySubmittedAtDesc(
            UUID examId, String studentEmail);

    List<ExamSubmission> findByExam_IdAndStudentEmailOrderBySubmittedAtDesc(UUID examId, String studentEmail);

    List<ExamSubmission> findByExam_IdOrderByStudentEmailAscSubmittedAtDesc(UUID examId);
}
