package com.hongyu.water.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用自定义配置（application.yml 中的 app.*）。
 */
@ConfigurationProperties("app")
public record AppProperties(Jwt jwt, Cors cors, Storage storage, Nas nas) {

    public record Jwt(String secret, Duration expire) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    /** 本地文件存储（系统上传的资料） */
    public record Storage(String localDir) {
    }

    /**
     * 内部网盘对接。
     *
     * @param type     none 未对接 / mount 以挂载目录方式访问（SMB 共享、NAS 挂载到服务器）；
     *                 webdav、api 等方式预留，按网盘产品实现 NasClient 即可
     * @param root     网盘根目录（mount：如 \\NAS01\资料库 或 /mnt/nas/资料库）
     * @param maxFiles 单次同步最多处理的文件数（防止误配到超大目录）
     */
    public record Nas(String type, String root, Integer maxFiles) {
    }
}
