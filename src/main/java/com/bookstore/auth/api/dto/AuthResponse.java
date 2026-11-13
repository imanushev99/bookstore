package com.bookstore.auth.api.dto;

/**
 * No refresh token currently to keep it simple but can be extended in the future.
 * JWT access tokens are with a lifetime of 15 minutes.
 */
public class AuthResponse {
    private String accessToken;
    private TokenType tokenType;
    private long expiresIn;

    public AuthResponse() {
    }

    public AuthResponse(String accessToken, TokenType tokenType, long expiresIn) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public TokenType getTokenType() {
        return tokenType;
    }

    public void setTokenType(TokenType tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }
}
