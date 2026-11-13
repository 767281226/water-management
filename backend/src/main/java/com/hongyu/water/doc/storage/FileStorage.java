package com.hongyu.water.doc.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.hongyu.water.common.BizException;
import com.hongyu.water.config.AppProperties;

/**
 * 资料文件读写：本地存储（系统上传）+ 网盘（只读）。
 */
@Component
public class FileStorage {

    private final Path localRoot;
    private final NasClient nas;

    public FileStorage(AppProperties props, NasClient nas) {
        String dir = props.storage() != null && props.storage().localDir() != null ? props.storage().localDir() : "./data/docs";
        this.localRoot = Path.of(dir).toAbsolutePath().normalize();
        this.nas = nas;
    }

    /** 保存上传文件，返回本地存储键（年/月/随机文件名.扩展名） */
    public String saveLocal(InputStream in, String ext) {
        LocalDate d = LocalDate.now();
        String key = "%d/%02d/%s%s".formatted(d.getYear(), d.getMonthValue(), UUID.randomUUID().toString().replace("-", ""),
                ext == null || ext.isBlank() ? "" : "." + ext);
        Path target = localPath(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("保存文件失败", e);
        }
        return key;
    }

    public Resource load(StorageType type, String key) {
        if (type == StorageType.NAS) {
            return nas.open(key);
        }
        Path p = localPath(key);
        if (!Files.isRegularFile(p)) {
            throw new BizException("文件已不存在，请联系资料管理员");
        }
        return new FileSystemResource(p);
    }

    public void deleteLocal(String key) {
        try {
            Files.deleteIfExists(localPath(key));
        } catch (IOException ignored) {
            // 删除失败不影响业务，文件可由运维清理
        }
    }

    private Path localPath(String key) {
        Path p = localRoot.resolve(key).normalize();
        if (!p.startsWith(localRoot)) {
            throw new BizException("非法的文件路径");
        }
        return p;
    }
}
