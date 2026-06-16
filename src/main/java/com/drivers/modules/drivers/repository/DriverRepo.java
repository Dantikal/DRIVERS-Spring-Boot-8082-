package com.drivers.modules.drivers.repository;

import com.drivers.modules.drivers.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
@Repository
public interface DriverRepo extends JpaRepository<Driver, UUID> {
}
