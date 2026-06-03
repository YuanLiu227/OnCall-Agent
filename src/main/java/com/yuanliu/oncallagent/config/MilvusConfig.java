package com.yuanliu.oncallagent.config;

import com.yuanliu.oncallagent.client.MilvusClientFactory;
import io.milvus.client.MilvusServiceClient;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ClassName:
 * Package: com.yuanliu.oncallagent.config
 * Description:
 *
 * @Author Yuan Liu
 * @Create 2026/5/22 9:15
 * @Version 1.0
 */
@Configuration
@Slf4j
public class MilvusConfig {

    @Autowired
    private MilvusClientFactory milvusClientFactory;

    private MilvusServiceClient milvusClient;

    /**
     * 创建MilvusServiceClient Bean
     */
    @Bean
    public MilvusServiceClient milvusServiceClient() {
        log.info("正在初始化Milvus客户端...");
        milvusClient = milvusClientFactory.createClient();
        log.info("Milvus客户端初始化成功");
        return milvusClient;
    }
    /**
     * 应用关闭时清理资源
     */
    @PreDestroy
    public void cleanup(){
        if(milvusClient != null){
            log.info("正在关闭Milvus客户端连接...");
            milvusClient.close();
            log.info("Milvus客户端连接已关闭");
        }
    }
}
