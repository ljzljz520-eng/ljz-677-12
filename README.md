# Excel数据导入系统

基于 Spring Boot + Vue 3 的Excel大数据导入系统，支持5万条数据导入及上报国家平台功能。

## 技术栈

- **Frontend**: Vue 3 + Element Plus + Tailwind CSS + Pinia
- **Backend**: Spring Boot 3.2 + MyBatis Plus + EasyExcel
- **Database**: MySQL 8.0
- **Security**: Spring Security + JWT + BCrypt加密

## 核心功能

- Excel文件上传与解析（支持5万条数据，使用EasyExcel SAX模式避免OOM）
- **导入前字段校验**：必填字段（医保编号、姓名、项目编码、金额、就诊日期、机构编码）存在性检查，金额与就诊日期格式校验，错误行逐行列出
- **校验关卡**：校验不过时整批拒绝入库，不能进入上送步骤；支持下载错误行，修正后重新上传
- 数据校验通过后批量导入
- 数据上送国家平台（模拟，上送前复检字段）
- 异常数据处理与导出
- 用户登录认证（密码BCrypt加密）

## 启动指南

### 1. 确保 Docker Desktop 已启动

### 2. 在根目录执行

```bash
docker compose up -d --build
```

### 3. 等待容器启动完成（首次构建约3-5分钟）

查看日志：

```bash
docker compose logs -f
```

## 服务地址

| 服务        | 地址                                  |
| ----------- | ------------------------------------- |
| Frontend    | http://localhost:3000                 |
| Backend API | http://localhost:8080                 |
| Swagger文档 | http://localhost:8080/swagger-ui.html |
| Database    | localhost:3306                        |

## 测试账号

| 用户名 | 密码     |
| ------ | -------- |
| admin  | admin123 |

## 项目结构

```
677/
├── backend/                    # Spring Boot后端
│   ├── src/main/java/com/excel/
│   │   ├── config/            # 配置类
│   │   ├── controller/        # 控制器
│   │   ├── dto/               # 数据传输对象
│   │   ├── entity/            # 实体类
│   │   ├── listener/          # EasyExcel监听器
│   │   ├── mapper/            # MyBatis Mapper
│   │   ├── service/           # 服务层
│   │   └── utils/             # 工具类
│   └── Dockerfile
├── frontend/                   # Vue 3前端
│   ├── src/
│   │   ├── api/               # API接口
│   │   ├── assets/            # 静态资源
│   │   ├── components/        # 组件
│   │   ├── router/            # 路由
│   │   ├── stores/            # Pinia状态管理
│   │   └── views/             # 页面
│   └── Dockerfile
└── docker-compose.yml          # 容器编排
```

## API接口

### 认证接口

- `POST /api/auth/login` - 用户登录

### Excel接口

- `POST /api/excel/import` - 导入Excel文件（先校验后入库，校验不过整批拒绝）
- `GET /api/excel/records` - 获取导入记录
- `GET /api/excel/data/{batchNo}` - 获取批次数据
- `GET /api/excel/template` - 下载导入模板
- `POST /api/excel/report/{batchNo}` - 上送数据到国家平台（上送前复检字段）
- `GET /api/excel/report/failed/{batchNo}` - 获取上送失败数据
- `POST /api/excel/report/retry/{batchNo}` - 重试上送
- `GET /api/excel/export/validation-errors/{batchNo}` - 下载校验错误行（修正后可重新上传）
- `GET /api/excel/export/errors/{batchNo}` - 导出上送失败数据

## 数据导入模板

| 字段     | 说明                       | 是否必填 |
| -------- | -------------------------- | -------- |
| 医保编号 | 医保编号                   | 是       |
| 姓名     | 姓名（最多50字符）         | 是       |
| 项目编码 | 项目编码                   | 是       |
| 金额     | 数值，如 1000.00，不能为负 | 是       |
| 就诊日期 | yyyy-MM-dd、yyyy/MM/dd等   | 是       |
| 机构编码 | 机构编码                   | 是       |
| 数据编号 | 唯一标识                   | 否       |
| 身份证号 | 18位身份证号               | 否       |
| 手机号   | 11位手机号                 | 否       |
| 地址     | 地址（最多200字符）        | 否       |
| 备注     | 备注信息                   | 否       |

## 注意事项

1. 系统使用EasyExcel的SAX模式解析Excel，内存占用低，支持大文件
2. 导入采用两段式处理：第一遍只做字段校验（不写库），全部通过后第二遍才批量入库；任一行校验不过则整批拒绝导入
3. 金额/就诊日期按原始字符串接收后逐行校验，格式错误（如金额含非法字符、日期格式不对）会逐行列出行号与原因
4. 校验不过的批次不会产生业务数据，不能进入上送步骤；可下载错误行Excel（列布局与模板一致，末尾附行号和错误原因），修正后重新上传
5. 数据每1000条批量入库，保证性能
6. 上送国家平台为模拟功能，会随机产生5%的失败率用于测试异常处理；上送前会复检必填字段，存在脏数据时拒绝上送
7. 密码使用BCrypt加密存储，与数据库密码加密方式一致
