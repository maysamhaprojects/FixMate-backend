package com.fixmate.modules.complaint.dto;

import jakarta.validation.constraints.NotBlank;

public class ComplaintRequest {

    @NotBlank
    private String subject;

    @NotBlank
    private String description;

    // אופציונלי — מזהה ההזמנה שהתלונה קשורה אליה
    private Long bookingId;

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
}
