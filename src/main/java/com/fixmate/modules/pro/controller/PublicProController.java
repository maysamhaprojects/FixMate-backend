package com.fixmate.modules.pro.controller;

import com.fixmate.modules.pro.model.ProProfile;
import com.fixmate.modules.pro.service.ProService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// זה ה-endpoint הפומבי — כל אחד יכול לחפש בעלי מקצוע בלי להתחבר
@RestController
@RequestMapping("/api/pros")
public class PublicProController {

    private final ProService proService;

    public PublicProController(ProService proService) {
        this.proService = proService;
    }

    // חיפוש בעלי מקצוע לפי קטגוריה ומיקום
    // דוגמה: GET /api/pros/search?specialty=electricity&location=תל אביב
    @GetMapping("/search")
    public ResponseEntity<List<ProProfile>> searchPros(
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false) String location) {
        return ResponseEntity.ok(proService.searchPros(specialty, location));
    }

    // שליפת פרופיל בעל מקצוע לפי ID
    // דוגמה: GET /api/pros/5
    @GetMapping("/{id}")
    public ResponseEntity<ProProfile> getProById(@PathVariable Long id) {
        return ResponseEntity.ok(proService.getById(id));
    }
}