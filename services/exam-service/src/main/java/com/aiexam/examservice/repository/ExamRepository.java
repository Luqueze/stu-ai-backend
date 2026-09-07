package com.aiexam.examservice.repository;

import com.aiexam.examservice.entity.Exam;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamRepository extends JpaRepository<Exam, UUID> {
    List<Exam> findByCreatedByEmail(String createdByEmail);
}
