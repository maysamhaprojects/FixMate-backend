package com.fixmate.modules.auth.dto;
import com.fixmate.modules.auth.model.User;


import com.fixmate.modules.auth.model.Role;
import jakarta.validation.constraints.*;

public class RegisterRequest {

    @NotBlank
    private String fullName;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;

    @NotNull
    private Role role;

    private String phone;

    // שדות אופציונליים לבעל מקצוע (נשלחים בהרשמת בעל מקצוע)
    private String specialty;
    private String location;
    private Double hourlyRate;
    private Double hourlyRateMax;
    private String bio;
    private Integer yearsExperience;
    private String documents;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Double getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(Double hourlyRate) { this.hourlyRate = hourlyRate; }

    public Double getHourlyRateMax() { return hourlyRateMax; }
    public void setHourlyRateMax(Double hourlyRateMax) { this.hourlyRateMax = hourlyRateMax; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public Integer getYearsExperience() { return yearsExperience; }
    public void setYearsExperience(Integer yearsExperience) { this.yearsExperience = yearsExperience; }

    public String getDocuments() { return documents; }
    public void setDocuments(String documents) { this.documents = documents; }
}
