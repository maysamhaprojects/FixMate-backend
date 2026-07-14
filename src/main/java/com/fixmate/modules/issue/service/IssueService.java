package com.fixmate.modules.issue.service;

import com.fixmate.modules.auth.model.User;
import com.fixmate.modules.issue.dto.IssueRequest;
import com.fixmate.modules.issue.model.Issue;
import com.fixmate.modules.issue.repository.IssueRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IssueService {

    private final IssueRepository issueRepository;

    public IssueService(IssueRepository issueRepository) {
        this.issueRepository = issueRepository;
    }

    public Issue createIssue(User client, IssueRequest req) {
        Issue issue = new Issue();
        issue.setClient(client);
        issue.setDescription(req.getDescription());
        issue.setImageUrl(req.getImageUrl());
        issue.setAiSuggestion(req.getAiSuggestion());
        return issueRepository.save(issue);
    }

    public List<Issue> getClientIssues(Long clientId) {
        return issueRepository.findByClientIdOrderByCreatedAtDesc(clientId);
    }

    public List<Issue> getAllIssues() {
        return issueRepository.findAll();
    }
}
