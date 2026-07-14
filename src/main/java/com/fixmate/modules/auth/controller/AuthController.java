package com.fixmate.modules.auth.controller;

import com.fixmate.modules.auth.dto.AuthResponse;
import com.fixmate.modules.auth.dto.LoginRequest;
import com.fixmate.modules.auth.dto.RegisterRequest;
import com.fixmate.modules.auth.service.AuthService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    public AuthResponse register(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }
}