package com.drivers.modules.offline.repository;

import com.drivers.modules.offline.entity.OfflineQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import com.drivers.modules.offline.entity.QueueStatus;

@Repository
public interface OfflineQueueRepo extends JpaRepository<OfflineQueue, UUID> {
    List<OfflineQueue> findByStatusOrderByCreatedOfflineAtAsc(QueueStatus status);
}
