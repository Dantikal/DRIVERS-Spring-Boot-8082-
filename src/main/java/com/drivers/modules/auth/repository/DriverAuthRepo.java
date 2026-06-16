package com.drivers.modules.auth.repository;

import com.drivers.modules.auth.entity.DriverAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverAuthRepo extends JpaRepository<DriverAuth, UUID> {
    Optional<DriverAuth> findByPhone(String phone);
}
