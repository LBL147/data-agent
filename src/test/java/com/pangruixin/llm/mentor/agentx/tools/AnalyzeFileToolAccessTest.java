package com.pangruixin.llm.mentor.agentx.tools;

import com.pangruixin.llm.mentor.agentx.domain.entities.AgentxFile;
import com.pangruixin.llm.mentor.agentx.service.EmbeddingService;
import com.pangruixin.llm.mentor.agentx.service.FileAccessDeniedException;
import com.pangruixin.llm.mentor.agentx.service.FileManageService;
import com.pangruixin.llm.mentor.agentx.service.MultimodalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalyzeFileToolAccessTest {

    private static final String OWNER_CONTENT = "owner-only-content";

    private FileManageService fileManageService;
    private EmbeddingService embeddingService;
    private MultimodalService multimodalService;
    private AnalyzeFileTool tool;

    @BeforeEach
    void setUp() {
        fileManageService = mock(FileManageService.class);
        embeddingService = mock(EmbeddingService.class);
        multimodalService = mock(MultimodalService.class);
        tool = new AnalyzeFileTool(fileManageService, embeddingService, multimodalService);
    }

    @Test
    void ownerCanReadOwnTextWithoutRagOrMultimodalAccess() {
        when(fileManageService.getFileInfo("file-1", 11L))
                .thenReturn(textFile("file-1", 11L, OWNER_CONTENT));

        String result = tool.analyzeFile("file-1", "summarize", "11");

        assertTrue(result.contains(OWNER_CONTENT));
        verify(fileManageService, never()).downloadFileBytes(anyString(), anyLong());
        verifyNoInteractions(embeddingService, multimodalService);
    }

    @Test
    void otherUserIsDeniedBeforeContentRagMinioOrMultimodalAccess() {
        when(fileManageService.getFileInfo("file-1", 22L))
                .thenThrow(new FileAccessDeniedException());

        String result = tool.analyzeFile("file-1", "summarize", "22");

        assertEquals("文件不存在或无权访问", result);
        assertFalse(result.contains(OWNER_CONTENT));
        verify(fileManageService, never()).downloadFileBytes(anyString(), anyLong());
        verify(fileManageService, never()).saveExtractedText(anyString(), anyString(), anyLong());
        verifyNoInteractions(embeddingService, multimodalService);
    }

    @Test
    void missingIdentityIsRejectedWithoutAnyFileAccess() {
        String result = tool.analyzeFile("file-1", "summarize", null);

        assertEquals("文件访问身份无效", result);
        verifyNoInteractions(fileManageService, embeddingService, multimodalService);
    }

    @Test
    void ownerCanUseRagForOwnVectorizedFile() {
        AgentxFile file = textFile("file-1", 11L, null);
        file.setEmbed(1);
        when(fileManageService.getFileInfo("file-1", 11L)).thenReturn(file);
        when(embeddingService.ragRetrieve("file-1", "payment summary"))
                .thenReturn(List.of("owner-rag-segment"));

        String result = tool.analyzeFile("file-1", "payment summary", "11");

        assertTrue(result.contains("owner-rag-segment"));
        verify(embeddingService).ragRetrieve("file-1", "payment summary");
        verify(fileManageService, never()).downloadFileBytes(anyString(), anyLong());
        verifyNoInteractions(multimodalService);
    }

    @Test
    void otherUserIsDeniedBeforeRagRetrieval() {
        when(fileManageService.getFileInfo("file-1", 22L))
                .thenThrow(new FileAccessDeniedException());

        String result = tool.analyzeFile("file-1", "owner-rag-segment", "22");

        assertEquals("文件不存在或无权访问", result);
        assertFalse(result.contains("owner-rag-segment"));
        verify(embeddingService, never()).ragRetrieve(anyString(), anyString());
        verifyNoInteractions(multimodalService);
    }

    @Test
    void ownerImageCacheHitDoesNotDownloadOrCallMultimodal() {
        AgentxFile file = imageFile("image-1", 11L, "cached-description");
        when(fileManageService.getFileInfo("image-1", 11L)).thenReturn(file);
        when(fileManageService.isImageFile("png")).thenReturn(true);

        String result = tool.analyzeFile("image-1", null, "11");

        assertTrue(result.contains("cached-description"));
        verify(fileManageService, never()).downloadFileBytes(anyString(), anyLong());
        verify(fileManageService, never()).saveExtractedText(anyString(), anyString(), anyLong());
        verifyNoInteractions(multimodalService);
    }

    @Test
    void ownerImageCacheMissDownloadsRecognizesAndWritesBack() {
        AgentxFile file = imageFile("image-1", 11L, null);
        byte[] bytes = "image-bytes".getBytes(StandardCharsets.UTF_8);
        when(fileManageService.getFileInfo("image-1", 11L)).thenReturn(file);
        when(fileManageService.isImageFile("png")).thenReturn(true);
        when(fileManageService.downloadFileBytes("image-1", 11L)).thenReturn(bytes);
        when(multimodalService.imageToText(bytes, "image.png")).thenReturn("fresh-description");

        String result = tool.analyzeFile("image-1", null, "11");

        assertTrue(result.contains("fresh-description"));
        verify(multimodalService).imageToText(bytes, "image.png");
        verify(fileManageService).saveExtractedText("image-1", "fresh-description", 11L);
    }

    @Test
    void otherUserCannotReadImageCacheOrWriteBack() {
        when(fileManageService.getFileInfo("image-1", 22L))
                .thenThrow(new FileAccessDeniedException());

        String result = tool.analyzeFile("image-1", null, "22");

        assertEquals("文件不存在或无权访问", result);
        verify(fileManageService, never()).downloadFileBytes(anyString(), anyLong());
        verify(fileManageService, never()).saveExtractedText(anyString(), anyString(), anyLong());
        verifyNoInteractions(multimodalService);
    }

    private static AgentxFile textFile(String fileId, Long ownerUserId, String content) {
        AgentxFile file = new AgentxFile();
        file.setFileId(fileId);
        file.setOwnerUserId(ownerUserId);
        file.setFileName("note.txt");
        file.setFileType("txt");
        file.setStatus("SUCCESS");
        file.setEmbed(0);
        file.setExtractedText(content);
        return file;
    }

    private static AgentxFile imageFile(String fileId, Long ownerUserId, String extractedText) {
        AgentxFile file = new AgentxFile();
        file.setFileId(fileId);
        file.setOwnerUserId(ownerUserId);
        file.setFileName("image.png");
        file.setFileType("png");
        file.setStatus("SUCCESS");
        file.setEmbed(0);
        file.setExtractedText(extractedText);
        return file;
    }
}
