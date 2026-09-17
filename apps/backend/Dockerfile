# ============================================================
# 后端（Spring Boot）生产镜像：多阶段构建
#
# 构建上下文是【apps/backend】：
#   docker build -t yorvix-api apps/backend
# ============================================================
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /build

# 先只复制依赖描述文件，让依赖层能命中缓存：改源码不会触发重新下载依赖
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# 再复制源码并打包（测试交给 CI，镜像构建只保证可编译打包）
COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

FROM eclipse-temurin:17-jre AS runtime

WORKDIR /app

# 以非 root 运行
RUN useradd --system --uid 1001 appuser

COPY --from=builder /build/target/*.jar app.jar
RUN chown appuser:appuser app.jar
USER appuser

EXPOSE 4838

# 生产必须激活 prod profile：关闭种子账号、关闭接口文档、收敛日志级别、
# 要求显式配置跨域来源。表结构由 Flyway 在启动时自动迁移。
# 详见 src/main/resources/application-prod.yaml
ENV SPRING_PROFILES_ACTIVE=prod

# 容器内按可用内存比例设置堆，避免写死 -Xmx
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
