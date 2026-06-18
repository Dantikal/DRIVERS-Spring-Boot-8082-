package com.drivers.modules.drivers.repository;

import com.drivers.modules.drivers.entity.Driver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;
@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {
    Page<Driver> findAll(Pageable pageable);
}
