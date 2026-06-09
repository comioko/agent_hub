-- V2: Add pinned and archived fields to conversation table
ALTER TABLE conversation ADD COLUMN pinned BOOLEAN DEFAULT FALSE;
ALTER TABLE conversation ADD COLUMN archived BOOLEAN DEFAULT FALSE;

-- Add index for efficient querying
ALTER TABLE conversation ADD INDEX idx_conversation_owner_pinned (owner_id, pinned, archived);

-- Add pinned and status fields to message table
ALTER TABLE message ADD COLUMN pinned BOOLEAN DEFAULT FALSE;
ALTER TABLE message ADD COLUMN status VARCHAR(20) DEFAULT 'COMPLETED';

-- Add index for pinned messages query
ALTER TABLE message ADD INDEX idx_message_conversation_pinned (conversation_id, pinned);
