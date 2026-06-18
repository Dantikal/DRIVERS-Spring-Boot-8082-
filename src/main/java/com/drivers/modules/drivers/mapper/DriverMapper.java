package com.drivers.modules.drivers.mapper;

import com.drivers.modules.drivers.dto.DriverDto;
import com.drivers.modules.drivers.entity.Driver;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DriverMapper {
    DriverDto toDto(Driver driver);

}
