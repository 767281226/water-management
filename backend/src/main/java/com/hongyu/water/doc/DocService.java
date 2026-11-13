package com.hongyu.water.doc;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.hongyu.water.common.BizException;
import com.hongyu.water.common.PageQuery;
import com.hongyu.water.common.PageResult;
import com.hongyu.water.doc.DocDtos.DocAccess;
import com.hongyu.water.doc.DocDtos.DocForm;
import com.hongyu.water.doc.DocDtos.DocQuery;
import com.hongyu.water.doc.DocDtos.DocVO;
import com.hongyu.water.doc.storage.FileStorage;
import com.hongyu.water.doc.storage.StorageType;
import com.hongyu.water.security.CurrentUser;
import com.hongyu.water.system.config.ConfigService;
import com.hongyu.water.system.user.SysUser;
import com.hongyu.water.system.user.SysUserRepository;

import jakarta.persistence.criteria.Predicate;

/**
 * 资料查询与维护（需求 3.4）。
 * <ul>
 * <li>浏览、搜索、在线预览对全体员工开放；下载需要审批（标记为"免审"的资料可直接下载）；</li>
 * <li>资料管理员（doc:manage）可上传、修改、更新文件、上下架、删除；网盘资料由同步维护文件本身。</li>
 * </ul>
 */
@Service
public class DocService {

    static final String PERM_MANAGE = "doc:manage";

    /** 允许上传的文件类型 */
    static final Set<String> ALLOWED_EXT = Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "wps", "et", "txt",
            "dwg", "dxf", "zip", "rar", "7z", "jpg", "jpeg", "png", "gif", "bmp", "webp", "tif", "tiff");
    /** 浏览器可直接在线预览的类型（Office 文档预览需部署文档转换服务，预留） */
    static final Set<String> PREVIEW_EXT = Set.of("pdf", "txt", "jpg", "jpeg", "png", "gif", "bmp", "webp");

    private final DocRepository repo;
    private final DocRequestRepository requestRepo;
    private final DocCategoryService categoryService;
    private final FileStorage storage;
    private final DocLogService logService;
    private final SysUserRepository userRepo;
    private final ConfigService configService;

    public DocService(DocRepository repo, DocRequestRepository requestRepo, DocCategoryService categoryService,
            FileStorage storage, DocLogService logService, SysUserRepository userRepo, ConfigService configService) {
        this.repo = repo;
        this.requestRepo = requestRepo;
        this.categoryService = categoryService;
        this.storage = storage;
        this.logService = logService;
        this.userRepo = userRepo;
        this.configService = configService;
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public PageResult<DocVO> search(DocQuery q, CurrentUser cu) {
        boolean manage = Boolean.TRUE.equals(q.manage()) && has(cu, PERM_MANAGE);
        Set<Long> cats = q.categoryId() == null ? null : categoryService.selfAndChildren(q.categoryId());
        Specification<Doc> spec = (root, cq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isFalse(root.get("deleted")));
            if (!manage) {
                ps.add(cb.equal(root.get("status"), Doc.Status.ON));
            } else if (q.status() != null) {
                ps.add(cb.equal(root.get("status"), q.status()));
            }
            if (q.keyword() != null && !q.keyword().isBlank()) {
                String like = "%" + q.keyword().trim().toLowerCase(Locale.ROOT) + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("fileName")), like)));
            }
            if (cats != null) {
                ps.add(root.get("categoryId").in(cats));
            }
            if (q.level() != null) {
                ps.add(cb.equal(root.get("level"), q.level()));
            }
            if (q.year() != null) {
                ps.add(cb.equal(root.get("year"), q.year()));
            }
            if (manage && q.storageType() != null) {
                ps.add(cb.equal(root.get("storageType"), q.storageType()));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        var page = repo.findAll(spec, PageQuery.of(q.page(), q.size(), Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, DocAccess> access = access(cu, page.getContent());
        Map<Long, String> paths = categoryService.paths();
        Map<Long, String> uploaders = uploaderNames(page.getContent());
        return PageResult.of(page, d -> toVO(d, paths, uploaders, access.get(d.getId())));
    }

    @Transactional(readOnly = true)
    public DocVO detail(Long id, CurrentUser cu) {
        Doc d = mustGet(id);
        if (!d.available() && !has(cu, PERM_MANAGE)) {
            throw BizException.notFound("资料");
        }
        return toVO(d, categoryService.paths(), uploaderNames(List.of(d)), access(cu, List.of(d)).get(id));
    }

    /** 当前用户对一批资料的访问权限 */
    @Transactional(readOnly = true)
    public Map<Long, DocAccess> access(CurrentUser cu, List<Doc> docs) {
        LocalDateTime now = LocalDateTime.now();
        Map<Long, DocRequest> bestReq = new HashMap<>();
        Map<Long, Integer> remaining = new HashMap<>();
        Set<Long> pending = new HashSet<>();
        for (DocRequest r : requestRepo.findByApplicantIdAndStatusIn(cu.id(), List.of(DocRequest.Status.APPROVED, DocRequest.Status.PENDING))) {
            for (DocRequest.Item it : r.getItems()) {
                if (r.getStatus() == DocRequest.Status.PENDING) {
                    pending.add(it.getDocId());
                } else if (r.valid(now)) {
                    int left = r.getMaxDownloads() - it.getDownloadCount();
                    if (left > 0 && left > remaining.getOrDefault(it.getDocId(), 0)) {
                        remaining.put(it.getDocId(), left);
                        bestReq.put(it.getDocId(), r);
                    }
                }
            }
        }
        Map<Long, DocAccess> result = new HashMap<>();
        for (Doc d : docs) {
            boolean free = !d.isNeedApproval();
            DocRequest r = bestReq.get(d.getId());
            result.put(d.getId(), new DocAccess(free, free || r != null, r == null ? null : r.getId(),
                    remaining.get(d.getId()), r == null ? null : r.getExpireAt(), pending.contains(d.getId())));
        }
        return result;
    }

    // ------------------------------------------------------------------ 维护

    @Transactional
    public Long upload(MultipartFile file, DocForm form, CurrentUser cu) {
        String ext = checkFile(file);
        categoryService.mustGet(form.categoryId());
        Doc d = new Doc();
        apply(d, form);
        d.setStorageType(StorageType.LOCAL);
        d.setStorageKey(save(file, ext));
        d.setFileName(cleanFileName(file.getOriginalFilename()));
        d.setFileExt(ext);
        d.setFileSize(file.getSize());
        d.setSourceModifiedAt(LocalDateTime.now().withNano(0));
        d.setUploaderId(cu.id());
        repo.save(d);
        logService.log(d.getId(), cu.id(), "UPLOAD", null, d.getFileName());
        return d.getId();
    }

    @Transactional
    public void update(Long id, DocForm form, CurrentUser cu) {
        Doc d = mustGet(id);
        categoryService.mustGet(form.categoryId());
        apply(d, form);
        logService.log(id, cu.id(), "UPDATE", null, null);
    }

    /** 更新文件（新版本）：仅系统上传的资料；网盘资料请在网盘中替换后同步 */
    @Transactional
    public void replaceFile(Long id, MultipartFile file, CurrentUser cu) {
        Doc d = mustGet(id);
        if (d.getStorageType() == StorageType.NAS) {
            throw new BizException("网盘资料请直接在网盘中替换文件，然后执行网盘同步");
        }
        String ext = checkFile(file);
        String old = d.getStorageKey();
        d.setStorageKey(save(file, ext));
        d.setFileName(cleanFileName(file.getOriginalFilename()));
        d.setFileExt(ext);
        d.setFileSize(file.getSize());
        d.setSourceModifiedAt(LocalDateTime.now().withNano(0));
        storage.deleteLocal(old);
        logService.log(id, cu.id(), "REPLACE", null, d.getFileName());
    }

    @Transactional
    public void setStatus(Long id, Doc.Status status, CurrentUser cu) {
        Doc d = mustGet(id);
        if (status == Doc.Status.ON && d.isSourceMissing()) {
            throw new BizException("网盘中的源文件已不存在，不能上架");
        }
        d.setStatus(status);
        logService.log(id, cu.id(), status == Doc.Status.ON ? "ONLINE" : "OFFLINE", null, null);
    }

    @Transactional
    public void delete(Long id, CurrentUser cu) {
        Doc d = mustGet(id);
        d.setDeleted(true);
        // 保留文件以便审计追溯，由运维按需清理
        logService.log(id, cu.id(), "DELETE", null, d.getName());
    }

    // ------------------------------------------------------------------ 内部

    Doc mustGet(Long id) {
        return repo.findByIdAndDeletedFalse(id).orElseThrow(() -> BizException.notFound("资料"));
    }

    static boolean previewable(String ext) {
        return ext != null && PREVIEW_EXT.contains(ext.toLowerCase(Locale.ROOT));
    }

    static String extOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int i = fileName.lastIndexOf('.');
        return i < 0 ? "" : fileName.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private String checkFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的文件");
        }
        String ext = extOf(file.getOriginalFilename());
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException("不支持的文件类型：" + (ext.isEmpty() ? "无扩展名" : ext));
        }
        return ext;
    }

    private String save(MultipartFile file, String ext) {
        try (InputStream in = file.getInputStream()) {
            return storage.saveLocal(in, ext);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String cleanFileName(String name) {
        if (name == null || name.isBlank()) {
            return "未命名";
        }
        String n = name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        return n.length() > 255 ? n.substring(n.length() - 255) : n;
    }

    private void apply(Doc d, DocForm f) {
        d.setCategoryId(f.categoryId());
        d.setName(f.name().trim());
        d.setCode(blankToNull(f.code()));
        d.setVersion(blankToNull(f.version()));
        d.setYear(f.year());
        d.setLevel(f.level());
        // 涉密资料必须审批
        d.setNeedApproval(f.level() == Doc.Level.SECRET || f.needApproval() == null || f.needApproval());
        d.setRemark(blankToNull(f.remark()));
    }

    private Map<Long, String> uploaderNames(List<Doc> docs) {
        Set<Long> ids = docs.stream().map(Doc::getUploaderId).filter(Objects::nonNull).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of()
                : userRepo.findByIdIn(ids).stream().collect(Collectors.toMap(SysUser::getId, SysUser::getRealName));
    }

    private DocVO toVO(Doc d, Map<Long, String> paths, Map<Long, String> uploaders, DocAccess access) {
        int newDays = configService.getInt("doc.new-days", 30);
        boolean isNew = d.getCreatedAt() != null && d.getCreatedAt().isAfter(LocalDateTime.now().minusDays(newDays));
        return new DocVO(d.getId(), d.getCategoryId(), paths.get(d.getCategoryId()), d.getName(), d.getCode(),
                d.getVersion(), d.getYear(), d.getLevel(), d.getLevel().label(), d.isNeedApproval(), d.getStorageType(),
                d.getFileName(), d.getFileExt(), d.getFileSize(), previewable(d.getFileExt()), d.getStatus(),
                d.isSourceMissing(), d.getViewCount(), d.getDownloadCount(), isNew, uploaders.get(d.getUploaderId()),
                d.getRemark(), d.getCreatedAt(), d.getUpdatedAt(), access);
    }

    static boolean has(CurrentUser cu, String perm) {
        if (cu.superAdmin() || cu.permissions().contains(perm)) {
            return true;
        }
        return false;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** 供文件访问：校验并返回资料 */
    Doc requireAvailable(Long id) {
        Doc d = mustGet(id);
        if (!d.available()) {
            throw new BizException(HttpStatus.NOT_FOUND, "资料已下架");
        }
        return d;
    }
}
