package com.fixmate.modules.issue.controller;

import com.fixmate.modules.auth.model.User;
import com.fixmate.modules.issue.dto.IssueRequest;
import com.fixmate.modules.issue.model.Issue;
import com.fixmate.modules.issue.service.IssueService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/client/issues")
public class IssueController {

    private final IssueService issueService;

    public IssueController(IssueService issueService) {
        this.issueService = issueService;
    }

    @PostMapping
    public ResponseEntity<Issue> createIssue(@AuthenticationPrincipal User user,
                                              @RequestBody IssueRequest request) {
        return ResponseEntity.ok(issueService.createIssue(user, request));
    }

    @GetMapping
    public ResponseEntity<List<Issue>> getMyIssues(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(issueService.getClientIssues(user.getId()));
    }
}
