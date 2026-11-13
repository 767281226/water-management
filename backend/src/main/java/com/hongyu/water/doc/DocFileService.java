package com.hongyu.water.doc;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hongyu.water.common.BizException;
import com.hongyu.water.doc.DocDtos.TicketResponse;
import com.hongyu.water.doc.DocTicketService.Ticket;
import com.hongyu.water.doc.storage.FileStorage;
import com.hongyu.water.security.CurrentUser;
import com.hongyu.water.system.user.SysUser;
import com.hongyu.water.system.user.SysUserRepository;

/**
 * 预览 / 下载：先校验权限签发凭证，再凭凭证读取文件；预览、下载在凭证首次使用时计数并记录日志。
 */
@Service
public class DocFileService {

    private final DocService docService;
    private final DocRequestService requestService;
    private final DocTicketService ticketService;
    private final DocRepository docRepo;
    private final FileStorage storage;
    private final DocLogService logService;
    private final SysUserRepository userRepo;

    public DocFileService(DocService docService, DocRequestService requestService, DocTicketService ticketService,
            DocRepository docRepo, FileStorage storage, DocLogService logService, SysUserRepository userRepo) {
        this.docService = docService;
        this.requestService = requestService;
        this.ticketService = ticketService;
        this.docRepo = docRepo;
        this.storage = storage;
        this.logService = logService;
        this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    public TicketResponse issue(Long docId, String mode, Long requestId, CurrentUser cu) {
        Doc d = docService.requireAvailable(docId);
        SysUser u = userRepo.findById(cu.id()).orElseThrow();
        boolean download = "download".equals(mode);
        Long reqId = null;
        if (download) {
            DocRequest r = requestService.checkDownload(d, requestId, cu.id());
            reqId = r == null ? null : r.getId();
        } else if (!DocService.previewable(d.getFileExt())) {
            throw new BizException("该格式暂不支持在线预览，请申请下载后查看");
        }
        String token = ticketService.issue(docId, cu.id(), u.getRealName(), u.getEmployeeNo(), download, reqId);
        return new TicketResponse("/api/files/" + token, DocTicketService.TTL.toSeconds());
    }

    /** 读取文件；凭证首次使用时计数（同一凭证的分段 / 续传请求不重复计数） */
    @Transactional
    public FileHandle open(String token) {
        Ticket t = ticketService.get(token);
        Doc d = docService.requireAvailable(t.docId());
        if (t.used().compareAndSet(false, true)) {
            if (t.download()) {
                if (t.requestId() != null) {
                    // 签发后到使用前可能已用完次数，再校验一次
                    requestService.checkDownload(d, t.requestId(), t.userId());
                    requestService.recordDownload(t.requestId(), d.getId());
                }
                docRepo.incrementDownload(d.getId());
                logService.log(d.getId(), t.userId(), "DOWNLOAD", t.requestId(), d.getFileName());
            } else {
                docRepo.incrementView(d.getId());
                logService.log(d.getId(), t.userId(), "VIEW", null, null);
            }
        }
        return new FileHandle(storage.load(d.getStorageType(), d.getStorageKey()), d.getFileName(), d.getFileExt(), t.download());
    }

    public record FileHandle(Resource resource, String fileName, String ext, boolean download) {
    }
}
