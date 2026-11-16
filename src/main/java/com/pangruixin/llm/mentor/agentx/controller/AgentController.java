package com.pangruixin.llm.mentor.agentx.controller;

import com.pangruixin.llm.mentor.agentx.DodoAgent;
import com.pangruixin.llm.mentor.agentx.auth.service.AuthService;
import com.pangruixin.llm.mentor.agentx.common.R;
import com.pangruixin.llm.mentor.agentx.domain.entities.StopResponse;
import com.pangruixin.llm.mentor.agentx.domain.dto.ChatRequest;
import com.agentx.ai.core.model.AgentStreamEvent;
import com.agentx.ai.core.model.RunnableParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * ReactAgent 总入口
 */
@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final DodoAgent dodoAgent;
    private final AuthService authService;

    /**
     * 流式问答（包含中断恢复功能）。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AgentStreamEvent> stream(@RequestBody ChatRequest request) {
        String convId = StringUtils.hasText(request.conversationId())
                ? request.conversationId()
                : "dodo_conv_" + UUID.randomUUID();

        // 拦截器已校验登录，走到这里 userId 必非空；为空说明 SaTokenContext 异常，直接 fail-fast
        Long userId = authService.getCurrentUserIdOrNull();
        if (userId == null) {
            log.error("[dodo-agentx] 拦截器已通过但 userId 为空，SaTokenContext 异常 | convId={}", convId);
            return Flux.error(new IllegalStateException("用户身份获取失败，请重新登录"));
        }
        String userIdStr = userId.toString();

        boolean online = Boolean.TRUE.equals(request.online());

        // 存在中断快照 → 恢复执行（本次 query 作为 resumeQuery 传入）
        if (dodoAgent.hasInterruptedState(convId)) {
            log.info("[dodo-agentx] 恢复中断会话: convId={}, userId={}, online={}, files={}, resumeQuery={}",
                    convId, userId, online,
                    request.fileIds() == null ? 0 : request.fileIds().size(),
                    request.query());
            return dodoAgent.resumeStream(userId, convId, online, request.fileIds(), request.query());
        }

        // userId 进入 RunnableParams：用于记忆维度，也通过 toolParams 在工具执行前注入。
        RunnableParams params = RunnableParams.builder()
                .conversationId(convId)
                .userId(userIdStr)
                .addToolParam("userId", userIdStr)
                .build();

        log.info("[dodo-agentx] 收到查询: convId={}, userId={}, online={}, files={}, query={}",
                convId, userId, online,
                request.fileIds() == null ? 0 : request.fileIds().size(),
                request.query());
        return dodoAgent.streamForResult(request.query(), params, online, request.fileIds());
    }

    /**
     * 中断指定会话的流式输出（持久化快照，支持后续 resume 继续）。
     */
    @GetMapping("/stop")
    public R<StopResponse> stop(@RequestParam String conversationId) {
        boolean interrupted = dodoAgent.interrupt(conversationId);
        boolean hasState = interrupted && dodoAgent.hasInterruptedState(conversationId);
        log.info("[dodo-agentx] 中断流式: convId={}, interrupted={}, hasState={}", conversationId, interrupted, hasState);
        return R.ok(new StopResponse(conversationId, interrupted, hasState));
    }
}
