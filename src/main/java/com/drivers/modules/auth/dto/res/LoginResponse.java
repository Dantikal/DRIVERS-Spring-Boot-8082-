package com.drivers.modules.auth.dto.res;

import com.drivers.modules.auth.dto.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record LoginResponse(
        @JsonProperty("access")
        String accessToken,
        @JsonProperty("refresh")
        String refreshToken,
        User user

) {}
