package com.hongyu.water.doc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.hongyu.water.doc.DocFileService.FileHandle;

/**
 * 凭证访问文件（/api/files/** 无需登录令牌，凭短时凭证访问，见 SecurityConfig）。
 * 返回 Resource，Spring MVC 自动支持 Range 请求（断点续传、PDF 分段加载）。
 */
@RestController
public class DocFileController {

    private static final Map<String, MediaType> INLINE_TYPES = Map.of(
            "pdf", MediaType.APPLICATION_PDF,
            "txt", new MediaType("text", "plain", StandardCharsets.UTF_8),
            "jpg", MediaType.IMAGE_JPEG, "jpeg", MediaType.IMAGE_JPEG,
            "png", MediaType.IMAGE_PNG, "gif", MediaType.IMAGE_GIF,
            "bmp", MediaType.parseMediaType("image/bmp"), "webp", MediaType.parseMediaType("image/webp"));

    private final DocFileService service;

    public DocFileController(DocFileService service) {
        this.service = service;
    }

    @GetMapping("/api/files/{token}")
    public ResponseEntity<Resource> file(@PathVariable String token) {
        FileHandle f = service.open(token);
        HttpHeaders h = new HttpHeaders();
        h.setCacheControl("private, no-store");
        h.set("X-Content-Type-Options", "nosniff");
        if (f.download()) {
            h.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            h.setContentDisposition(ContentDisposition.attachment().filename(f.fileName(), StandardCharsets.UTF_8).build());
        } else {
            h.setContentType(INLINE_TYPES.getOrDefault(f.ext() == null ? "" : f.ext().toLowerCase(), MediaType.APPLICATION_OCTET_STREAM));
            h.setContentDisposition(ContentDisposition.inline().filename(f.fileName(), StandardCharsets.UTF_8).build());
        }
        return ResponseEntity.ok().headers(h).body(f.resource());
    }
}
