package com.fixmate.modules.pro.model;

import com.fixmate.modules.auth.model.User;
import jakarta.persistence.*;

@Entity
@Table(name = "pro_profiles")
public class ProProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    private String specialty;

    @Column(length = 1000)
    private String bio;

    private String location;

    private Double hourlyRate;

    private Double averageRating = 0.0;

    private Integer totalRatings = 0;

    private Integer yearsExperience;

    private String profilePicture;

    // מסמכים/תעודות שהועלו בהרשמה — מאוחסן כ-JSON (מערך של {name, data})
    @Column(columnDefinition = "LONGTEXT")
    private String documents;

    private boolean approved = false;

    // דחייה — נשמר כדי שבעל המקצוע יקבל הסבר, במקום להימחק
    private boolean rejected = false;

    private String rejectionReason;

    public ProProfile() {}

    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Double getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(Double hourlyRate) { this.hourlyRate = hourlyRate; }

    public Double getAverageRating() { return averageRating; }
    public void setAverageRating(Double averageRating) { this.averageRating = averageRating; }

    public Integer getTotalRatings() { return totalRatings; }
    public void setTotalRatings(Integer totalRatings) { this.totalRatings = totalRatings; }

    public Integer getYearsExperience() { return yearsExperience; }
    public void setYearsExperience(Integer yearsExperience) { this.yearsExperience = yearsExperience; }

    public String getProfilePicture() { return profilePicture; }
    public void setProfilePicture(String profilePicture) { this.profilePicture = profilePicture; }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public boolean isRejected() { return rejected; }
    public void setRejected(boolean rejected) { this.rejected = rejected; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getDocuments() { return documents; }
    public void setDocuments(String documents) { this.documents = documents; }
}
