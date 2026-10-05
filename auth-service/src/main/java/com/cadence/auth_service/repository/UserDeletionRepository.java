package com.cadence.auth_service.repository;

import com.cadence.auth_service.model.UserDeletion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface UserDeletionRepository extends JpaRepository<UserDeletion, String> {
    List<UserDeletion> findAllByCompletedAtIsNullAndLastRequestedAtBefore(Instant cutoff);
}
