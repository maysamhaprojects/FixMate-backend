package com.fixmate.modules.availability.model;

import com.fixmate.modules.auth.model.User;
import jakarta.persistence.*;
import java.time.LocalTime;

@Entity
@Table(name = "pro_availability")
public class ProAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pro_id", nullable = false)
    private User pro;

    @Column(nullable = false)
    private String dayOfWeek; // SUNDAY, MONDAY, ...

    private LocalTime startTime;

    private LocalTime endTime;

    private boolean available = true;

    public ProAvailability() {}

    public Long getId() { return id; }

    public User getPro() { return pro; }
    public void setPro(User pro) { this.pro = pro; }

    public String getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}
