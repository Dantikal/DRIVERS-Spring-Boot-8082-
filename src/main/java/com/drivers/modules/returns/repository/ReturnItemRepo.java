package com.drivers.modules.returns.repository;

import com.drivers.modules.returns.entity.ReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReturnItemRepo extends JpaRepository<ReturnItem, UUID> {
}
