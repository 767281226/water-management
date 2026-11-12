package com.hongyu.water.security;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.hongyu.water.config.AppProperties;
import com.hongyu.water.system.user.SysUser;

/**
 * 签发 JWT。令牌中携带用户 ID 与令牌版本（ver），用户修改密码 / 被停用后版本递增，旧令牌立即失效。
 */
@Service
public class JwtService {

    public static final String CLAIM_VERSION = "ver";

    private final JwtEncoder encoder;
    private final AppProperties props;

    public JwtService(JwtEncoder encoder, AppProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public String issue(SysUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("water-management")
                .issuedAt(now)
                .expiresAt(now.plus(props.jwt().expire()))
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim(CLAIM_VERSION, user.getTokenVersion())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long expireSeconds() {
        return props.jwt().expire().toSeconds();
    }
}
