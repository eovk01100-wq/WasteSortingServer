package com.jiwoo.wastesorting_server.auth;

/** POST /auth/device 응답. 토큰 원문은 이때 한 번만 보낸다 */
public record DeviceTokenResponse(String token) {
}
