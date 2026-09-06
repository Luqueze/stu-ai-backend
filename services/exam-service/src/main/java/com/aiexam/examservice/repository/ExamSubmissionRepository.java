package com.aiexam.examservice.repository;

import com.aiexam.examservice.entity.ExamSubmission;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamSubmissionRepository extends JpaRepository<ExamSubmission, UUID> {
    Optional<ExamSubmission> findByExam_IdAndStudentEmail(UUID examId, String studentEmail);
}
