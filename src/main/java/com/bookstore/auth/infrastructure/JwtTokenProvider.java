package com.bookstore.auth.infrastructure;

import com.bookstore.auth.application.port.TokenProvider;
import com.bookstore.user.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class JwtTokenProvider implements TokenProvider {
    public static final String $_APP_JWT_EXPIRATION_MINUTES_OR_15 = "${app.jwt.expiration-minutes:15}";
    public static final String BOOKSTORE = "bookstore";
    public static final String ROLE = "role";

    private final JwtEncoder jwtEncoder;
    private final Duration tokenLifetime;

    public JwtTokenProvider(
            JwtEncoder jwtEncoder,
            @Value($_APP_JWT_EXPIRATION_MINUTES_OR_15) long expirationMinutes) {
        this.jwtEncoder = jwtEncoder;
        this.tokenLifetime = Duration.ofMinutes(expirationMinutes);
    }

    @Override
    public String generateAccessToken(User user) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(BOOKSTORE)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(tokenLifetime))
                .claim(ROLE, user.getRole().name())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
