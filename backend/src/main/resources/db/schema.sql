CREATE DATABASE IF NOT EXISTS agent_hub DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE agent_hub;

-- User Table
CREATE TABLE IF NOT EXISTS `user` (
                                      `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                                      `username` VARCHAR(50) NOT NULL UNIQUE,
                                      `password_hash` VARCHAR(255) NOT NULL,
                                      `nickname` VARCHAR(100),
                                      `avatar_url` VARCHAR(500),
                                      `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                      `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                      INDEX `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Agent Table
CREATE TABLE IF NOT EXISTS `agent` (
                                       `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                                       `code` VARCHAR(50) NOT NULL UNIQUE,
                                       `name` VARCHAR(100) NOT NULL,
                                       `description` VARCHAR(500),
                                       `avatar_url` VARCHAR(500),
                                       `system_prompt` TEXT,
                                       `provider` VARCHAR(50) NOT NULL,
                                       `provider_model` VARCHAR(100),
                                       `enabled` BOOLEAN DEFAULT TRUE,
                                       `is_orchestrator` BOOLEAN DEFAULT FALSE,
                                       `config_json` TEXT,
                                       `owner_id` BIGINT NULL,
                                       `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                       `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Conversation Table
CREATE TABLE IF NOT EXISTS `conversation` (
                                              `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                                              `title` VARCHAR(200),
                                              `type` VARCHAR(20) NOT NULL DEFAULT 'SINGLE',
                                              `owner_id` BIGINT NOT NULL,
                                              `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                              `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                              INDEX `idx_owner` (`owner_id`),
                                              FOREIGN KEY (`owner_id`) REFERENCES `user`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Conversation Participant Table
CREATE TABLE IF NOT EXISTS `conversation_participant` (
                                                          `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                                                          `conversation_id` BIGINT NOT NULL,
                                                          `user_id` BIGINT,
                                                          `agent_id` BIGINT,
                                                          `role` VARCHAR(20) DEFAULT 'MEMBER',
                                                          `joined_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                                          INDEX `idx_conv` (`conversation_id`),
                                                          INDEX `idx_user` (`user_id`),
                                                          INDEX `idx_agent` (`agent_id`),
                                                          FOREIGN KEY (`conversation_id`) REFERENCES `conversation`(`id`),
                                                          FOREIGN KEY (`user_id`) REFERENCES `user`(`id`),
                                                          FOREIGN KEY (`agent_id`) REFERENCES `agent`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Message Table
CREATE TABLE IF NOT EXISTS `message` (
                                         `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                                         `conversation_id` BIGINT NOT NULL,
                                         `sender_type` VARCHAR(20) NOT NULL,
                                         `sender_id` BIGINT NOT NULL,
                                         `content` TEXT NOT NULL,
                                         `message_type` VARCHAR(20) DEFAULT 'TEXT',
                                         `parent_id` BIGINT,
                                         `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                         INDEX `idx_conv_time` (`conversation_id`, `created_at`),
                                         INDEX `idx_sender` (`sender_id`),
                                         FOREIGN KEY (`conversation_id`) REFERENCES `conversation`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Message Block Table (for artifacts)
CREATE TABLE IF NOT EXISTS `message_block` (
                                               `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                                               `message_id` BIGINT NOT NULL,
                                               `block_type` VARCHAR(30) NOT NULL,
                                               `content` TEXT,
                                               `language` VARCHAR(50),
                                               `metadata` TEXT,
                                               `sort_order` INT DEFAULT 0,
                                               `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                               INDEX `idx_message` (`message_id`),
                                               FOREIGN KEY (`message_id`) REFERENCES `message`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Agent Provider Table
CREATE TABLE IF NOT EXISTS `agent_provider` (
                                                `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
                                                `code` VARCHAR(50) NOT NULL UNIQUE,
                                                `name` VARCHAR(100) NOT NULL,
                                                `api_base` VARCHAR(500),
                                                `api_key_encrypted` VARCHAR(500),
                                                `enabled` BOOLEAN DEFAULT TRUE,
                                                `config_json` TEXT,
                                                `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                                `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Insert default agents
INSERT INTO `agent` (`code`, `name`, `description`, `provider`, `provider_model`, `system_prompt`, `enabled`) VALUES
                                                                                                                  ('assistant', 'Assistant', 'A helpful AI assistant', 'BUILTIN', 'builtin', 'You are a helpful AI assistant. Provide clear, concise, and accurate responses.', TRUE),
                                                                                                                  ('coder', 'Code Assistant', 'Specialized in programming and code review', 'BUILTIN', 'builtin', 'You are an expert programmer. Help write clean, efficient code and provide thorough code reviews.', TRUE),
                                                                                                                  ('reviewer', 'Code Reviewer', 'Focuses on code quality and best practices', 'BUILTIN', 'builtin', 'You are a code reviewer focused on quality, security, and best practices. Provide constructive feedback.', TRUE);

-- Insert default providers
INSERT INTO `agent_provider` (`code`, `name`, `api_base`, `enabled`) VALUES
                                                                         ('BUILTIN', 'Built-in Agent', NULL, TRUE),
                                                                         ('OPENAI', 'OpenAI', 'https://api.openai.com/v1', TRUE),
                                                                         ('ANTHROPIC', 'Anthropic', 'https://api.anthropic.com/v1', TRUE);
