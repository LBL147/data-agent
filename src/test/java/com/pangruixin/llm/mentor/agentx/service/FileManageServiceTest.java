package com.pangruixin.llm.mentor.agentx.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.pangruixin.llm.mentor.agentx.domain.entities.AgentxConversation;
import com.pangruixin.llm.mentor.agentx.domain.entities.AgentxFile;
import com.pangruixin.llm.mentor.agentx.mapper.AgentxConversationMapper;
import com.pangruixin.llm.mentor.agentx.mapper.AgentxFileMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FileManageServiceTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, AgentxFile.class);
        TableInfoHelper.initTableInfo(assistant, AgentxConversation.class);
    }

    private MinioService minioService;
    private FileParserService fileParserService;
    private EmbeddingService embeddingService;
    private AgentxFileMapper agentxFileMapper;
    private AgentxConversationMapper agentxConversationMapper;
    private FileManageService service;

    @BeforeEach
    void setUp() {
        minioService = mock(MinioService.class);
        fileParserService = mock(FileParserService.class);
        embeddingService = mock(EmbeddingService.class);
        agentxFileMapper = mock(AgentxFileMapper.class);
        agentxConversationMapper = mock(AgentxConversationMapper.class);
        service = new FileManageService(
                mock(DataSource.class),
                minioService,
                fileParserService,
                embeddingService,
                agentxFileMapper,
                agentxConversationMapper
        );
    }

    @Test
    void ownerCanReadOwnMetadataAndQueryIsOwnerScoped() {
        AgentxFile file = file("file-1", 11L);
        when(agentxFileMapper.selectOne(any())).thenReturn(file);

        AgentxFile result = service.getFileInfo("file-1", 11L);

        assertEquals("file-1", result.getFileId());
        assertEquals(11L, result.getOwnerUserId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<AgentxFile>> wrapperCaptor =
                ArgumentCaptor.forClass(Wrapper.class);
        verify(agentxFileMapper).selectOne(wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getTargetSql();
        assertTrue(sql.contains("owner_user_id"), sql);
        assertTrue(((AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue())
                .getParamNameValuePairs().containsValue(11L));
        verifyNoInteractions(minioService, fileParserService, embeddingService);
    }

    @Test
    void otherUserCannotReadMetadataBeforeAnyExternalAccess() {
        when(agentxFileMapper.selectOne(any())).thenReturn(null);

        assertThrows(FileAccessDeniedException.class,
                () -> service.getFileInfo("file-1", 22L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<AgentxFile>> wrapperCaptor =
                ArgumentCaptor.forClass(Wrapper.class);
        verify(agentxFileMapper).selectOne(wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getTargetSql();
        assertTrue(sql.contains("owner_user_id"), sql);
        assertTrue(((AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue())
                .getParamNameValuePairs().containsValue(22L));
        verifyNoInteractions(minioService, fileParserService, embeddingService);
    }

    @Test
    void missingIdentityIsRejectedWithoutQueryingMetadata() {
        assertThrows(FileAccessDeniedException.class,
                () -> service.getFileInfo("file-1", null));

        verify(agentxFileMapper, never()).selectOne(any());
        verifyNoInteractions(minioService, fileParserService, embeddingService);
    }

    @Test
    void unknownHistoricalOwnerIsRejectedEvenIfRowIsReturned() {
        when(agentxFileMapper.selectOne(any())).thenReturn(file("legacy-file", null));

        assertThrows(FileAccessDeniedException.class,
                () -> service.getFileInfo("legacy-file", 11L));

        verifyNoInteractions(minioService, fileParserService, embeddingService);
    }

    @Test
    void ownerCanReadOwnTextContent() {
        AgentxFile file = file("file-1", 11L);
        file.setExtractedText("owner-only-content");
        when(agentxFileMapper.selectOne(any())).thenReturn(file);

        String content = service.getFileContent("file-1", 11L);

        assertEquals("owner-only-content", content);
        verifyNoInteractions(minioService, fileParserService, embeddingService);
    }

    @Test
    void otherUserCannotReadTextContentBeforeReturn() {
        when(agentxFileMapper.selectOne(any())).thenReturn(null);

        assertThrows(FileAccessDeniedException.class,
                () -> service.getFileContent("file-1", 22L));

        verifyNoInteractions(minioService, fileParserService, embeddingService);
    }

    @Test
    void missingIdentityCannotReadTextContentWithoutQueryingMetadata() {
        assertThrows(FileAccessDeniedException.class,
                () -> service.getFileContent("file-1", null));

        verify(agentxFileMapper, never()).selectOne(any());
        verifyNoInteractions(minioService, fileParserService, embeddingService);
    }

    @Test
    void uploadRecordsTrustedOwner() throws Exception {
        MultipartFile upload = new MockMultipartFile(
                "file", "note.bin", "application/octet-stream", "data".getBytes());
        when(minioService.uploadFile(any(), anyString())).thenReturn("http://minio/bucket/object");
        when(agentxFileMapper.insert(any(AgentxFile.class))).thenReturn(1);
        when(agentxFileMapper.updateById(any(AgentxFile.class))).thenReturn(1);

        AgentxFile saved = service.uploadFile(upload, 7L);

        assertEquals(7L, saved.getOwnerUserId());
        ArgumentCaptor<AgentxFile> entityCaptor = ArgumentCaptor.forClass(AgentxFile.class);
        verify(agentxFileMapper).insert(entityCaptor.capture());
        assertEquals(7L, entityCaptor.getValue().getOwnerUserId());
    }

    @Test
    void uploadWithoutIdentityIsRejectedBeforeSideEffects() {
        MultipartFile upload = new MockMultipartFile(
                "file", "note.bin", "application/octet-stream", "data".getBytes());

        assertThrows(FileAccessDeniedException.class,
                () -> service.uploadFile(upload, null));

        verifyNoInteractions(agentxFileMapper, minioService, fileParserService, embeddingService);
    }

    @Test
    void ownerCanDownloadOwnBytesAndObjectReadHappensAfterOwnershipCheck() throws Exception {
        AgentxFile file = file("file-1", 11L);
        file.setMinioPath("http://minio/rag-test2/file-1.txt");
        byte[] expected = "owner-bytes".getBytes(StandardCharsets.UTF_8);
        when(agentxFileMapper.selectOne(any())).thenReturn(file);
        when(minioService.downloadFile("file-1.txt"))
                .thenReturn(new ByteArrayInputStream(expected));

        byte[] actual = service.downloadFileBytes("file-1", 11L);

        assertArrayEquals(expected, actual);
        verify(agentxFileMapper).selectOne(any());
        verify(minioService).downloadFile("file-1.txt");
    }

    @Test
    void otherUserCannotDownloadBytesOrReachMinio() {
        when(agentxFileMapper.selectOne(any())).thenReturn(null);

        assertThrows(FileAccessDeniedException.class,
                () -> service.downloadFileBytes("file-1", 22L));

        verifyNoInteractions(minioService);
    }

    @Test
    void missingIdentityCannotDownloadBytesWithoutQueryingMetadata() {
        assertThrows(FileAccessDeniedException.class,
                () -> service.downloadFileBytes("file-1", null));

        verify(agentxFileMapper, never()).selectOne(any());
        verifyNoInteractions(minioService);
    }

    @Test
    void ownerCanWriteImageCache() {
        AgentxFile file = file("file-1", 11L);
        when(agentxFileMapper.selectOne(any())).thenReturn(file);

        service.saveExtractedText("file-1", "recognized-image", 11L);

        assertEquals("recognized-image", file.getExtractedText());
        verify(agentxFileMapper).updateById(file);
    }

    @Test
    void otherUserCannotOverwriteImageCache() {
        when(agentxFileMapper.selectOne(any())).thenReturn(null);

        assertThrows(FileAccessDeniedException.class,
                () -> service.saveExtractedText("file-1", "attacker-overwrite", 22L));

        verify(agentxFileMapper, never()).updateById(any(AgentxFile.class));
    }

    @Test
    void missingIdentityCannotOverwriteImageCacheWithoutMetadataQuery() {
        assertThrows(FileAccessDeniedException.class,
                () -> service.saveExtractedText("file-1", "attacker-overwrite", null));

        verify(agentxFileMapper, never()).selectOne(any());
        verify(agentxFileMapper, never()).updateById(any(AgentxFile.class));
    }

    @Test
    void ownerDeleteRemovesOwnedObjectAndDatabaseRow() throws Exception {
        AgentxFile file = file("file-1", 11L);
        file.setMinioPath("http://minio/rag-test2/file-1.txt");
        when(agentxFileMapper.selectOne(any())).thenReturn(file);

        service.deleteFile("file-1", 11L);

        verify(minioService).deleteFile("file-1.txt");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<AgentxFile>> wrapperCaptor =
                ArgumentCaptor.forClass(Wrapper.class);
        verify(agentxFileMapper).delete(wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getTargetSql();
        assertTrue(sql.contains("owner_user_id"), sql);
        assertTrue(((AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue())
                .getParamNameValuePairs().containsValue(11L));
    }

    @Test
    void otherUserDeleteIsRejectedBeforeMinioOrDatabaseDelete() {
        when(agentxFileMapper.selectOne(any())).thenReturn(null);

        assertThrows(FileAccessDeniedException.class,
                () -> service.deleteFile("file-1", 22L));

        verify(agentxFileMapper, never()).delete(any());
        verifyNoInteractions(minioService);
    }

    @Test
    void missingIdentityDeleteIsRejectedWithoutAnySideEffect() {
        assertThrows(FileAccessDeniedException.class,
                () -> service.deleteFile("file-1", null));

        verify(agentxFileMapper, never()).selectOne(any());
        verify(agentxFileMapper, never()).delete(any());
        verifyNoInteractions(minioService);
    }

    @Test
    void listByConversationIdIsOwnerScoped() {
        when(agentxFileMapper.selectList(any())).thenReturn(List.of(file("file-1", 11L)));

        List<AgentxFile> files = service.listByConversationId("conv-1", 11L);

        assertEquals(1, files.size());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<AgentxFile>> wrapperCaptor =
                ArgumentCaptor.forClass(Wrapper.class);
        verify(agentxFileMapper).selectList(wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getTargetSql();
        assertTrue(sql.contains("conversation_id"), sql);
        assertTrue(sql.contains("owner_user_id"), sql);
        assertTrue(((AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue())
                .getParamNameValuePairs().containsValue(11L));
    }

    @Test
    void listBySessionIdIsOwnerScoped() {
        when(agentxFileMapper.selectList(any())).thenReturn(List.of(file("file-1", 11L)));

        List<AgentxFile> files = service.listBySessionId(9001L, 11L);

        assertEquals(1, files.size());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<AgentxFile>> wrapperCaptor =
                ArgumentCaptor.forClass(Wrapper.class);
        verify(agentxFileMapper).selectList(wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getTargetSql();
        assertTrue(sql.contains("session_id"), sql);
        assertTrue(sql.contains("owner_user_id"), sql);
        assertTrue(((AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue())
                .getParamNameValuePairs().containsValue(11L));
    }

    @Test
    void listWithoutTrustedIdentityReturnsEmptyWithoutQuery() {
        assertTrue(service.listByConversationId("conv-1", null).isEmpty());
        assertTrue(service.listBySessionId(9001L, null).isEmpty());
        verify(agentxFileMapper, never()).selectList(any());
    }

    @Test
    void linkToConversationRejectsMixedFileOwnershipBeforeAnyWrite() {
        when(agentxConversationMapper.selectList(any()))
                .thenReturn(List.of(conversation("conv-1", "9001", "11")));
        when(agentxFileMapper.selectCount(any())).thenReturn(1L);

        assertThrows(FileAccessDeniedException.class,
                () -> service.linkToConversation(
                        List.of("owned-file", "foreign-file"), "conv-1", 11L));

        verify(agentxFileMapper, never()).update(any(), any());
    }

    @Test
    void linkToConversationRejectsForeignTargetConversationBeforeFileAccess() {
        when(agentxConversationMapper.selectList(any()))
                .thenReturn(List.of(conversation("conv-1", "9001", "22")));

        assertThrows(FileAccessDeniedException.class,
                () -> service.linkToConversation(List.of("file-1"), "conv-1", 11L));

        verify(agentxFileMapper, never()).selectCount(any());
        verify(agentxFileMapper, never()).update(any(), any());
    }

    @Test
    void linkToConversationUpdatesAllOwnedFiles() {
        when(agentxConversationMapper.selectList(any()))
                .thenReturn(List.of(conversation("conv-1", "9001", "11")));
        when(agentxFileMapper.selectCount(any())).thenReturn(2L);
        when(agentxFileMapper.update(isNull(), any())).thenReturn(2);
        when(agentxFileMapper.selectList(any()))
                .thenReturn(List.of(file("file-1", 11L), file("file-2", 11L)));

        service.linkToConversation(List.of("file-1", "file-2"), "conv-1", 11L);

        verify(agentxFileMapper).update(isNull(), any());
    }

    @Test
    void linkToConversationAllowsFirstRoundBeforeConversationIsPersisted() {
        when(agentxConversationMapper.selectList(any())).thenReturn(List.of());
        when(agentxFileMapper.selectCount(any())).thenReturn(1L);
        when(agentxFileMapper.update(isNull(), any())).thenReturn(1);
        when(agentxFileMapper.selectList(any()))
                .thenReturn(List.of(file("file-1", 11L)));

        service.linkToConversation(List.of("file-1"), "new-conv", 11L);

        verify(agentxFileMapper).update(isNull(), any());
    }

    @Test
    void linkToConversationIsIdempotentWhenDatabaseReportsZeroChangedRows() {
        when(agentxConversationMapper.selectList(any()))
                .thenReturn(List.of(conversation("conv-1", "9001", "11")));
        when(agentxFileMapper.selectCount(any())).thenReturn(2L);
        when(agentxFileMapper.update(isNull(), any())).thenReturn(0);
        when(agentxFileMapper.selectList(any()))
                .thenReturn(List.of(file("file-1", 11L), file("file-2", 11L)));

        service.linkToConversation(List.of("file-1", "file-2"), "conv-1", 11L);

        verify(agentxFileMapper).update(isNull(), any());
    }

    @Test
    void linkToConversationDeduplicatesRepeatedFileIds() {
        when(agentxConversationMapper.selectList(any()))
                .thenReturn(List.of(conversation("conv-1", "9001", "11")));
        when(agentxFileMapper.selectCount(any())).thenReturn(1L);
        when(agentxFileMapper.update(isNull(), any())).thenReturn(1);
        when(agentxFileMapper.selectList(any()))
                .thenReturn(List.of(file("file-1", 11L)));

        service.linkToConversation(List.of("file-1", "file-1"), "conv-1", 11L);

        verify(agentxFileMapper).update(isNull(), any());
    }

    @Test
    void linkToConversationRejectsPartialDatabaseWrite() {
        when(agentxConversationMapper.selectList(any()))
                .thenReturn(List.of(conversation("conv-1", "9001", "11")));
        when(agentxFileMapper.selectCount(any())).thenReturn(2L);
        when(agentxFileMapper.update(isNull(), any())).thenReturn(1);
        when(agentxFileMapper.selectList(any()))
                .thenReturn(List.of(file("file-1", 11L)));

        assertThrows(FileAccessDeniedException.class,
                () -> service.linkToConversation(
                        List.of("file-1", "file-2"), "conv-1", 11L));
    }

    @Test
    void linkToConversationFailsWhenUpdateCountDoesNotMatchValidatedFiles() {
        when(agentxConversationMapper.selectList(any()))
                .thenReturn(List.of(conversation("conv-1", "9001", "11")));
        when(agentxFileMapper.selectCount(any())).thenReturn(2L);
        when(agentxFileMapper.update(isNull(), any())).thenReturn(1);

        assertThrows(FileAccessDeniedException.class,
                () -> service.linkToConversation(
                        List.of("file-1", "file-2"), "conv-1", 11L));
    }

    @Test
    void linkToSessionRejectsForeignTargetSessionBeforeFileAccess() {
        when(agentxConversationMapper.selectOne(any()))
                .thenReturn(conversation("conv-1", "9001", "22"));

        assertThrows(FileAccessDeniedException.class,
                () -> service.linkToSession(List.of("file-1"), 9001L, "conv-1", 11L));

        verify(agentxFileMapper, never()).selectCount(any());
        verify(agentxFileMapper, never()).update(any(), any());
    }

    @Test
    void linkToSessionRejectsMixedFileOwnershipBeforeAnyWrite() {
        when(agentxConversationMapper.selectOne(any()))
                .thenReturn(conversation("conv-1", "9001", "11"));
        when(agentxFileMapper.selectCount(any())).thenReturn(1L);

        assertThrows(FileAccessDeniedException.class,
                () -> service.linkToSession(
                        List.of("owned-file", "foreign-file"), 9001L, null, 11L));

        verify(agentxFileMapper, never()).update(any(), any());
    }

    @Test
    void linkToSessionUpdatesAllOwnedFilesForOwnedTargetSession() {
        when(agentxConversationMapper.selectOne(any()))
                .thenReturn(conversation("conv-1", "9001", "11"));
        when(agentxFileMapper.selectCount(any())).thenReturn(2L);
        when(agentxFileMapper.update(isNull(), any())).thenReturn(2);
        when(agentxFileMapper.selectList(any()))
                .thenReturn(List.of(file("file-1", 11L), file("file-2", 11L)));

        service.linkToSession(List.of("file-1", "file-2"), 9001L, "conv-1", 11L);

        verify(agentxFileMapper).update(isNull(), any());
    }

    @Test
    void linkToSessionRejectsConversationMismatchBeforeFileAccess() {
        when(agentxConversationMapper.selectOne(any()))
                .thenReturn(conversation("another-conv", "9001", "11"));

        assertThrows(FileAccessDeniedException.class,
                () -> service.linkToSession(List.of("file-1"), 9001L, "conv-1", 11L));

        verify(agentxFileMapper, never()).selectCount(any());
        verify(agentxFileMapper, never()).update(any(), any());
    }

    private static AgentxFile file(String fileId, Long ownerUserId) {
        AgentxFile file = new AgentxFile();
        file.setFileId(fileId);
        file.setOwnerUserId(ownerUserId);
        file.setFileName("note.txt");
        file.setFileType("txt");
        file.setStatus("SUCCESS");
        file.setEmbed(0);
        file.setExtractedText("content");
        return file;
    }

    private static AgentxConversation conversation(String conversationId, String sessionId,
                                                   String userId) {
        AgentxConversation conversation = new AgentxConversation();
        conversation.setConversationId(conversationId);
        conversation.setSessionId(sessionId);
        conversation.setUserId(userId);
        return conversation;
    }
}
