-- Message Version Table (for version history)
CREATE TABLE IF NOT EXISTS `message_version` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `message_id` BIGINT NOT NULL,
    `content` TEXT,
    `version_number` INT NOT NULL DEFAULT 1,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_message` (`message_id`),
    FOREIGN KEY (`message_id`) REFERENCES `message`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
