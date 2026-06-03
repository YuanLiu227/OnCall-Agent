package com.yuanliu.oncallagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * ClassName:
 * Package: com.yuanliu.oncallagent.config
 * Description:
 *
 * @Author Yuan Liu
 * @Create 2026/5/22 9:16
 * @Version 1.0
 */
@Configuration
@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {
    private String host="localhost";
    private Integer port=19530;
    private String username="";
    private String password="";
    private String database="default";
    private Long timeout=10000L;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public Long getTimeout() {
        return timeout;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }
}
