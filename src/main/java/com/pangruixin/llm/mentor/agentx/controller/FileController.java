package com.pangruixin.llm.mentor.agentx.controller;

import com.pangruixin.llm.mentor.agentx.common.R;
import com.pangruixin.llm.mentor.agentx.auth.service.AuthService;
import com.pangruixin.llm.mentor.agentx.domain.entities.AgentxFile;
import com.pangruixin.llm.mentor.agentx.domain.vo.UploadResponse;
import com.pangruixin.llm.mentor.agentx.service.FileAccessDeniedException;
import com.pangruixin.llm.mentor.agentx.service.FileManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件相关操作
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private static final Map<String, MediaType> IMAGE_MIME = Map.of(
            "jpg", MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG,
            "png", MediaType.IMAGE_PNG,
            "gif", MediaType.IMAGE_GIF,
            "bmp", MediaType.parseMediaType("image/bmp"),
            "webp", MediaType.parseMediaType("image/webp")
    );

    private final FileManageService fileManageService;
    private final AuthService authService;

    @Value("${file.max-size-mb:100}")
    private long maxSizeMb;

    /**
     * 文件上传
     */
    @PostMapping("/upload")
    public R<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return R.paramError("文件不能为空");
        }
        long maxBytes = maxSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            return R.paramError("文件不能超过 " + maxSizeMb + "MB");
        }

        log.info("[dodo-agentx] 收到上传: name={}, size={}", file.getOriginalFilename(), file.getSize());
        try {
            Long currentUserId = authService.getCurrentUserIdOrNull();
            if (currentUserId == null) {
                return R.unauthorized("请先登录");
            }
            AgentxFile saved = fileManageService.uploadFile(file, currentUserId);
            return R.ok(UploadResponse.from(saved));
        } catch (Exception e) {
            log.error("[dodo-agentx] 上传失败", e);
            return R.fail("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 文件预览
     */
    @GetMapping("/{fileId}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable String fileId) {
        Long currentUserId = authService.getCurrentUserIdOrNull();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            AgentxFile meta = fileManageService.getFileInfo(fileId, currentUserId);
            byte[] bytes = fileManageService.downloadFileBytes(fileId, currentUserId);
            MediaType mime = IMAGE_MIME.getOrDefault(
                    meta.getFileType() == null ? "" : meta.getFileType().toLowerCase(),
                    MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok()
                    .contentType(mime)
                    .cacheControl(CacheControl.noStore())
                    .contentLength(bytes.length)
                    .body(bytes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.warn("[dodo-agentx] 预览失败: fileId={}, err={}", fileId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 文件删除
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<R<Void>> delete(@PathVariable String fileId) {
        Long currentUserId = authService.getCurrentUserIdOrNull();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(R.unauthorized("请先登录"));
        }
        try {
            fileManageService.deleteFile(fileId, currentUserId);
            return ResponseEntity.ok(R.ok());
        } catch (FileAccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(R.notFound(e.getMessage()));
        } catch (Exception e) {
            log.error("[dodo-agentx] 删除失败: fileId={}", fileId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(R.fail("删除失败: " + e.getMessage()));
        }
    }
}
