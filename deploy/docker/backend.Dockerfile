# 后端镜像：Maven + JDK 21 编译，JRE 21 运行
# 构建上下文为仓库根目录（见 docker-compose.yml）

FROM maven:3.9-eclipse-temurin-21 AS build
# 内网或国内网络可指定 Maven 镜像，如 https://maven.aliyun.com/repository/public
ARG MAVEN_MIRROR=
WORKDIR /src
RUN if [ -n "$MAVEN_MIRROR" ]; then \
      mkdir -p /root/.m2 && printf '<settings><mirrors><mirror><id>mirror</id><mirrorOf>*</mirrorOf><url>%s</url></mirror></mirrors></settings>' "$MAVEN_MIRROR" > /root/.m2/settings.xml; \
    fi
# 先下载依赖，源码变动时可复用缓存层
COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline || true
COPY backend/src src
RUN mvn -B -q -DskipTests package && cp target/water-management-*.jar /app.jar

FROM eclipse-temurin:21-jre
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"
RUN groupadd -g 10001 app && useradd -u 10001 -g app -d /app -s /usr/sbin/nologin app \
    && mkdir -p /app/data/docs /nas && chown -R app:app /app
WORKDIR /app
COPY --from=build --chown=app:app /app.jar /app/app.jar
USER app
EXPOSE 8080
# 不依赖 curl：用 bash 的 /dev/tcp 访问健康检查接口
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health HTTP/1.0\r\n\r\n" >&3 && grep -q UP <&3'
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
