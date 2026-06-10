-- Add context management fields
ALTER TABLE message ADD COLUMN context_type VARCHAR(20) DEFAULT 'AUTO' COMMENT 'Context type: AUTO, PINNED, EXCLUDED';
ALTER TABLE message ADD COLUMN context_priority INT DEFAULT 0 COMMENT 'Context priority for long-term memory';

-- Create index for context queries
CREATE INDEX idx_message_context ON message(conversation_id, context_type, id);

-- Add context management fields to message_block
ALTER TABLE message_block ADD COLUMN context_type VARCHAR(20) DEFAULT 'AUTO' COMMENT 'Context type: AUTO, PINNED, EXCLUDED';
ALTER TABLE message_block ADD COLUMN context_priority INT DEFAULT 0 COMMENT 'Context priority for long-term memory';
