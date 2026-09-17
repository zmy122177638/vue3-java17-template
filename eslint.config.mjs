import { defineConfig } from '@vben/eslint-config';

export default defineConfig([
  {
    // Maven 构建产物（apps/backend/target）不是源码。
    // @vben/eslint-config 的忽略列表沿袭自 vben 前端仓库，不含 target，
    // 于是执行过 ./mvnw 之后，target/classes 下的资源副本（如 application.yaml）
    // 会被当成源文件检查——`pnpm lint` 会因构建产物报错，且很难看出原因。
    // 写在这里而不是 internal/ 里：这是本模板新增 Java 后端才出现的问题，
    // 属应用级配置，不该污染共享的 lint 配置包。
    ignores: ['**/target/**'],
  },
]);
