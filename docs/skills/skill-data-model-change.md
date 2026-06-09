# Skill: 数据模型变更

## 适用场景

新增表、修改字段、创建索引等数据库变更。

## 输入

- 变更描述（新增/修改/删除）
- 字段定义
- 影响范围评估

## 输出

SQL 迁移脚本 + 实体类更新。

## 执行步骤

### 1. 编写 SQL 迁移脚本

放在 `backend/src/main/resources/db/migration/` 目录：

```sql
-- V001__add_deployment_record.sql
-- 创建部署记录表

CREATE TABLE IF NOT EXISTS deployment_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id BIGINT NOT NULL,
    artifact_id BIGINT,
    status VARCHAR(20) DEFAULT 'PENDING',
    deploy_url VARCHAR(500),
    metadata TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_message (message_id),
    FOREIGN KEY (message_id) REFERENCES message(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

命名规范：`V{版本号}__{描述}.sql`

### 2. 更新实体类

```java
// model/entity/DeploymentRecord.java
@Data
@TableName("deployment_record")
public class DeploymentRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long messageId;
    private Long artifactId;
    private String status;
    private String deployUrl;
    private String metadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

### 3. 创建/更新 Mapper

```java
// repository/DeploymentRecordMapper.java
@Mapper
public interface DeploymentRecordMapper extends BaseMapper<DeploymentRecord> {
}
```

### 4. 更新 schema.sql

将迁移脚本内容同步到 `schema.sql`，保持完整 schema 定义。

### 5. 测试验证

```bash
# 本地测试
mysql -u root -p agenthub < src/main/resources/db/migration/V001__add_deployment_record.sql

# 验证表结构
mysql -u root -p agenthub -e "DESC deployment_record;"
```

## 常用变更模式

### 新增表

```sql
CREATE TABLE new_table (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    -- 字段定义
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 新增字段

```sql
ALTER TABLE existing_table ADD COLUMN new_column VARCHAR(100) AFTER existing_column;
```

### 新增索引

```sql
-- 对于大表，使用 pt-online-schema-change
ALTER TABLE message ADD INDEX idx_conversation_time (conversation_id, created_at);
```

### 修改字段

```sql
-- 注意：MySQL 8.0+ 支持 ALTER TABLE ... MODIFY COLUMN
ALTER TABLE message MODIFY COLUMN content TEXT;
```

## 回滚策略

每个迁移脚本需要提供回滚 SQL：

```sql
-- V001__add_deployment_record.sql

-- 回滚
-- DROP TABLE IF EXISTS deployment_record;
```

## 字段命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 表名 | snake_case | `deployment_record` |
| 主键 | `id` 或 `{table_singular}_id` | `id`, `user_id` |
| 外键 | `{ref_table}_id` | `conversation_id` |
| 时间 | `{action}_at` | `created_at`, `updated_at` |
| 布尔 | `is_` 或 `has_` | `is_enabled` |
| 状态 | `{noun}_status` | `deploy_status` |

## 数据迁移

如果变更涉及数据迁移，需要编写迁移脚本：

```sql
-- V002__migrate_user_data.sql

-- 1. 备份原数据
UPDATE user SET nickname = username WHERE nickname IS NULL;

-- 2. 添加新字段默认值
ALTER TABLE user ADD COLUMN display_name VARCHAR(100);

-- 3. 填充数据
UPDATE user SET display_name = nickname;
```

## 注意事项

1. **事务**：每个迁移脚本尽量独立，不要依赖其他脚本
2. **可回滚**：必须提供回滚 SQL
3. **大表变更**：使用 pt-online-schema-change 工具
4. **索引命名**：`idx_{table}_{column}` 或 `idx_{table}_{columns}`
5. **字符集**：统一使用 utf8mb4

## 检查清单

- [ ] 迁移脚本命名正确 (V{数字}__{描述}.sql)
- [ ] 迁移脚本有回滚方案
- [ ] 实体类与表结构一致
- [ ] Mapper 已创建/更新
- [ ] schema.sql 已同步
- [ ] 本地测试通过
- [ ] 外键约束正确
