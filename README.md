# 供应商到客户管理系统

这是一个 Spring Boot + Vue 的第一版业务原型，包含三个登录入口：供应商、客户、总后台。

## 当前环境检查

- 已检测到 Java 8，可运行本项目使用的 Spring Boot 2.7。
- 已检测到 Node.js 20 和 npm 10。
- 未检测到命令行 Maven。如果 IDEA 自带 Maven，可以直接用 IDEA 打开 `backend/pom.xml` 运行；否则需要安装 Maven 并配置到 PATH。
- 未检测到命令行 Git，不影响本项目运行。
- `npm install` 访问官方源超时，已在 `frontend/.npmrc` 配置国内镜像。

## 默认账号

| 入口 | 账号 | 密码 |
| --- | --- | --- |
| 总后台 | admin | 123456 |
| 供应商 | supplier01 | 123456 |
| 客户 | customer01 | 123456 |

首次登录后可以在页面右上区域修改密码。正式使用前请先修改默认密码。

## 后端运行

用 IDEA 打开 `backend/pom.xml`，等待 Maven 依赖加载完成后，运行：

```text
com.suppliercustomer.Application
```

后端地址：

```text
http://localhost:8080
```

开发数据库默认使用 H2 文件库，数据会保存在：

```text
backend/data/
```

H2 控制台：

```text
http://localhost:8080/h2-console
```

连接信息：

```text
JDBC URL: jdbc:h2:file:./data/supplier_customer
User Name: sa
Password: 留空
```

## 前端运行

进入 `frontend` 目录后执行：

```bash
npm install
npm run dev
```

前端地址：

```text
http://localhost:5173
```

前端源码位置：

- 页面都在 `frontend/src/views/`：登录、总后台、供应商、客户各一个页面。
- 接口连接只看 `frontend/src/request.js`：里面配置了 `baseURL`、token 请求头、后端 `Result` 返回处理。
- 路由在 `frontend/src/router/index.js`。
- 公共样式在 `frontend/src/style.css`。
- 产品字段配置在 `frontend/src/data/productFields.js`。
- `node_modules` 是 npm 自动下载的依赖目录，文件很多是正常的，不需要阅读或修改。

## 使用 MySQL

项目默认用 H2，方便马上跑。你电脑有 MySQL 的话，按下面做：

1. 在 MySQL 里执行：

```sql
source D:/供应商到客户/backend/mysql-init.sql;
```

如果你的 MySQL 工具不支持 `source`，就打开 [mysql-init.sql](D:/供应商到客户/backend/mysql-init.sql)，复制全部 SQL 执行。

2. 修改 [application-mysql.properties](D:/供应商到客户/backend/src/main/resources/application-mysql.properties)：

```properties
spring.datasource.username=root
spring.datasource.password=你的MySQL密码
```

3. IDEA 运行后端时，在 VM options 或 Program arguments 里加：

```text
--spring.profiles.active=mysql
```

## 已实现的业务

- 三入口登录：供应商、客户、总后台。
- 账号管理：总后台添加账号、删除账号、设置角色和权限字段。
- 修改密码：总后台、供应商、客户均可修改自己的密码。
- 总后台六表查看：
  - 总表 `master_products`
  - 供应商提交产品表 `supplier_submissions`
  - 客户订单表 `customer_orders`
  - 客户产品明细表 `customer_order_items`
  - 客户未匹配表 `unmatched_customer_items`
  - 供应商报价表 `supplier_quotes`
- 供应商提交产品，后台补编码后审核进入总表，并记录供应商账号。
- 客户下载 Excel 模板、上传 Excel，系统自动生成订单编号并记录客户账号。
- 客户上传产品后按编码自动匹配总表；匹配不到的进入未匹配表，供总后台人工补编码。
- 总后台可按订单生成供应商报价任务，供应商填写采购价，总后台后续维护销售价。
