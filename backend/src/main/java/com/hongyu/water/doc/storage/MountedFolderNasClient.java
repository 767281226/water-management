package com.hongyu.water.doc.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import com.hongyu.water.common.BizException;

/**
 * 以挂载目录方式访问网盘（app.nas.type = mount）。
 * <p>
 * 适用场景：NAS 的共享目录已挂载到服务器——Windows 可直接使用 \\NAS01\资料库 这样的 UNC 路径
 * （服务运行账号需有访问权限），Linux 可通过 mount -t cifs / nfs 挂载到 /mnt/nas。
 * 系统只读，不会修改网盘中的文件。
 */
public class MountedFolderNasClient implements NasClient {

    private final Path root;

    public MountedFolderNasClient(String root) {
        if (root == null || root.isBlank()) {
            throw new IllegalStateException("app.nas.type=mount 时必须配置 app.nas.root（NAS_ROOT）");
        }
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public String type() {
        return "mount";
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public String describe() {
        return "挂载目录：" + root;
    }

    @Override
    public List<NasEntry> list(int maxFiles) {
        if (!Files.isDirectory(root)) {
            throw new BizException("无法访问网盘目录：" + root + "，请检查路径和服务账号权限");
        }
        List<NasEntry> result = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return !dir.equals(root) && hidden(dir) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && !hidden(file)) {
                        if (result.size() >= maxFiles) {
                            throw new BizException("网盘文件超过 " + maxFiles + " 个，请确认根目录是否配置正确（可调整 NAS_MAX_FILES）");
                        }
                        String rel = root.relativize(file).toString().replace('\\', '/');
                        result.add(new NasEntry(rel, attrs.size(), attrs.lastModifiedTime().toInstant()));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }

    @Override
    public Resource open(String path) {
        Path file = root.resolve(path).normalize();
        if (!file.startsWith(root)) {
            throw new BizException("非法的文件路径");
        }
        if (!Files.isRegularFile(file)) {
            throw new BizException("网盘中的文件已不存在，请联系资料管理员");
        }
        return new FileSystemResource(file);
    }

    /** 跳过隐藏文件、Office 临时文件、系统文件 */
    private static boolean hidden(Path p) {
        String n = p.getFileName().toString();
        return n.startsWith(".") || n.startsWith("~$") || n.startsWith("@") || n.equalsIgnoreCase("Thumbs.db")
                || n.equalsIgnoreCase("desktop.ini") || n.equals("#recycle");
    }
}
