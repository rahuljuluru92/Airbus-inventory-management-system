package com.airbus.inventory.exception;

import com.airbus.inventory.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFoundMapsTo404WithMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleNotFound(new ResourceNotFoundException("Product not found with id 5"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("Product not found with id 5");
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    void duplicateUsernameMapsTo409() {
        ResponseEntity<ErrorResponse> response =
                handler.handleDuplicateUsername(new DuplicateUsernameException("Username 'admin' is already taken"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).contains("already taken");
    }

    @Test
    void badCredentialsNeverLeaksWhichFieldWasWrong() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBadCredentials(new BadCredentialsException("Bad credentials"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid username or password");
    }

    @Test
    void invalidTokenMapsTo401() {
        ResponseEntity<ErrorResponse> response =
                handler.handleInvalidToken(new InvalidTokenException("Invalid or expired refresh token"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid or expired refresh token");
    }

    @Test
    void accessDeniedMapsTo403WithoutLeakingInternalDetail() {
        ResponseEntity<ErrorResponse> response =
                handler.handleAccessDenied(new AccessDeniedException("some internal detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessage()).isEqualTo("Access denied");
    }

    @Test
    void unhandledExceptionMapsTo500WithoutExposingStackTrace() {
        ResponseEntity<ErrorResponse> response =
                handler.handleGeneric(new RuntimeException("some sensitive internal detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().getMessage()).doesNotContain("sensitive internal detail");
    }
}
