package com.cadence.auth_service.repository;

import com.cadence.auth_service.model.UserDeletion;
import io.micrometer.observation.annotation.Observed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

@Observed
public interface UserDeletionRepository extends JpaRepository<UserDeletion, String> {
    List<UserDeletion> findAllByCompletedAtIsNullAndLastRequestedAtBefore(Instant cutoff);
}
