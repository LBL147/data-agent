package com.pangruixin.llm.mentor.agentx.domain.entities;

/**
 * /agent/stop 响应体。
 */
public record StopResponse(String conversationId, boolean stopped, boolean hasInterruptState) {
}
