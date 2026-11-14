package com.prisma.api.service;

import com.prisma.api.dto.AuthResponse;
import com.prisma.api.dto.LoginRequest;

public interface AuthService {
    AuthResponse authenticate(LoginRequest loginRequest);
}
