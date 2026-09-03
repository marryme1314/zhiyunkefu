# 本机已用 Maven 打好 jar 时使用（国内网络更稳）。
# 构建前：cd backend && mvn -DskipTests package
FROM docker.m.daocloud.io/library/eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --create-home app || adduser -S app
COPY target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","app.jar"]
