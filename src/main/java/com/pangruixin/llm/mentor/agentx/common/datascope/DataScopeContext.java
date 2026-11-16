package com.pangruixin.llm.mentor.agentx.common.datascope;

import com.pangruixin.llm.mentor.agentx.common.enums.DataScope;

import java.util.List;

/**
 * 数据权限解析结果（DataScopeResolver 产出，DataScopeRewriter 消费）。
 */
public record DataScopeContext(Long userId, DataScope scope, List<Long> deptIdList) {
}
