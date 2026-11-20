package com.pangruixin.llm.mentor.agentx;

import com.agentx.ai.core.model.PauseState;
import com.agentx.ai.core.model.RunnableParams;
import com.pangruixin.llm.mentor.agentx.service.FileAccessDeniedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResumeOwnershipGuardTest {

    @Test
    void matchingTrustedUserAndToolParamAreAllowed() {
        PauseState state = state("11", "11");

        assertDoesNotThrow(() -> ResumeOwnershipGuard.requireOwner(state, 11L));
    }

    @Test
    void mismatchedPauseOwnerIsRejected() {
        PauseState state = state("1", "1");

        FileAccessDeniedException error = assertThrows(FileAccessDeniedException.class,
                () -> ResumeOwnershipGuard.requireOwner(state, 11L));

        assertEquals("会话不存在或无权访问", error.getMessage());
    }

    @Test
    void missingToolParamIdentityIsRejected() {
        PauseState state = state("11", null);

        assertThrows(FileAccessDeniedException.class,
                () -> ResumeOwnershipGuard.requireOwner(state, 11L));
    }

    @Test
    void mismatchedToolParamIdentityIsRejected() {
        PauseState state = state("11", "1");

        assertThrows(FileAccessDeniedException.class,
                () -> ResumeOwnershipGuard.requireOwner(state, 11L));
    }

    @Test
    void missingCurrentUserIsRejected() {
        assertThrows(FileAccessDeniedException.class,
                () -> ResumeOwnershipGuard.requireOwner(state("11", "11"), null));
    }

    private static PauseState state(String paramsUserId, String toolUserId) {
        RunnableParams.Builder params = RunnableParams.builder()
                .conversationId("conv-1")
                .userId(paramsUserId);
        if (toolUserId != null) {
            params.addToolParam("userId", toolUserId);
        }
        return PauseState.builder()
                .params(params.build())
                .build();
    }
}
