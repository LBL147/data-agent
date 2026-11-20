-- Add a trustworthy owner to uploaded files.
-- Historical rows stay NULL unless ownership can be established from reliable evidence;
-- the application denies NULL-owner files by default.

SET @missing_owner_user_id := (
  SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agentx_file' AND COLUMN_NAME = 'owner_user_id'
);
SET @sql := IF(@missing_owner_user_id,
  'ALTER TABLE agentx_file ADD COLUMN owner_user_id BIGINT NULL DEFAULT NULL COMMENT ''文件所有者（sys_user.id）；NULL 表示历史归属未确认，默认拒绝访问'' AFTER file_id',
  'SELECT ''agentx_file.owner_user_id already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @missing_owner_index := (
  SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agentx_file' AND INDEX_NAME = 'idx_agentx_file_owner'
);
SET @sql := IF(@missing_owner_index,
  'CREATE INDEX idx_agentx_file_owner ON agentx_file (owner_user_id)',
  'SELECT ''agentx_file.idx_agentx_file_owner already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
