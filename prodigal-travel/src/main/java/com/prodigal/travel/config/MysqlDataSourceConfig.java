package com.prodigal.travel.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 主数据源（MySQL）：用户 / 会话 / 聊天记录等业务数据。
 * 引入 pgvector 的 DataSource Bean 后，Spring Boot 数据源自动配置会退让，
 * 因此必须显式声明并标记 @Primary，否则 MyBatis-Plus 会误用 PostgreSQL。
 */
@Configuration
public class MysqlDataSourceConfig {

    @Bean(name = "mysqlDataSourceProperties")
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSourceProperties mysqlDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "mysqlDataSource")
    public DataSource mysqlDataSource(
            @Qualifier("mysqlDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }
}
