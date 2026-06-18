package com.drivers.modules.drivers.mapper;

import com.drivers.modules.drivers.dto.DriverDebtDto;
import com.drivers.modules.drivers.entity.DriverDebt;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DriverDebtMapper {
    DriverDebtDto toDto(DriverDebt driverDebt);
}
