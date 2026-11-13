package com.hongyu.water.doc.storage;

import java.time.Instant;
import java.util.List;

import org.springframework.core.io.Resource;

/**
 * 内部网盘适配器（网盘对接的扩展点）。
 * <p>
 * 已实现：
 * <ul>
 * <li>{@code none}：未对接（{@link DisabledNasClient}）；</li>
 * <li>{@code mount}：网盘以共享目录 / 挂载点方式访问（{@link MountedFolderNasClient}），适用于群晖、威联通、
 * Windows 共享等大多数 NAS。</li>
 * </ul>
 * 网盘只能通过 WebDAV 或厂商 API 访问时，新增一个实现类并在 {@link NasConfig} 中注册即可，业务代码无需修改。
 */
public interface NasClient {

    /** 适配器类型：none / mount / webdav / api ... */
    String type();

    boolean enabled();

    /** 展示给管理员的说明（如根目录），不得包含密码等敏感信息 */
    String describe();

    /**
     * 列出网盘中的全部文件（递归）。
     *
     * @param maxFiles 最多返回的文件数，超过时抛出异常提示管理员检查根目录配置
     */
    List<NasEntry> list(int maxFiles);

    /** 打开网盘文件；path 为 {@link NasEntry#path()} 返回的相对路径 */
    Resource open(String path);

    /**
     * 网盘中的文件。
     *
     * @param path     相对网盘根目录的路径，统一使用 / 分隔，如 "相关规范/水利行业标准/SL 252-2017 水利水电工程等级划分及洪水标准.pdf"
     * @param size     字节数
     * @param modified 最后修改时间
     */
    record NasEntry(String path, long size, Instant modified) {
    }
}
