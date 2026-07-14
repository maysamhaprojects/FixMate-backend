package com.fixmate.modules.complaint.repository;

import com.fixmate.modules.complaint.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    List<Complaint> findByComplainantIdOrderByCreatedAtDesc(Long complainantId);
    List<Complaint> findAllByOrderByCreatedAtDesc();
}
