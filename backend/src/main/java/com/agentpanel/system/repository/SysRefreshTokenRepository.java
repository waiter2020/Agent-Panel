package com.agentpanel.system.repository;

import com.agentpanel.system.entity.SysRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SysRefreshTokenRepository extends JpaRepository<SysRefreshToken, Long> {
    Optional<SysRefreshToken> findByTokenAndRevokedFalse(String token);
}
