package com.hongyu.water.security;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * 令牌校验通过后的认证对象，principal 为 {@link CurrentUser}，权限点作为 authorities。
 */
public class UserAuthenticationToken extends AbstractAuthenticationToken {

    private final CurrentUser user;

    public UserAuthenticationToken(CurrentUser user, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.user = user;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public CurrentUser getPrincipal() {
        return user;
    }

    @Override
    public String getName() {
        return user.username();
    }
}
