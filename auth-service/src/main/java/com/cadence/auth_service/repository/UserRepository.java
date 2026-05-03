package com.cadence.auth_service.repository;

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

    @Query("select u.role from User u where u.email = :email")
    Role getRoleByEmail(@Param("email") String email);

}
