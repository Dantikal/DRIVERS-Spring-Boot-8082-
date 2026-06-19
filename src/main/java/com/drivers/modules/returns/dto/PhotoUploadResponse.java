package com.drivers.modules.returns.dto;

import lombok.Builder;

@Builder
public record PhotoUploadResponse(
        String photoUrl
) {}
