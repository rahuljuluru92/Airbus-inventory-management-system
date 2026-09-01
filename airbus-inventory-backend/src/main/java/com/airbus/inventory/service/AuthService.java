package com.airbus.inventory.service;

import com.airbus.inventory.dto.AuthResponse;
import com.airbus.inventory.dto.LoginRequest;
import com.airbus.inventory.dto.RefreshRequest;
import com.airbus.inventory.dto.RegisterRequest;
import com.airbus.inventory.exception.DuplicateUsernameException;
import com.airbus.inventory.exception.InvalidTokenException;
import com.airbus.inventory.model.User;
import com.airbus.inventory.repository.UserRepository;
import com.airbus.inventory.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                        AuthenticationManager authenticationManager, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateUsernameException("Username '" + request.getUsername() + "' is already taken");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        // /api/auth/register is unauthenticated (permitAll) — always USER, never trust a
        // client-supplied role here, or anyone could self-register as ADMIN.
        user.setRole("USER");

        User saved = userRepository.save(user);
        return issueTokens(saved);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        User user = userRepository.findByUsername(request.getUsername()).orElseThrow();
        return issueTokens(user);
    }

    public AuthResponse refresh(RefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtUtil.isRefreshToken(refreshToken)) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        String username;
        try {
            username = jwtUtil.extractUsername(refreshToken);
        } catch (Exception ex) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        if (username == null || !jwtUtil.isTokenValid(refreshToken, username)) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired refresh token"));

        return issueTokens(user);
    }

    private AuthResponse issueTokens(User user) {
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());
        return new AuthResponse(token, refreshToken, user.getUsername(), user.getRole(), jwtExpirationMs);
    }
}
