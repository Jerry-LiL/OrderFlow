package com.orderflow.service;

import com.orderflow.dto.request.LoginRequest;
import com.orderflow.dto.request.RegisterRequest;
import com.orderflow.dto.response.AuthResponse;
import com.orderflow.entity.Role;
import com.orderflow.entity.User;
import com.orderflow.exception.ConflictException;
import com.orderflow.exception.UnauthorizedException;
import com.orderflow.repository.UserRepository;
import com.orderflow.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, tokenProvider);
    }

    @Test
    void register_Success() {
        RegisterRequest request = new RegisterRequest("johndoe", "john@example.com", "secret123");

        when(userRepository.existsByUsername("johndoe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");

        User savedUser = new User("johndoe", "john@example.com", "encoded-password", Role.ROLE_CUSTOMER);
        savedUser.setId(10L);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(tokenProvider.generateToken(10L, "johndoe", "john@example.com", "ROLE_CUSTOMER")).thenReturn("mock-jwt-token");

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("mock-jwt-token");
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.username()).isEqualTo("johndoe");
        assertThat(response.role()).isEqualTo("ROLE_CUSTOMER");

        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_DuplicateUsername_ThrowsConflictException() {
        RegisterRequest request = new RegisterRequest("johndoe", "john@example.com", "secret123");
        when(userRepository.existsByUsername("johndoe")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Username is already taken");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_DuplicateEmail_ThrowsConflictException() {
        RegisterRequest request = new RegisterRequest("johndoe", "john@example.com", "secret123");
        when(userRepository.existsByUsername("johndoe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email is already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_Success() {
        LoginRequest request = new LoginRequest("johndoe", "secret123");

        User user = new User("johndoe", "john@example.com", "encoded-hash", Role.ROLE_CUSTOMER);
        user.setId(5L);

        when(userRepository.findByUsername("johndoe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "encoded-hash")).thenReturn(true);
        when(tokenProvider.generateToken(5L, "johndoe", "john@example.com", "ROLE_CUSTOMER")).thenReturn("valid-jwt");

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("valid-jwt");
        assertThat(response.username()).isEqualTo("johndoe");
    }

    @Test
    void login_InvalidPassword_ThrowsUnauthorizedException() {
        LoginRequest request = new LoginRequest("johndoe", "wrong-password");

        User user = new User("johndoe", "john@example.com", "encoded-hash", Role.ROLE_CUSTOMER);
        when(userRepository.findByUsername("johndoe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    void login_UserNotFound_ThrowsUnauthorizedException() {
        LoginRequest request = new LoginRequest("unknown", "secret");
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class);
    }
}
