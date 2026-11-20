package com.pangruixin.llm.mentor.agentx.domain.vo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pangruixin.llm.mentor.agentx.domain.entities.AgentxFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class FileResponseLeakTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void uploadResponseDoesNotExposeMinioPathOrObjectUrl() throws Exception {
        AgentxFile file = fileWithMinioPath();

        String json = objectMapper.writeValueAsString(UploadResponse.from(file));

        assertNoObjectStorageLeak(json);
    }

    @Test
    void sessionAttachmentInfoDoesNotExposeMinioPathOrObjectUrl() throws Exception {
        AgentxFile file = fileWithMinioPath();
        SessionMessageVO.AttachmentInfo attachment = new SessionMessageVO.AttachmentInfo(
                file.getFileId(), file.getFileName(), file.getFileType(), file.getFileSize());

        String json = objectMapper.writeValueAsString(List.of(attachment));

        assertNoObjectStorageLeak(json);
    }

    private static void assertNoObjectStorageLeak(String json) {
        String lower = json.toLowerCase();
        assertFalse(lower.contains("minio"), json);
        assertFalse(lower.contains("minio_path"), json);
        assertFalse(lower.contains("rag-test2"), json);
        assertFalse(lower.contains("http://"), json);
        assertFalse(lower.contains("https://"), json);
    }

    private static AgentxFile fileWithMinioPath() {
        AgentxFile file = new AgentxFile();
        file.setFileId("file-1");
        file.setFileName("note.txt");
        file.setFileType("txt");
        file.setFileSize(12L);
        file.setStatus("SUCCESS");
        file.setEmbed(0);
        file.setMinioPath("http://<你的MinIO地址>:19000/rag-test2/file-1.txt");
        return file;
    }
}
