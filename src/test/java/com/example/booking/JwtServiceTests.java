package com.example.booking;

import com.example.booking.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTests {

    private static final String SECRET =
            "ThisIsATestJwtSecretKeyThatIsLongEnoughForHS256Signing1234567890";

    @Test
    void generatedTokenContainsUsernameAndValidatesForSameUser() {
        JwtService jwtService = new JwtService(SECRET, 60_000);
        UserDetails user = User.withUsername("user").password("ignored").roles("USER").build();

        String token = jwtService.generateToken(user);

        assertEquals("user", jwtService.extractUsername(token));
        assertTrue(jwtService.isTokenValid(token, user));
    }

    @Test
    void tokenIsRejectedForDifferentUserAndInvalidTokenIsRejected() {
        JwtService jwtService = new JwtService(SECRET, 60_000);
        UserDetails user = User.withUsername("user").password("ignored").roles("USER").build();
        UserDetails other = User.withUsername("other").password("ignored").roles("USER").build();

        String token = jwtService.generateToken(user);

        assertFalse(jwtService.isTokenValid(token, other));
        assertFalse(jwtService.isTokenValid("not-a-jwt", user));
    }
}
