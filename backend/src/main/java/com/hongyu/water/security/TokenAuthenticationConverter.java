package com.hongyu.water.security;

import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import com.hongyu.water.system.auth.CurrentUserLoader;

/**
 * 把 JWT 转换为 {@link UserAuthenticationToken}：每次请求从数据库加载用户状态与权限，
 * 保证停用账号、调整权限后立即生效。
 */
@Component
public class TokenAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final CurrentUserLoader loader;

    public TokenAuthenticationConverter(CurrentUserLoader loader) {
        this.loader = loader;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new InvalidBearerTokenException("无效的令牌");
        }
        Object ver = jwt.getClaim(JwtService.CLAIM_VERSION);
        int version = ver instanceof Number n ? n.intValue() : -1;
        CurrentUser user = loader.load(userId, version)
                .orElseThrow(() -> new InvalidBearerTokenException("登录已失效，请重新登录"));
        List<SimpleGrantedAuthority> authorities = user.permissions().stream().map(SimpleGrantedAuthority::new).toList();
        return new UserAuthenticationToken(user, authorities);
    }
}
