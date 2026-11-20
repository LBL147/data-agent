package com.pangruixin.llm.mentor.agentx.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.pangruixin.llm.mentor.agentx.common.R;
import com.pangruixin.llm.mentor.agentx.trace.TraceWriteHealthProbe;
import com.pangruixin.llm.mentor.agentx.trace.TraceWriteHealthProbe.TraceHealthResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评测运行前使用的受保护健康检查端点。
 */
@RestController
@RequestMapping("/api/eval/health")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class EvalHealthController {

    private final TraceWriteHealthProbe traceWriteHealthProbe;

    @GetMapping("/trace")
    public ResponseEntity<R<TraceHealthResult>> traceHealth() {
        TraceHealthResult result = traceWriteHealthProbe.check();
        R<TraceHealthResult> body = result.isUp()
                ? R.ok("trace 写入可用", result)
                : R.ok(result).code(503).msg("trace 写入不可用");
        return ResponseEntity.status(result.isUp() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
