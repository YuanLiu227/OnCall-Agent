package com.yuanliu.oncallagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * ClassName:
 * Package: com.yuanliu.oncallagent.config
 * Description:
 *
 * @Author Yuan Liu
 * @Create 2026/5/22 10:45
 * @Version 1.0
 */
@Configuration
@ConfigurationProperties(prefix = "document.chunk")
public class DocumentChunkConfig {
    //每个分片的最大字符数
    private int maxSize=800;
    //分片之间的重叠字符数
    private int overlap=100;

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public int getOverlap() {
        return overlap;
    }

    public void setOverlap(int overlap) {
        this.overlap = overlap;
    }
}
