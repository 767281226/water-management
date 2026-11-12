package com.hongyu.water;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 宏禹水利管理软件 —— 后端服务入口。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class WaterApplication {

    public static void main(String[] args) {
        SpringApplication.run(WaterApplication.class, args);
    }
}
