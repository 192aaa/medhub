package com.sdas.server.identity.dto;

/**
 * 端点 5 响应 data；新续期凭证只通过 HttpOnly Cookie 下发。
 *
 * @param accessToken                新 JWT
 * @param accessTokenExpiresInSeconds 有效期 7200
 */
public record RefreshTokenResponse(
        String accessToken,
        long accessTokenExpiresInSeconds) {
}
