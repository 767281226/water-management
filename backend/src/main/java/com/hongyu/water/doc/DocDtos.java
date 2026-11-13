package com.hongyu.water.doc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.hongyu.water.doc.storage.StorageType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class DocDtos {

    private DocDtos() {
    }

    // ---------------- 分类 ----------------

    public record CategoryNode(Long id, Long parentId, String name, int sort, long docCount, List<CategoryNode> children) {

        public CategoryNode {
            children = children == null ? new ArrayList<>() : children;
        }
    }

    public record CategoryForm(Long parentId,
            @NotBlank(message = "请输入分类名称") @Size(max = 64, message = "分类名称不超过 64 个字") String name,
            @Min(0) @Max(9999) Integer sort) {
    }

    // ---------------- 资料 ----------------

    public record DocQuery(String keyword, Long categoryId, Doc.Level level, Integer year, Boolean manage,
            Doc.Status status, StorageType storageType, Integer page, Integer size) {
    }

    /**
     * 当前用户对资料的访问权限。
     *
     * @param free        免审批，可直接下载
     * @param canDownload 可以下载（免审，或有有效的审批通过申请且仍有剩余次数）
     * @param requestId   用于下载的申请
     * @param remaining   剩余下载次数
     * @param pending     有审批中的申请
     */
    public record DocAccess(boolean free, boolean canDownload, Long requestId, Integer remaining,
            LocalDateTime expireAt, boolean pending) {
    }

    public record DocVO(Long id, Long categoryId, String categoryPath, String name, String code, String version,
            Integer year, Doc.Level level, String levelLabel, boolean needApproval, StorageType storageType,
            String fileName, String fileExt, long fileSize, boolean previewable, Doc.Status status,
            boolean sourceMissing, int viewCount, int downloadCount, boolean isNew, String uploaderName,
            String remark, LocalDateTime createdAt, LocalDateTime updatedAt, DocAccess access) {
    }

    public record DocForm(
            @NotNull(message = "请选择分类") Long categoryId,
            @NotBlank(message = "请输入资料名称") @Size(max = 200, message = "资料名称不超过 200 个字") String name,
            @Size(max = 64, message = "编号不超过 64 个字符") String code,
            @Size(max = 32, message = "版本不超过 32 个字符") String version,
            @Min(value = 1900, message = "年份不正确") @Max(value = 2100, message = "年份不正确") Integer year,
            @NotNull(message = "请选择密级") Doc.Level level,
            Boolean needApproval,
            @Size(max = 500, message = "备注不超过 500 个字") String remark) {
    }

    public record StatusRequest(@NotNull Doc.Status status) {
    }

    // ---------------- 下载申请 ----------------

    public record ApplyRequest(@NotEmpty(message = "请选择要下载的资料") @Size(max = 50, message = "一次最多申请 50 份资料") List<Long> docIds,
            Long projectId,
            @NotBlank(message = "请填写用途说明") @Size(max = 500, message = "用途说明不超过 500 个字") String purpose) {
    }

    public record ApproveRequest(@Size(max = 255) String comment) {
    }

    public record RejectRequest(@NotBlank(message = "请填写驳回原因") @Size(max = 255) String comment) {
    }

    public record BatchApproveRequest(@NotEmpty List<Long> ids) {
    }

    public record RequestItemVO(Long docId, String name, String code, Doc.Level level, String fileExt, long fileSize,
            int downloadCount, boolean available) {
    }

    public record StepVO(int stepNo, String name, DocRequestStep.ApproverType approverType, DocRequestStep.Status status,
            String approverName, String comment, LocalDateTime actedAt) {
    }

    public record RequestActions(boolean approve, boolean cancel, boolean download) {
    }

    public record RequestVO(Long id, Long applicantId, String applicantName, String applicantDept, String projectName,
            String purpose, DocRequest.Status status, LocalDateTime createdAt, LocalDateTime expireAt, int maxDownloads,
            boolean secret, List<RequestItemVO> items, List<StepVO> steps, RequestActions actions) {
    }

    // ---------------- 文件访问 ----------------

    public record TicketResponse(String url, long expiresIn) {
    }

    // ---------------- 网盘 ----------------

    public record StorageInfo(String nasType, boolean nasEnabled, String nasDescription, String lastSync, long nasDocs,
            long localDocs) {
    }

    public record SyncResult(int scanned, int added, int updated, int missing, int restored, long costMs) {
    }

    // ---------------- 日志 ----------------

    public record LogQuery(String keyword, String action, Long docId, Integer page, Integer size) {
    }

    public record LogVO(Long id, LocalDateTime createdAt, String userName, String action, String docName, Long docId,
            Long requestId, String ip, String detail) {
    }
}
