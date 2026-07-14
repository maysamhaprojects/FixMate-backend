package com.fixmate.modules.pro.repository;

import com.fixmate.modules.pro.model.ProProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProProfileRepository extends JpaRepository<ProProfile, Long> {

    Optional<ProProfile> findByUserId(Long userId);

    List<ProProfile> findBySpecialtyContainingIgnoreCaseAndApprovedTrue(String specialty);

    List<ProProfile> findByLocationContainingIgnoreCaseAndApprovedTrue(String location);

    List<ProProfile> findByApprovedTrue();

    List<ProProfile> findByApprovedFalse();
    
}
