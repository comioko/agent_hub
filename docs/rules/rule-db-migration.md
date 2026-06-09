# Rule: 数据模型变更规则

## 目的

保证数据库变更可追溯、可回滚。

## 变更流程

```
1. 编写迁移脚本 (V{版本}__{描述}.sql)
2. 更新实体类
3. 更新 schema.sql
4. 测试验证
5. 记录回滚方案
```

## 迁移脚本规范

### 命名

```
V{版本号}__{简短描述}.sql
V001__add_deployment_record.sql
V002__add_session_index.sql
V003__modify_message_content_type.sql
```

### 存放位置

```
backend/src/main/resources/db/migration/
```

### 脚本结构

```sql
-- V001__add_deployment_record.sql
-- Description: 创建部署记录表，用于支持部署功能
-- Author: developer
-- Date: 2024-01-15

-- 创建表
CREATE TABLE IF NOT EXISTS deployment_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_message (message_id),
    FOREIGN KEY (message_id) REFERENCES message(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 回滚
-- DROP TABLE IF EXISTS deployment_record;
```

## 字段变更规则

### 新增字段

```sql
ALTER TABLE message ADD COLUMN parent_id BIGINT AFTER conversation_id;
```

### 修改字段

```sql
-- MySQL 8.0+
ALTER TABLE message MODIFY COLUMN content MEDIUMTEXT;
```

### 删除字段（禁止）

禁止直接删除字段，使用 tombstone 模式：

```sql
-- 1. 将字段标记为废弃
ALTER TABLE message ADD COLUMN is_deleted TINYINT(1) DEFAULT 0;

-- 2. 业务上忽略该字段
-- 3. 真正删除需要大版本升级时进行
```

## 索引变更

### 添加索引

```sql
-- 简单索引
ALTER TABLE message ADD INDEX idx_conversation_time (conversation_id, created_at);

-- 唯一索引
ALTER TABLE message ADD UNIQUE INDEX idx_unique_conversation_sender (conversation_id, sender_id);
```

### 大表加索引

使用 pt-online-schema-change：

```bash
pt-online-schema-change \
    --alter "ADD INDEX idx_conversation_time (conversation_id, created_at)" \
    --user=root \
    --password=xxx \
    D=mydb,t=message
```

## 回滚方案

每个迁移必须记录回滚 SQL：

| 变更类型 | 回滚方式 |
|----------|----------|
| 新增表 | DROP TABLE |
| 新增字段 | ALTER TABLE DROP COLUMN |
| 新增索引 | DROP INDEX |
| 修改字段 | 修改回原类型 |

## schema.sql 同步

每次迁移后，必须同步更新 `schema.sql`：

```bash
# 流程
1. 开发环境执行迁移
2. 验证功能正常
3. 更新 schema.sql 反映最新结构
4. 提交代码
```

## 注意事项

1. **事务**：单个迁移脚本尽量在一个事务内完成
2. **可重复执行**：使用 `IF NOT EXISTS` / `CREATE TABLE IF NOT EXISTS`
3. **大表变更**：评估影响，避免锁表
4. **外键**：确保引用的表/字段存在
5. **字符集**：统一使用 utf8mb4

## 检查清单

- [ ] 迁移脚本命名正确
- [ ] 有回滚方案
- [ ] 实体类已更新
- [ ] schema.sql 已同步
- [ ] 本地测试通过
