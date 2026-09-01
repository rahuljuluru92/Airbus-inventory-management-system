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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtUtil jwtUtil;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, authenticationManager, jwtUtil);
        ReflectionTestUtils.setField(authService, "jwtExpirationMs", 3600000L);
    }

    @Test
    void registerThrowsWhenUsernameTaken() {
        when(userRepository.existsByUsername("admin")).thenReturn(true);
        RegisterRequest request = new RegisterRequest();
        request.setUsername("admin");
        request.setPassword("password1");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    void registerEncodesPasswordAndIssuesTokens() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("password1");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode("password1")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtUtil.generateToken("newuser", "USER")).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken("newuser")).thenReturn("refresh-token");

        AuthResponse response = authService.register(request);

        assertThat(response.getToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getUsername()).isEqualTo("newuser");
        assertThat(response.getRole()).isEqualTo("USER");
        assertThat(response.getExpiresInMs()).isEqualTo(3600000L);
    }

    @Test
    void registerAlwaysCreatesUserRole() {
        // /api/auth/register is unauthenticated (permitAll). RegisterRequest has no role field
        // at all (removed deliberately) — this locks in that self-registration can never create
        // an ADMIN, regardless of what a caller puts in the request body.
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("password1");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(userCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request);

        assertThat(userCaptor.getValue().getRole()).isEqualTo("USER");
    }

    @Test
    void loginAuthenticatesAndIssuesTokens() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        User user = new User(1L, "admin", "hashed", "ADMIN");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken("admin", "ADMIN")).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken("admin")).thenReturn("refresh-token");

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("access-token");
        assertThat(response.getRole()).isEqualTo("ADMIN");
    }

    @Test
    void refreshRejectsAnAccessTokenPresentedAsRefreshToken() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("not-a-refresh-token");
        when(jwtUtil.isRefreshToken("not-a-refresh-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refreshRejectsWhenUserNoLongerExists() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("valid-refresh");
        when(jwtUtil.isRefreshToken("valid-refresh")).thenReturn(true);
        when(jwtUtil.extractUsername("valid-refresh")).thenReturn("ghost");
        when(jwtUtil.isTokenValid("valid-refresh", "ghost")).thenReturn(true);
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refreshIssuesNewTokenPairForValidRefreshToken() {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("valid-refresh");
        User user = new User(1L, "admin", "hashed", "ADMIN");

        when(jwtUtil.isRefreshToken("valid-refresh")).thenReturn(true);
        when(jwtUtil.extractUsername("valid-refresh")).thenReturn("admin");
        when(jwtUtil.isTokenValid("valid-refresh", "admin")).thenReturn(true);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken("admin", "ADMIN")).thenReturn("new-access-token");
        when(jwtUtil.generateRefreshToken("admin")).thenReturn("new-refresh-token");

        AuthResponse response = authService.refresh(request);

        assertThat(response.getToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
    }
}
