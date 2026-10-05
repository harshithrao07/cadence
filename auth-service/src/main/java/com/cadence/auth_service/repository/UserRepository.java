package com.cadence.auth_service.repository;

import com.cadence.auth_service.model.UserStatus;
import java.time.Instant;
import java.util.List;

import com.cadence.auth_service.model.Role;
import com.cadence.auth_service.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    List<User> findAllByStatus(UserStatus status);

    /**
     * Reads the status column from the database. Unlike findById this never returns a cached entity, which matters
     * inside a web request: open-in-view keeps one EntityManager (and its cached User) for the whole request.
     */
    @Query("select u.status from User u where u.id = :id")
    Optional<UserStatus> findStatusById(@Param("id") String id);

    List<User> findAllByStatusAndRegisteredAtBefore(UserStatus status, Instant cutoff);

    @Query("select u.role from User u where u.email = :email")
    Role getRoleByEmail(@Param("email") String email);

}
