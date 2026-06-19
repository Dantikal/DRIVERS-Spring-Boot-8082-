package com.drivers.modules.returns.repository;

import com.drivers.modules.returns.entity.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReturnRequestRepo extends JpaRepository<ReturnRequest, UUID>, JpaSpecificationExecutor<ReturnRequest> {
}
