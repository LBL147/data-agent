package com.pangruixin.llm.mentor.agentx.controller;

import com.pangruixin.llm.mentor.agentx.auth.service.AuthService;
import com.pangruixin.llm.mentor.agentx.domain.entities.AgentxFile;
import com.pangruixin.llm.mentor.agentx.service.FileAccessDeniedException;
import com.pangruixin.llm.mentor.agentx.service.FileManageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FileControllerPreviewTest {

    private FileManageService fileManageService;
    private AuthService authService;
    private FileController controller;

    @BeforeEach
    void setUp() {
        fileManageService = mock(FileManageService.class);
        authService = mock(AuthService.class);
        controller = new FileController(fileManageService, authService);
    }

    @Test
    void ownerPreviewReturnsBytesWithNoStoreCachePolicy() {
        AgentxFile file = new AgentxFile();
        file.setFileId("file-1");
        file.setFileType("png");
        byte[] bytes = "image-bytes".getBytes(StandardCharsets.UTF_8);
        when(authService.getCurrentUserIdOrNull()).thenReturn(11L);
        when(fileManageService.getFileInfo("file-1", 11L)).thenReturn(file);
        when(fileManageService.downloadFileBytes("file-1", 11L)).thenReturn(bytes);

        ResponseEntity<byte[]> response = controller.preview("file-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
        assertArrayEquals(bytes, response.getBody());
        String cacheControl = response.getHeaders().getCacheControl();
        assertNotNull(cacheControl);
        assertTrue(cacheControl.contains("no-store"), cacheControl);
        assertFalse(cacheControl.contains("public"), cacheControl);
        verify(fileManageService).downloadFileBytes("file-1", 11L);
    }

    @Test
    void otherUserPreviewIsRejectedBeforeDownload() {
        when(authService.getCurrentUserIdOrNull()).thenReturn(22L);
        when(fileManageService.getFileInfo("file-1", 22L))
                .thenThrow(new FileAccessDeniedException());

        ResponseEntity<byte[]> response = controller.preview("file-1");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(fileManageService, never()).downloadFileBytes(anyString(), anyLong());
    }

    @Test
    void missingLoginIsRejectedWithoutFileAccess() {
        when(authService.getCurrentUserIdOrNull()).thenReturn(null);

        ResponseEntity<byte[]> response = controller.preview("file-1");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(fileManageService);
    }
}
