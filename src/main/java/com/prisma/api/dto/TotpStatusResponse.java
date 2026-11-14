package com.prisma.api.dto;

public class TotpStatusResponse {

    private final boolean enabled;

    public TotpStatusResponse(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() { return enabled; }
}
