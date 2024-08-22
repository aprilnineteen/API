package com.cloudians.domain.auth.service;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import org.apache.http.auth.InvalidCredentialsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.cloudians.domain.auth.dto.request.UserAuthRequest;
import com.cloudians.domain.auth.entity.UserToken;
import com.cloudians.domain.auth.repository.UserAuthRepository;
import com.cloudians.domain.auth.repository.UserTokenRepository;

@Service
public class AuthTokenService {

    @Autowired
    private UserTokenRepository userTokenRepository;

    @Autowired
    private UserAuthRepository userAuthRepository;

    public Map<String, String> login(String email, String password) throws InvalidCredentialsException {
        if (!authenticateUser(email, password)) {
            throw new InvalidCredentialsException("이메일 또는 비밀번호가 잘못되었습니다.");
        }

        String accessToken = generateAccessToken(email);
        String refreshToken = generateRefreshToken(email);

        LocalDateTime accessTokenExpiresAt = LocalDateTime.now().plusMinutes(10); // 10분 후
        LocalDateTime refreshTokenExpiresAt = LocalDateTime.now().plusDays(30); // 30일 후

        saveToken(email, accessToken, "jwt", accessTokenExpiresAt);
        saveToken(email, refreshToken, "jwt rf", refreshTokenExpiresAt);

        return Map.of("accessToken", accessToken, "refreshToken", refreshToken);
    }

    private boolean authenticateUser(String email, String password) {
        Optional<UserAuthRequest> userAuthOptional = userAuthRepository.findByUserEmail(email);
        if (userAuthOptional.isPresent()) {
            return userAuthOptional.get().getPassword().equals(password);
        }
        return false;
    }

    private String generateAccessToken(String email) {
        return JWT.create()
                .withSubject("jwt token")
                .withExpiresAt(new Date(System.currentTimeMillis() + (1000L * 60 * 10))) // 10분 후 만료
                .withClaim("userEmail", email)
                .sign(Algorithm.HMAC512("jwt"));
    }

    private String generateRefreshToken(String email) {
        return JWT.create().withSubject("jwt rf token")
                .withExpiresAt(new Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 30))) // 30일 후 만료
                .withClaim("userEmail", email).sign(Algorithm.HMAC512("jwt"));
    }

    private void saveToken(String userEmail, String tokenValue, String tokenType, LocalDateTime expiresAt) {
        UserToken userToken = new UserToken();
        userToken.setUserEmail(userEmail);
        userToken.setTokenValue(tokenValue);
        userToken.setTokenType(tokenType);
        userToken.setCreatedAt(LocalDateTime.now());
        userToken.setUpdatedAt(expiresAt);

        userTokenRepository.save(userToken);
    }

    public Optional<UserToken> getUserToken(String email, String tokenType) {
        return userTokenRepository.findByUserEmailAndTokenType(email, tokenType);
    }

    public boolean isTokenExpired(LocalDateTime expiresAt) {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    public String refreshAccessToken(String refreshToken) {
        String userEmail = JwtUtil.extractEmail(refreshToken);
        Optional<UserToken> storedRefreshToken = getUserToken(userEmail, "jwt rf");

        if (storedRefreshToken.isPresent() && !isTokenExpired(storedRefreshToken.get().getUpdatedAt())) {
            String newAccessToken = generateAccessToken(userEmail);
            LocalDateTime newAccessTokenExpiresAt = LocalDateTime.now().plusMinutes(10);

            userTokenRepository.deleteByUserEmailAndTokenType(userEmail, "jwt"); // 기존 액세스 토큰 삭제
            saveToken(userEmail, newAccessToken, "jwt", newAccessTokenExpiresAt);

            return newAccessToken; // 새로운 액세스 토큰 반환
        } else {
            throw new RuntimeException("리프레시 토큰이 유효하지 않거나 만료되었습니다.");
        }
    }

    public String refreshRefreshToken(String accessToken) {
        String userEmail = JwtUtil.extractEmail(accessToken);
        Optional<UserToken> storedAccessToken = getUserToken(userEmail, "jwt");
        Optional<UserToken> storedRefreshToken = getUserToken(userEmail, "jwt rf");

        // 액세스 토큰이 존재하고 만료되지 않았을 경우
        if (storedAccessToken.isPresent() && !isTokenExpired(storedAccessToken.get().getUpdatedAt())) {
            // 리프레시 토큰이 존재하지 않거나 만료된 경우
            if (storedRefreshToken.isEmpty() || isTokenExpired(storedRefreshToken.get().getUpdatedAt())) {
                // 새로운 리프레시 토큰 생성
                String newRefreshToken = generateRefreshToken(userEmail);
                LocalDateTime newRefreshTokenExpiresAt = LocalDateTime.now().plusDays(30);
                saveToken(userEmail, newRefreshToken, "jwt rf", newRefreshTokenExpiresAt);
                return newRefreshToken; // 새로운 리프레시 토큰 반환
            } else {
                throw new RuntimeException("리프레시 토큰이 유효합니다.");
            }
        } else {
            throw new RuntimeException("액세스 토큰이 유효하지 않거나 만료되었습니다.");
        }
    }
}
