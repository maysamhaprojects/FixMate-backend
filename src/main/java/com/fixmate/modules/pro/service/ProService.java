package com.fixmate.modules.pro.service;

import com.fixmate.modules.auth.model.User;
import com.fixmate.modules.pro.dto.ProProfileRequest;
import com.fixmate.modules.pro.model.ProProfile;
import com.fixmate.modules.pro.repository.ProProfileRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProService {

    private final ProProfileRepository proProfileRepository;

    public ProService(ProProfileRepository proProfileRepository) {
        this.proProfileRepository = proProfileRepository;
    }

    public ProProfile getOrCreateProfile(User user) {
        return proProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    ProProfile p = new ProProfile();
                    p.setUser(user);
                    return proProfileRepository.save(p);
                });
    }

    public ProProfile updateProfile(User user, ProProfileRequest req) {
        ProProfile profile = getOrCreateProfile(user);
        if (req.getSpecialty() != null) profile.setSpecialty(req.getSpecialty());
        if (req.getBio() != null) profile.setBio(req.getBio());
        if (req.getLocation() != null) profile.setLocation(req.getLocation());
        if (req.getHourlyRate() != null) profile.setHourlyRate(req.getHourlyRate());
        if (req.getHourlyRateMax() != null) profile.setHourlyRateMax(req.getHourlyRateMax());
        if (req.getYearsExperience() != null) profile.setYearsExperience(req.getYearsExperience());
        if (req.getProfilePicture() != null) profile.setProfilePicture(req.getProfilePicture());
        return proProfileRepository.save(profile);
    }

    public List<ProProfile> searchPros(String specialty, String location) {
        if (specialty != null && !specialty.isBlank()) {
            return proProfileRepository.findBySpecialtyContainingIgnoreCaseAndApprovedTrue(specialty);
        }
        if (location != null && !location.isBlank()) {
            return proProfileRepository.findByLocationContainingIgnoreCaseAndApprovedTrue(location);
        }
        return proProfileRepository.findByApprovedTrue();
    }

    public ProProfile getById(Long id) {
        return proProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pro profile not found"));
    }
}
