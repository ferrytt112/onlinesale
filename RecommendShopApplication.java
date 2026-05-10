package com.recommend.shop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.recommend.shop.mapper")
public class RecommendShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecommendShopApplication.class, args);
    }
}
