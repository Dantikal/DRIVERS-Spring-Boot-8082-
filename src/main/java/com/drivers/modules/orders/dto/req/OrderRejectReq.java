package com.drivers.modules.orders.dto.req;

import lombok.Builder;

@Builder
public record OrderRejectReq(
        String comment
) {}
