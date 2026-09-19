package com.team22.aiguanli;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.team22.aiguanli.mapper")
public class AiguanliApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiguanliApplication.class, args);
    }
}
