package com.fixmate.modules.rating.repository;

import com.fixmate.modules.rating.model.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {

    List<Rating> findByProId(Long proId);

    List<Rating> findByClientId(Long clientId);

    boolean existsByBookingId(Long bookingId);

    @Query("SELECT AVG(r.score) FROM Rating r WHERE r.pro.id = :proId")
    Double findAverageScoreByProId(Long proId);
}
