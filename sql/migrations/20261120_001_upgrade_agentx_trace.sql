-- Idempotent migration for the locked agentx-core 1.0.1-M2 TraceStore contract.
-- Each ALTER is guarded by information_schema so MySQL 8.0 does not need ADD COLUMN IF NOT EXISTS.

SET @missing_user_id := (
  SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agentx_trace' AND COLUMN_NAME = 'user_id'
);
SET @sql := IF(@missing_user_id,
  'ALTER TABLE agentx_trace ADD COLUMN user_id VARCHAR(100) DEFAULT NULL COMMENT ''用户ID''',
  'SELECT ''agentx_trace.user_id already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @missing_span_type := (
  SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agentx_trace' AND COLUMN_NAME = 'span_type'
);
SET @sql := IF(@missing_span_type,
  'ALTER TABLE agentx_trace ADD COLUMN span_type VARCHAR(8) NOT NULL DEFAULT ''LLM'' COMMENT ''Span类型：LLM模型调用 | TOOL工具执行 | COMPACT上下文压缩''',
  'SELECT ''agentx_trace.span_type already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @missing_tool_name := (
  SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agentx_trace' AND COLUMN_NAME = 'tool_name'
);
SET @sql := IF(@missing_tool_name,
  'ALTER TABLE agentx_trace ADD COLUMN tool_name VARCHAR(128) DEFAULT NULL COMMENT ''工具名（TOOL span）''',
  'SELECT ''agentx_trace.tool_name already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @missing_tool_call_id := (
  SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agentx_trace' AND COLUMN_NAME = 'tool_call_id'
);
SET @sql := IF(@missing_tool_call_id,
  'ALTER TABLE agentx_trace ADD COLUMN tool_call_id VARCHAR(100) DEFAULT NULL COMMENT ''模型tool_calls调用ID（TOOL span）''',
  'SELECT ''agentx_trace.tool_call_id already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @missing_compact_id := (
  SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agentx_trace' AND COLUMN_NAME = 'compact_id'
);
SET @sql := IF(@missing_compact_id,
  'ALTER TABLE agentx_trace ADD COLUMN compact_id BIGINT DEFAULT NULL COMMENT ''上级COMPACT span id（上下文压缩摘要LLM用）''',
  'SELECT ''agentx_trace.compact_id already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @created_at_precision := (
  SELECT SUBSTRING_INDEX(SUBSTRING_INDEX(COLUMN_TYPE, '(', -1), ')', 1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agentx_trace' AND COLUMN_NAME = 'created_at'
);
SET @sql := IF(@created_at_precision = '3',
  'SELECT ''agentx_trace.created_at already has millisecond precision''',
  'ALTER TABLE agentx_trace MODIFY COLUMN created_at TIMESTAMP(3) NULL DEFAULT NULL COMMENT ''事件创建时间（Java写入，毫秒精度）''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
