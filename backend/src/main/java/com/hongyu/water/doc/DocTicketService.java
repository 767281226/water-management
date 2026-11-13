package com.hongyu.water.doc;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.hongyu.water.common.BizException;

/**
 * 文件访问凭证：校验权限后签发短时有效的随机凭证，浏览器凭链接直接预览 / 下载
 * （无需携带登录令牌，大文件不经过前端内存，支持断点续传与 PDF 分段加载）。
 * <p>
 * 凭证保存在内存中，单实例部署可用；多实例部署时需改为 Redis 等共享存储。
 */
@Service
public class DocTicketService {

    static final Duration TTL = Duration.ofMinutes(5);

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    /**
     * @param download  true 下载 / false 预览
     * @param requestId 下载所使用的申请（免审资料为 null）
     */
    public record Ticket(Long docId, Long userId, String userName, String employeeNo, boolean download, Long requestId,
            Instant expiresAt, AtomicBoolean used) {
    }

    public String issue(Long docId, Long userId, String userName, String employeeNo, boolean download, Long requestId) {
        cleanup();
        byte[] b = new byte[24];
        random.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        tickets.put(token, new Ticket(docId, userId, userName, employeeNo, download, requestId, Instant.now().plus(TTL),
                new AtomicBoolean(false)));
        return token;
    }

    public Ticket get(String token) {
        Ticket t = token == null ? null : tickets.get(token);
        if (t == null || t.expiresAt().isBefore(Instant.now())) {
            throw new BizException(HttpStatus.GONE, "链接已失效，请返回系统重新打开");
        }
        return t;
    }

    private void cleanup() {
        Instant now = Instant.now();
        tickets.values().removeIf(t -> t.expiresAt().isBefore(now));
    }
}
