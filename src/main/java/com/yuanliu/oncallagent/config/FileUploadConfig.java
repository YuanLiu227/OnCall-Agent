package com.yuanliu.oncallagent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * ClassName:
 * Package: com.yuanliu.oncallagent.config
 * Description:
 *
 * @Author Yuan Liu
 * @Create 2026/5/26 14:17
 * @Version 1.0
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "file.upload")
public class FileUploadConfig {
    private String path;
    private String allowedExtensions;

}
