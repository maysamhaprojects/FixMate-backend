package com.fixmate.modules.availability.service;

import com.fixmate.modules.auth.model.User;
import com.fixmate.modules.availability.dto.AvailabilityRequest;
import com.fixmate.modules.availability.model.ProAvailability;
import com.fixmate.modules.availability.repository.ProAvailabilityRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AvailabilityService {

    private final ProAvailabilityRepository availabilityRepository;

    public AvailabilityService(ProAvailabilityRepository availabilityRepository) {
        this.availabilityRepository = availabilityRepository;
    }

    public List<ProAvailability> getAvailability(Long proId) {
        return availabilityRepository.findByProId(proId);
    }

    public ProAvailability setAvailability(User pro, AvailabilityRequest req) {
        ProAvailability slot = availabilityRepository
                .findByProIdAndDayOfWeek(pro.getId(), req.getDayOfWeek())
                .orElseGet(() -> {
                    ProAvailability newSlot = new ProAvailability();
                    newSlot.setPro(pro);
                    newSlot.setDayOfWeek(req.getDayOfWeek());
                    return newSlot;
                });

        slot.setStartTime(req.getStartTime());
        slot.setEndTime(req.getEndTime());
        slot.setAvailable(req.isAvailable());

        return availabilityRepository.save(slot);
    }
}
