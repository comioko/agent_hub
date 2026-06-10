-- Agent delegation support fields
ALTER TABLE agent ADD COLUMN capabilities VARCHAR(500) DEFAULT NULL COMMENT '能力标签，如 backend,frontend,review';
ALTER TABLE agent ADD COLUMN can_delegate BOOLEAN DEFAULT TRUE COMMENT '是否可以委托任务给其他 Agent';

-- Conversation supervisor field
ALTER TABLE conversation ADD COLUMN supervisor_agent_id BIGINT DEFAULT NULL;
