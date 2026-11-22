package com.sdas.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SDAS 后端服务启动类。
 *
 * <p>仅负责启动 Spring 容器，不包含任何业务逻辑。</p>
 */
@SpringBootApplication
public class SdasServerApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(SdasServerApplication.class);

    /**
     * 应用入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SdasServerApplication.class, args);
        LOGGER.info("SDAS server started successfully");
    }
}
