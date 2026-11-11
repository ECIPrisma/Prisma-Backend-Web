package com.prisma.api.dto;

public class TotpSetupResponse {

    private final String secret;
    private final String otpauthUri;

    public TotpSetupResponse(String secret, String otpauthUri) {
        this.secret = secret;
        this.otpauthUri = otpauthUri;
    }

    public String getSecret() { return secret; }
    public String getOtpauthUri() { return otpauthUri; }
}
