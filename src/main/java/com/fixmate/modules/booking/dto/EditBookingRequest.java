package com.fixmate.modules.booking.dto;

import java.time.LocalDateTime;

/**
 * בקשת עריכת הזמנה קיימת ע"י הלקוח (רק כשההזמנה עדיין ממתינה לאישור).
 * הלקוח לא יכול לשנות את בעל המקצוע או סוג השירות — רק מועד / כתובת / הערות.
 */
public class EditBookingRequest {

    private LocalDateTime scheduledAt;
    private String address;
    private String notes;

    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
