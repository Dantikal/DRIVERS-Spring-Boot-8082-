package com.drivers.modules.orders.repository;

import com.drivers.modules.orders.entity.DriverOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DriverOrderRepo extends JpaRepository<DriverOrder, UUID>{
}
