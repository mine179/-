# 免费部署说明

推荐方案：

- 后端：Render Free Web Service，运行 Spring Boot Docker 服务。
- 前端：Vercel Hobby，运行 Vite/Vue 静态站。

Vercel 适合部署前端，但当前项目后端是 Spring Boot 长驻 Web 服务，不适合直接放到 Vercel Functions。

## 1. 部署后端到 Render

1. 把整个项目上传到 GitHub。
2. 登录 Render，创建 `New` -> `Web Service`。
3. 选择这个 GitHub 仓库。
4. 配置：
   - Root Directory: `backend`
   - Runtime/Language: `Docker`
   - Dockerfile Path: `backend/Dockerfile`
   - Instance Type: `Free`
5. 环境变量：
   - `APP_CORS_ORIGIN`: 先填 `*` 临时测试，前端上线后建议改成 Vercel 域名，例如 `https://xxx.vercel.app`
6. 部署完成后记下后端地址，例如：
   - `https://supplier-customer-backend.onrender.com`

后端接口地址就是：

```text
https://supplier-customer-backend.onrender.com/api
```

## 2. 部署前端到 Vercel

1. 登录 Vercel，导入同一个 GitHub 仓库。
2. 配置：
   - Framework Preset: `Vite`
   - Root Directory: `frontend`
   - Build Command: `npm run build`
   - Output Directory: `dist`
3. 环境变量：
   - `VITE_API_BASE_URL`: 填 Render 后端接口地址，例如 `https://supplier-customer-backend.onrender.com/api`
4. 部署完成后记下前端地址，例如：
   - `https://supplier-customer.vercel.app`

## 3. 回填 CORS

前端部署成功后，回到 Render 后端服务，把环境变量改成：

```text
APP_CORS_ORIGIN=https://supplier-customer.vercel.app
```

然后重新部署后端。

## 免费方案限制

- Render 免费后端空闲一段时间会休眠，第一次打开可能要等约 1 分钟。
- 当前后端默认使用 H2 文件数据库。Render 免费 Web Service 没有持久磁盘，服务重启或重新部署后，本地文件数据可能丢失。只是给别人试看可以；正式使用建议改 MySQL/Postgres。
- Vercel 前端静态站可以免费试看，适合这个 Vue 前端。
