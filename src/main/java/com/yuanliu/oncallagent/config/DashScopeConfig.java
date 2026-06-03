package com.yuanliu.oncallagent.config;

/**
 * ClassName:
 * Package: com.yuanliu.oncallagent.config
 * Description:
 *
 * @Author Yuan Liu
 * @Create 2026/6/2 16:24
 * @Version 1.0
 */

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * DashScope API 配置
 * 用于配置超时事件等参数
 */
@Configuration
public class DashScopeConfig {

    @Value("${spring.ai.dashscope.chat.options.timeout}")
    private long timeout;

    @Bean
    @SuppressWarnings("deprecation")
    public RestClient.Builder restClientBuilder() {
        //创建自定义的 OKHttpClient，设置超时时间
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(timeout))
                .readTimeout(Duration.ofMillis(timeout))
                .writeTimeout(Duration.ofMillis(timeout))
                .callTimeout(Duration.ofMillis(timeout))
                .build();
        //创建RestClient.Builder 并且 配置 OkHttpClient
        return RestClient.builder().requestFactory(new OkHttp3ClientHttpRequestFactory(okHttpClient));
    }
}
