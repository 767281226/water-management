package com.hongyu.water.doc;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hongyu.water.config.AppProperties;
import com.hongyu.water.doc.DocDtos.StorageInfo;
import com.hongyu.water.doc.DocDtos.SyncResult;
import com.hongyu.water.doc.storage.NasClient;
import com.hongyu.water.doc.storage.NasClient.NasEntry;
import com.hongyu.water.doc.storage.StorageType;
import com.hongyu.water.security.CurrentUser;
import com.hongyu.water.system.config.ConfigService;

/**
 * 网盘同步（需求 5.2 方案 A：直接读取网盘目录，系统只建立索引并控制权限）。
 * <ul>
 * <li>一级目录 → 一级分类，二级目录 → 二级分类（更深的目录归入二级分类），根目录下的文件归入"未分类"；</li>
 * <li>新文件：建立索引，默认"内部 / 需审批"，资料管理员可再调整；文件名以标准号开头时自动识别编号和年份；</li>
 * <li>已有文件：更新大小和修改时间；网盘中已删除的文件自动下架，重新出现时自动恢复。</li>
 * </ul>
 */
@Service
public class NasSyncService {

    /** 识别文件名开头的标准号，如 "SL 252-2017 水利水电工程等级划分及洪水标准" */
    private static final Pattern STANDARD = Pattern.compile(
            "^((?:GB|SL|DL|DB|JTG|CJJ|NB|HJ|JGJ|SY|TB)(?:\\s*\\d+)?(?:/T)?\\s*[\\d.]+\\s*[-—]\\s*(\\d{4}))\\s*[_\\-—\\s]*(.*)$",
            Pattern.CASE_INSENSITIVE);

    private final NasClient nas;
    private final DocRepository docRepo;
    private final DocCategoryService categoryService;
    private final DocLogService logService;
    private final ConfigService configService;
    private final AppProperties props;

    public NasSyncService(NasClient nas, DocRepository docRepo, DocCategoryService categoryService, DocLogService logService,
            ConfigService configService, AppProperties props) {
        this.nas = nas;
        this.docRepo = docRepo;
        this.categoryService = categoryService;
        this.logService = logService;
        this.configService = configService;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public StorageInfo info() {
        long nasDocs = docRepo.findByStorageTypeAndDeletedFalse(StorageType.NAS).size();
        long localDocs = docRepo.findByStorageTypeAndDeletedFalse(StorageType.LOCAL).size();
        return new StorageInfo(nas.type(), nas.enabled(), nas.describe(), configService.get("doc.nas.last-sync", ""),
                nasDocs, localDocs);
    }

    @Transactional
    public SyncResult sync(CurrentUser cu) {
        long start = System.currentTimeMillis();
        int max = props.nas() != null && props.nas().maxFiles() != null ? props.nas().maxFiles() : 20000;
        List<NasEntry> entries = nas.list(max);

        Map<String, Doc> existing = new HashMap<>();
        docRepo.findByStorageTypeAndDeletedFalse(StorageType.NAS).forEach(d -> existing.put(d.getStorageKey(), d));
        Map<String, Long> categoryCache = new HashMap<>();
        Set<String> seen = new HashSet<>();
        int added = 0, updated = 0, restored = 0, missing = 0;

        for (NasEntry e : entries) {
            String key = e.path();
            if (key.length() > 500) {
                continue;
            }
            seen.add(key);
            LocalDateTime modified = LocalDateTime.ofInstant(e.modified(), ZoneId.systemDefault()).withNano(0);
            Doc d = existing.get(key);
            if (d == null) {
                docRepo.save(newDoc(e, key, modified, categoryOf(key, categoryCache), cu));
                added++;
                continue;
            }
            boolean changed = d.getFileSize() != e.size() || !modified.equals(d.getSourceModifiedAt());
            if (changed) {
                d.setFileSize(e.size());
                d.setSourceModifiedAt(modified);
                updated++;
            }
            if (d.isSourceMissing()) {
                d.setSourceMissing(false);
                d.setStatus(Doc.Status.ON);
                restored++;
            }
        }
        for (Doc d : existing.values()) {
            if (!seen.contains(d.getStorageKey()) && !d.isSourceMissing()) {
                d.setSourceMissing(true);
                d.setStatus(Doc.Status.OFF);
                missing++;
            }
        }
        long cost = System.currentTimeMillis() - start;
        configService.setInternal("doc.nas.last-sync", LocalDateTime.now().withNano(0).toString().replace('T', ' '));
        SyncResult result = new SyncResult(entries.size(), added, updated, missing, restored, cost);
        logService.log(null, cu.id(), "SYNC", null,
                "扫描 %d 个文件，新增 %d，更新 %d，下架 %d，恢复 %d".formatted(entries.size(), added, updated, missing, restored));
        return result;
    }

    private Long categoryOf(String key, Map<String, Long> cache) {
        String[] parts = key.split("/");
        if (parts.length == 1) {
            return cache.computeIfAbsent("", k -> categoryService.findOrCreate(null, "未分类").getId());
        }
        Long top = cache.computeIfAbsent(parts[0], k -> categoryService.findOrCreate(null, parts[0]).getId());
        if (parts.length == 2) {
            return top;
        }
        return cache.computeIfAbsent(parts[0] + "/" + parts[1], k -> categoryService.findOrCreate(top, parts[1]).getId());
    }

    private Doc newDoc(NasEntry e, String key, LocalDateTime modified, Long categoryId, CurrentUser cu) {
        String fileName = key.substring(key.lastIndexOf('/') + 1);
        String ext = DocService.extOf(fileName);
        String base = ext.isEmpty() ? fileName : fileName.substring(0, fileName.length() - ext.length() - 1);
        Doc d = new Doc();
        Matcher m = STANDARD.matcher(base);
        if (m.matches()) {
            d.setCode(m.group(1).replaceAll("\\s+", " ").trim());
            d.setYear(Integer.parseInt(m.group(2)));
            d.setName(m.group(3).isBlank() ? base : m.group(3).trim());
        } else {
            d.setName(base);
        }
        if (d.getName().length() > 200) {
            d.setName(d.getName().substring(0, 200));
        }
        d.setCategoryId(categoryId);
        d.setLevel(Doc.Level.INTERNAL);
        d.setNeedApproval(true);
        d.setStorageType(StorageType.NAS);
        d.setStorageKey(key);
        d.setFileName(fileName.length() > 255 ? fileName.substring(fileName.length() - 255) : fileName);
        d.setFileExt(ext.length() > 16 ? ext.substring(0, 16) : ext);
        d.setFileSize(e.size());
        d.setSourceModifiedAt(modified);
        d.setUploaderId(cu.id());
        return d;
    }
}
