# Skill: Java API 开发

## 适用场景

新增 REST API 接口，包括 Controller、Service、Mapper 的完整实现。

## 输入

- 功能描述
- HTTP 方法和路径设计
- 请求/响应数据结构

## 输出

完整的 Controller、Service、Mapper 代码。

## 执行步骤

### 1. 分析需求

确定以下内容：
- HTTP 方法（GET/POST/PUT/DELETE）
- API 路径（遵循 RESTful 规范）
- 是否需要鉴权
- 请求参数和响应格式

### 2. 创建 DTO

请求 DTO（放在 `model/dto/`）：
```java
@Data
public class CreateXxxRequest {
    @NotNull(message = "field is required")
    private Long field;

    private String optionalField;
}
```

响应 VO（使用通用 ApiResponse<T> 包装）：
```java
// 直接使用已有的 VO 类，或创建新的
public class XxxVO {
    private Long id;
    private String name;
}
```

### 3. 创建/更新 Mapper

```java
// repository/XxxMapper.java
@Mapper
public interface XxxMapper extends BaseMapper<XxxEntity> {
}
```

如需复杂 SQL，创建 XML 文件：
```xml
<!-- resources/mapper/XxxMapper.xml -->
<mapper namespace="com.agenthub.repository.XxxMapper">
    <select id="complexQuery" resultType="XxxVO">
        SELECT * FROM xxx WHERE ...
    </select>
</mapper>
```

### 4. 创建 Service

```java
// service/XxxService.java
@Service
public class XxxService {

    private final XxxMapper xxxMapper;

    public XxxService(XxxMapper xxxMapper) {
        this.xxxMapper = xxxMapper;
    }

    @Transactional
    public XxxVO createXxx(CreateXxxRequest request) {
        // 1. 业务校验
        // 2. 转换 Entity
        // 3. 插入数据库
        // 4. 转换返回 VO
    }

    public XxxVO getXxx(Long id) {
        XxxEntity entity = xxxMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "Xxx not found");
        }
        return convertToVO(entity);
    }
}
```

### 5. 创建 Controller

```java
// controller/XxxController.java
@RestController
@RequestMapping("/api/xxxs")
public class XxxController {

    private final XxxService xxxService;

    public XxxController(XxxService xxxService) {
        this.xxxService = xxxService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<XxxVO>> createXxx(
            @Valid @RequestBody CreateXxxRequest request,
            @AuthenticationPrincipal User currentUser) {

        XxxVO result = xxxService.createXxx(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<XxxVO>> getXxx(@PathVariable Long id) {
        XxxVO result = xxxService.getXxx(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
```

### 6. 更新 API 文档

在 `SPEC_API.md` 中添加新的端点说明。

## 注意事项

1. **参数校验**：使用 `@Valid` 和 `@NotNull` 等注解
2. **异常处理**：使用 `BusinessException`，禁止吞异常
3. **事务管理**：写操作加 `@Transactional`
4. **鉴权**：需要登录的接口使用 `@AuthenticationPrincipal User currentUser`
5. **禁止**：Controller 中禁止写业务逻辑

## 代码模板

```java
// 1. Request DTO
@Data
public class CreateSomethingRequest {
    @NotBlank(message = "Name is required")
    private String name;

    private String description;
}

// 2. VO (如需新建)
@Data
public class SomethingVO {
    private Long id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
}

// 3. Mapper (如需新建)
@Mapper
public interface SomethingMapper extends BaseMapper<SomethingEntity> {
}

// 4. Service
@Service
public class SomethingService {
    private final SomethingMapper somethingMapper;

    @Transactional
    public SomethingVO create(CreateSomethingRequest request) {
        SomethingEntity entity = new SomethingEntity();
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        somethingMapper.insert(entity);
        return convertToVO(entity);
    }

    private SomethingVO convertToVO(SomethingEntity entity) {
        SomethingVO vo = new SomethingVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}

// 5. Controller
@RestController
@RequestMapping("/api/somethings")
public class SomethingController {
    private final SomethingService somethingService;

    @PostMapping
    public ResponseEntity<ApiResponse<SomethingVO>> create(
            @Valid @RequestBody CreateSomethingRequest request) {
        return ResponseEntity.ok(ApiResponse.success(somethingService.create(request)));
    }
}
```

## 检查清单

- [ ] DTO 有完整的字段注释
- [ ] Service 有事务注解
- [ ] Controller 参数有 @Valid
- [ ] 异常使用 BusinessException
- [ ] 已更新 SPEC_API.md
- [ ] 有单元测试（如需要）
