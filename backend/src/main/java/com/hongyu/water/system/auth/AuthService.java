package com.hongyu.water.system.auth;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hongyu.water.common.BizException;
import com.hongyu.water.common.PasswordPolicy;
import com.hongyu.water.security.CurrentUser;
import com.hongyu.water.security.JwtService;
import com.hongyu.water.system.auth.AuthDtos.ChangePasswordRequest;
import com.hongyu.water.system.auth.AuthDtos.LoginRequest;
import com.hongyu.water.system.auth.AuthDtos.LoginResponse;
import com.hongyu.water.system.auth.AuthDtos.MeResponse;
import com.hongyu.water.system.auth.AuthDtos.ProfileRequest;
import com.hongyu.water.system.config.ConfigService;
import com.hongyu.water.system.dept.DeptService;
import com.hongyu.water.system.log.LogService;
import com.hongyu.water.system.role.SysRole;
import com.hongyu.water.system.user.SysUser;
import com.hongyu.water.system.user.SysUserRepository;
import com.hongyu.water.system.user.UserStatus;

/**
 * 登录、当前用户信息、修改密码。
 */
@Service
public class AuthService {

    private static final String BAD_CREDENTIALS = "账号或密码错误";

    private final SysUserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final CurrentUserLoader loader;
    private final ConfigService configService;
    private final DeptService deptService;
    private final LogService logService;

    public AuthService(SysUserRepository userRepo, PasswordEncoder encoder, JwtService jwtService,
            CurrentUserLoader loader, ConfigService configService, DeptService deptService, LogService logService) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.loader = loader;
        this.configService = configService;
        this.deptService = deptService;
        this.logService = logService;
    }

    /**
     * 登录。连续输错密码达到上限后锁定账号（需求 F-105）。
     * 失败计数需要在抛出业务异常时仍然提交，因此不对 BizException 回滚。
     */
    @Transactional(noRollbackFor = BizException.class)
    public LoginResponse login(LoginRequest req, String ip, String userAgent) {
        String account = req.username().trim();
        List<SysUser> found = userRepo.findForLogin(account);
        if (found.size() != 1) {
            logService.saveLogin(account, false, BAD_CREDENTIALS, ip, userAgent);
            throw new BizException(BAD_CREDENTIALS);
        }
        SysUser user = found.get(0);
        LocalDateTime now = LocalDateTime.now().withNano(0);

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            long minutes = Math.max(1, Duration.between(now, user.getLockedUntil()).toMinutes());
            String msg = "密码错误次数过多，账号已锁定，请 " + minutes + " 分钟后再试";
            logService.saveLogin(account, false, msg, ip, userAgent);
            throw new BizException(msg);
        }
        if (user.getStatus() != UserStatus.ENABLED) {
            logService.saveLogin(account, false, "账号已停用", ip, userAgent);
            throw new BizException("账号已停用，请联系管理员");
        }
        if (!encoder.matches(req.password(), user.getPassword())) {
            int maxFail = configService.getInt("security.login.max-fail", 5);
            int fails = user.getLoginFailCount() + 1;
            String msg;
            if (fails >= maxFail) {
                int lockMinutes = configService.getInt("security.login.lock-minutes", 15);
                user.setLockedUntil(now.plusMinutes(lockMinutes));
                user.setLoginFailCount(0);
                msg = "密码错误次数过多，账号已锁定 " + lockMinutes + " 分钟";
            } else {
                user.setLoginFailCount(fails);
                msg = BAD_CREDENTIALS + "，再输错 " + (maxFail - fails) + " 次将锁定账号";
            }
            logService.saveLogin(account, false, msg, ip, userAgent);
            throw new BizException(msg);
        }

        user.setLoginFailCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        user.setLastLoginIp(ip);
        logService.saveLogin(user.getUsername(), true, "登录成功", ip, userAgent);
        return new LoginResponse(jwtService.issue(user), jwtService.expireSeconds(), user.isMustChangePwd());
    }

    @Transactional(readOnly = true)
    public MeResponse me(CurrentUser cu) {
        SysUser u = mustGet(cu.id());
        List<String> roles = u.getRoles().stream().sorted((a, b) -> Integer.compare(a.getSort(), b.getSort()))
                .map(SysRole::getName).toList();
        return new MeResponse(u.getId(), u.getUsername(), u.getRealName(), u.getEmployeeNo(), u.getDeptId(),
                deptService.nameOf(u.getDeptId()), u.getTitle(), u.getPhone(), u.getEmail(), cu.superAdmin(),
                cu.dataScope(), roles, cu.permissions(), u.isMustChangePwd(), u.getLastLoginAt());
    }

    /** 修改密码后令牌版本递增，返回新令牌，其他设备上的登录随即失效。 */
    @Transactional
    public LoginResponse changePassword(CurrentUser cu, ChangePasswordRequest req) {
        SysUser u = mustGet(cu.id());
        if (!encoder.matches(req.oldPassword(), u.getPassword())) {
            throw new BizException("原密码不正确");
        }
        PasswordPolicy.check(req.newPassword());
        if (encoder.matches(req.newPassword(), u.getPassword())) {
            throw new BizException("新密码不能与原密码相同");
        }
        u.setPassword(encoder.encode(req.newPassword()));
        u.setMustChangePwd(false);
        u.revokeTokens();
        userRepo.flush();
        return new LoginResponse(jwtService.issue(u), jwtService.expireSeconds(), false);
    }

    @Transactional
    public void updateProfile(CurrentUser cu, ProfileRequest req) {
        SysUser u = mustGet(cu.id());
        u.setPhone(blankToNull(req.phone()));
        u.setEmail(blankToNull(req.email()));
    }

    @Transactional(readOnly = true)
    public CurrentUser reload(CurrentUser cu) {
        return loader.toCurrentUser(mustGet(cu.id()));
    }

    private SysUser mustGet(Long id) {
        return userRepo.findWithRolesByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
