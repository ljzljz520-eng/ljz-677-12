# Excel数据导入系统

基于 Spring Boot + Vue 3 的Excel大数据导入系统，支持5万条数据导入及上报国家平台功能。

## 技术栈

- **Frontend**: Vue 3 + Element Plus + Tailwind CSS + Pinia
- **Backend**: Spring Boot 3.2 + MyBatis Plus + EasyExcel
- **Database**: MySQL 8.0
- **Security**: Spring Security + JWT + BCrypt加密

## 核心功能

- Excel文件上传与解析（支持5万条数据，使用EasyExcel SAX模式避免OOM）
- 数据校验与批量导入
- 数据上报国家平台（模拟）
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

- `POST /api/excel/validate` - 导入前字段校验（不写入数据库）
- `GET /api/excel/validate/errors/{validationId}` - 下载校验错误行
- `POST /api/excel/import` - 导入Excel文件（导入前自动校验，不过则整批取消）
- `GET /api/excel/records` - 获取导入记录
- `GET /api/excel/data/{batchNo}` - 获取批次数据
- `GET /api/excel/template` - 下载导入模板
- `POST /api/excel/report/{batchNo}` - 上报数据到国家平台（上送前再次校验）
- `GET /api/excel/report/failed/{batchNo}` - 获取上报失败数据
- `POST /api/excel/report/retry/{batchNo}` - 重试上报
- `GET /api/excel/export/errors/{batchNo}` - 导出错误数据

## 数据导入模板

| 字段     | 说明                                                         | 是否必填 |
| -------- | ------------------------------------------------------------ | -------- |
| 医保编号 | 医保编号                                                     | 是       |
| 姓名     | 姓名（最多50字符）                                           | 是       |
| 项目编码 | 项目编码                                                     | 是       |
| 金额     | 合法数值，不能为负（支持千分位逗号、￥/¥/元符号）             | 是       |
| 就诊日期 | 日期（支持 yyyy-MM-dd、yyyy/M/d、yyyyMMdd、yyyy年M月d日 及Excel日期序列号） | 是       |
| 机构编码 | 机构编码                                                     | 是       |
| 身份证号 | 18位身份证号                                                 | 否       |
| 手机号   | 11位手机号                                                   | 否       |
| 备注     | 备注信息                                                     | 否       |

## 导入前校验流程

1. 上传文件后先点击「字段校验」，系统检查医保编号、姓名、项目编码、金额、就诊日期、机构编码是否存在，并校验金额与日期格式。
2. 校验不通过时页面列出错误行（含行号与错误原因），并可下载完整错误行Excel（含行号、错误原因），修正后重新上传。
3. 存在错误行时禁止导入，数据不入库，也不会进入上送环节。
4. 正式导入时后端会再次执行同样校验作为关卡；上送国家平台前还会对批次数据再校验一次，不通过则禁止上送。

## 注意事项

1. 系统使用EasyExcel的SAX模式解析Excel，内存占用低，支持大文件
2. 数据每1000条批量入库，保证性能
3. 上报国家平台为模拟功能，会随机产生5%的失败率用于测试异常处理
4. 密码使用BCrypt加密存储，与数据库密码加密方式一致
