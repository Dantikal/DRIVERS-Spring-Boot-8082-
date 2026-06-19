package com.drivers.modules.drivers.repository.specification;

import com.drivers.modules.drivers.entity.Driver;
import com.drivers.modules.drivers.entity.DriverStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class DriverSpecification {
    public static Specification<Driver> hasStatus(DriverStatus status){
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }
    public static Specification<Driver> hasWarehouseId(UUID warehouseId){
        return (root, query, cb) -> warehouseId == null ? null : cb.equal(root.get("warehouseId"), warehouseId);
    }
    public static Specification<Driver> search(String search) {
        return (root, query, cb) -> {

            if (search == null || search.isBlank()) {
                return null;
            }

            String pattern = "%" + search.toLowerCase() + "%";

            return cb.or(
                    cb.like(cb.lower(root.get("fullName")), pattern),
                    cb.like(cb.lower(root.get("phone")), pattern),
                    cb.like(cb.lower(root.get("carNumber")), pattern)
            );
        };
    }
}
