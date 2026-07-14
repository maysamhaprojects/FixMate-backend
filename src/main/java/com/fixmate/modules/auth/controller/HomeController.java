package com.fixmate.modules.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "FixMate Backend is running ✅";
    }

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}
