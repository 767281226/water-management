package com.hongyu.water.system.log;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

import com.hongyu.water.common.BizException;
import com.hongyu.water.common.WebUtils;
import com.hongyu.water.security.CurrentUser;
import com.hongyu.water.security.SecurityUtils;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * 操作日志切面：记录 {@link OperLog} 标注的方法。参数中的密码字段会被脱敏。
 */
@Aspect
@Component
public class OperLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperLogAspect.class);
    private static final Pattern SECRET = Pattern.compile(
            "(\"(?:password|oldPassword|newPassword|token|configValue)\"\\s*:\\s*)\"(?:[^\"\\\\]|\\\\.)*\"");

    private final LogService logService;
    private final JsonMapper jsonMapper;

    public OperLogAspect(LogService logService, JsonMapper jsonMapper) {
        this.logService = logService;
        this.jsonMapper = jsonMapper;
    }

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint pjp, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        CurrentUser user = SecurityUtils.currentOrNull();
        HttpServletRequest req = WebUtils.currentRequest();
        String params = serializeArgs(pjp);
        try {
            Object result = pjp.proceed();
            save(operLog, user, req, params, true, null, start);
            return result;
        } catch (Throwable t) {
            String msg = t instanceof BizException ? t.getMessage() : t.getClass().getSimpleName() + ": " + t.getMessage();
            save(operLog, user, req, params, false, msg, start);
            throw t;
        }
    }

    private void save(OperLog ann, CurrentUser user, HttpServletRequest req, String params, boolean ok, String err,
            long start) {
        try {
            SysOperLog l = new SysOperLog();
            if (user != null) {
                l.setUserId(user.id());
                l.setUsername(user.username());
                l.setRealName(user.realName());
            }
            l.setModule(ann.module());
            l.setAction(ann.action());
            if (req != null) {
                l.setMethod(req.getMethod());
                l.setUri(WebUtils.truncate(req.getRequestURI(), 255));
                l.setIp(WebUtils.clientIp(req));
            }
            l.setParams(WebUtils.truncate(params, 2000));
            l.setSuccess(ok);
            l.setErrorMsg(WebUtils.truncate(err, 500));
            l.setCostMs(System.currentTimeMillis() - start);
            logService.saveOper(l);
        } catch (RuntimeException e) {
            log.warn("写入操作日志失败", e);
        }
    }

    private String serializeArgs(ProceedingJoinPoint pjp) {
        try {
            String[] names = ((MethodSignature) pjp.getSignature()).getParameterNames();
            Object[] args = pjp.getArgs();
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < args.length; i++) {
                Object a = args[i];
                if (a instanceof ServletRequest || a instanceof ServletResponse || a instanceof BindingResult) {
                    continue;
                }
                map.put(names != null && i < names.length ? names[i] : "arg" + i,
                        a instanceof MultipartFile f ? f.getOriginalFilename() : a);
            }
            if (map.isEmpty()) {
                return null;
            }
            return SECRET.matcher(jsonMapper.writeValueAsString(map)).replaceAll("$1\"******\"");
        } catch (RuntimeException e) {
            return null;
        }
    }
}
