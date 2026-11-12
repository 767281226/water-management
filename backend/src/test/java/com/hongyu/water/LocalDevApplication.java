package com.hongyu.water;

import java.io.File;

import org.springframework.boot.SpringApplication;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * 本机没有安装 PostgreSQL 时的开发启动方式：先启动内嵌 PostgreSQL（数据保存在 target/dev-pgdata），再启动应用。
 *
 * <pre>
 * mvn spring-boot:test-run
 * </pre>
 *
 * 已有 PostgreSQL 时请直接运行 {@link WaterApplication}。
 */
public class LocalDevApplication {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getProperty("dev.pg.port", "15432"));
        File dataDir = new File("target/dev-pgdata");
        boolean dataDirExists = dataDir.exists();
        EmbeddedPostgres pg = EmbeddedPostgres.builder()
                .setPort(port)
                .setDataDirectory(dataDir)
                .setCleanDataDirectory(false)
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                pg.close();
            } catch (Exception ignored) {
                // 退出时关闭失败无需处理
            }
        }));
        System.out.println("内嵌 PostgreSQL 已启动：localhost:" + port + "（" + (dataDirExists ? "沿用" : "新建") + "数据目录 "
                + dataDir.getAbsolutePath() + "）");

        SpringApplication app = new SpringApplication(WaterApplication.class);
        app.setDefaultProperties(java.util.Map.of(
                "spring.datasource.url", pg.getJdbcUrl("postgres", "postgres"),
                "spring.datasource.username", "postgres",
                "spring.datasource.password", ""));
        app.run(args);
    }
}
