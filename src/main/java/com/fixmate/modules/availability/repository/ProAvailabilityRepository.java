package com.fixmate.modules.availability.repository;

import com.fixmate.modules.availability.model.ProAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProAvailabilityRepository extends JpaRepository<ProAvailability, Long> {

    List<ProAvailability> findByProId(Long proId);

    Optional<ProAvailability> findByProIdAndDayOfWeek(Long proId, String dayOfWeek);
}
