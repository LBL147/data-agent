package com.pangruixin.llm.mentor.agentx;

import com.agentx.ai.core.model.PauseState;
import com.agentx.ai.core.model.RunnableParams;
import com.pangruixin.llm.mentor.agentx.service.FileAccessDeniedException;

/**
 * 恢复中断任务前的归属校验。
 *
 * <p>恢复执行沿用暂停快照中的 RunnableParams，因此必须确认快照属于当前可信用户，
 * 否则攻击者只要猜到 conversationId，就可能以快照原用户身份继续执行工具。
 */
final class ResumeOwnershipGuard {

    private ResumeOwnershipGuard() {
    }

    static void requireOwner(PauseState state, Long currentUserId) {
        RunnableParams params = state == null ? null : state.getParams();
        String expected = currentUserId == null ? null : currentUserId.toString();
        String stateUserId = params == null ? null : params.getUserId();
        Object toolUserId = params == null || params.getToolParams() == null
                ? null
                : params.getToolParams().get("userId");
        if (expected == null
                || !expected.equals(stateUserId)
                || !expected.equals(toolUserId == null ? null : String.valueOf(toolUserId))) {
            throw new FileAccessDeniedException("会话不存在或无权访问");
        }
    }
}
