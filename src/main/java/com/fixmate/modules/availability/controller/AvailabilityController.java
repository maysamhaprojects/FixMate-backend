package com.fixmate.modules.availability.controller;

import com.fixmate.modules.auth.model.User;
import com.fixmate.modules.availability.dto.AvailabilityRequest;
import com.fixmate.modules.availability.model.ProAvailability;
import com.fixmate.modules.availability.service.AvailabilityService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    // Pro: get my availability
    @GetMapping("/pro/availability")
    public ResponseEntity<List<ProAvailability>> getMyAvailability(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(availabilityService.getAvailability(user.getId()));
    }

    // Pro: set availability for a day
    @PutMapping("/pro/availability")
    public ResponseEntity<ProAvailability> setAvailability(@AuthenticationPrincipal User user,
                                                            @RequestBody AvailabilityRequest request) {
        return ResponseEntity.ok(availabilityService.setAvailability(user, request));
    }

    // Public: view pro availability
    @GetMapping("/pros/{proId}/availability")
    public ResponseEntity<List<ProAvailability>> getProAvailability(@PathVariable Long proId) {
        return ResponseEntity.ok(availabilityService.getAvailability(proId));
    }
}
