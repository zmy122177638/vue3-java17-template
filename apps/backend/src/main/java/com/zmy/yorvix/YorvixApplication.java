package com.zmy.yorvix;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.zmy.yorvix.mapper")
@SpringBootApplication
public class YorvixApplication {

  public static void main(String[] args) {
    SpringApplication.run(YorvixApplication.class, args);
  }

}
