package com.fixmate.modules.issue.repository;

import com.fixmate.modules.issue.model.Issue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IssueRepository extends JpaRepository<Issue, Long> {

    List<Issue> findByClientIdOrderByCreatedAtDesc(Long clientId);
}
