package com.fixmate.modules.pro.dto;

public class ProProfileRequest {

    private String specialty;
    private String bio;
    private String location;
    private Double hourlyRate;
    private Double hourlyRateMax;
    private Integer yearsExperience;
    private String profilePicture;

    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Double getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(Double hourlyRate) { this.hourlyRate = hourlyRate; }

    public Double getHourlyRateMax() { return hourlyRateMax; }
    public void setHourlyRateMax(Double hourlyRateMax) { this.hourlyRateMax = hourlyRateMax; }

    public Integer getYearsExperience() { return yearsExperience; }
    public void setYearsExperience(Integer yearsExperience) { this.yearsExperience = yearsExperience; }

    public String getProfilePicture() { return profilePicture; }
    public void setProfilePicture(String profilePicture) { this.profilePicture = profilePicture; }
}
