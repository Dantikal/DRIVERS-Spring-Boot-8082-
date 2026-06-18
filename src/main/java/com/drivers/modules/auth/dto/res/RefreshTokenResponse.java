package com.drivers.modules.auth.dto.res;

import lombok.Builder;

@Builder
public record RefreshTokenResponse(
        String access
) {}
