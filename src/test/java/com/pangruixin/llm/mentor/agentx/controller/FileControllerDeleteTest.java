package com.pangruixin.llm.mentor.agentx.controller;

import com.pangruixin.llm.mentor.agentx.auth.service.AuthService;
import com.pangruixin.llm.mentor.agentx.common.R;
import com.pangruixin.llm.mentor.agentx.service.FileAccessDeniedException;
import com.pangruixin.llm.mentor.agentx.service.FileManageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FileControllerDeleteTest {

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
    void ownerDeleteReturnsHttp200() {
        when(authService.getCurrentUserIdOrNull()).thenReturn(11L);

        ResponseEntity<R<Void>> response = controller.delete("file-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(R.SUCCESS, response.getBody().getCode());
        verify(fileManageService).deleteFile("file-1", 11L);
    }

    @Test
    void otherUserDeleteReturnsRealHttp404WithUnifiedMessage() {
        when(authService.getCurrentUserIdOrNull()).thenReturn(22L);
        doThrow(new FileAccessDeniedException())
                .when(fileManageService).deleteFile("file-1", 22L);

        ResponseEntity<R<Void>> response = controller.delete("file-1");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(R.NOT_FOUND, response.getBody().getCode());
        assertEquals("文件不存在或无权访问", response.getBody().getMsg());
    }

    @Test
    void missingLoginDeleteReturnsHttp401WithoutFileServiceAccess() {
        when(authService.getCurrentUserIdOrNull()).thenReturn(null);

        ResponseEntity<R<Void>> response = controller.delete("file-1");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(R.UNAUTHORIZED, response.getBody().getCode());
        verify(fileManageService, never()).deleteFile(anyString(), anyLong());
        verifyNoInteractions(fileManageService);
    }
}
