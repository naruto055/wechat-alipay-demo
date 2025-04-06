package com.naruto.config;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @Author: naruto
 * @CreateTime: 2025-04-05-22:36
 */
@Configuration
@EnableTransactionManagement
@MapperScan("com.naruto.mapper")
public class MybatisPlusConfig {

}
