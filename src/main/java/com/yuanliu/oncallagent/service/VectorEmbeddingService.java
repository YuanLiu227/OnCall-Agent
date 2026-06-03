package com.yuanliu.oncallagent.service;

import com.alibaba.dashscope.embeddings.*;
import com.alibaba.dashscope.utils.Constants;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ClassName:
 * Package: com.yuanliu.oncallagent.service
 * Description:
 *
 * @Author Yuan Liu
 * @Create 2026/5/25 9:18
 * @Version 1.0
 */
@Service
@Slf4j
public class VectorEmbeddingService {

    @Value("${dashscope.api.key}")
    private String apiKey;
    @Value("${dashscope.embedding.model}")
    private String model;

    private TextEmbedding textEmbedding;

    @PostConstruct
    public void init(){
        //验证 API Key
        if(apiKey == null || apiKey.trim().isEmpty()){
            log.error("API Key 配置错误! 当前值:{}",apiKey);
            throw new IllegalStateException("请配置好API Key");
        }
        //打印API Key用于调试，但是不打印完整的API Key 用于保证安全性
        String maskedKey = apiKey.length() > 13?
                apiKey.substring(0, 8)+"..."+apiKey.substring(apiKey.length()-4):"***";
        log.info("API Key 已加载:{}",maskedKey);

        //设置全局API Key，确保设置成功
        Constants.apiKey = apiKey;

        //验证API Key 是否设置成功
        if(Constants.apiKey == null || Constants.apiKey.isEmpty()){
            log.error("Constants.apiKey 设置失败!");
            throw new IllegalStateException("API Key设置到Constants失败");
        }

        log.info("Constants.apiKey 已设置:{}",Constants.apiKey.substring(0,Math.min(8,Constants.apiKey.length()))+"...");

        //创建TextEmbedding实例
        textEmbedding = new TextEmbedding();

        log.info("阿里云 DashScope Embedding 服务初始化完成，模型:{}",model);
    }

    //生成向量嵌入
    public List<Float> generateEmbedding(String content){
        try{
            if(content == null || content.trim().isEmpty()){
                log.warn("内容为空，无法生成向量");
                throw new IllegalArgumentException("内容不能为空");
            }
            log.debug("开始生成向量嵌入，内容长度：{}字符",content.length());
            //确保API Key 已经设置(防止被其他地方覆盖)
            if(Constants.apiKey == null || Constants.apiKey.isEmpty()){
                log.warn("检测到 Constants.apiKey 为空，重新设置");
                Constants.apiKey = apiKey;
            }
            log.debug("调用 API 前 Constants.apiKey:{}",
                    Constants.apiKey!=null? Constants.apiKey.substring(0,Math.min(8,Constants.apiKey.length()))+"...":"null");

            //构建请求参数
            TextEmbeddingParam param = TextEmbeddingParam
                    .builder()
                    .apiKey(apiKey)
                    .model(model)
                    .texts(Collections.singletonList(content))
                    .build();
            TextEmbeddingResult result = textEmbedding.call(param);
            List<Float> floatEmbedding = getFloats(result);
            log.info("成功生成向量嵌入，内容长度:{}字符，向量维度:{}",content.length(),floatEmbedding.size());
            return floatEmbedding;
        }catch(Exception e){
            log.error("生成向量嵌入失败！！");
            throw new RuntimeException("生成向量嵌入失败:"+e.getMessage(),e);
        }
    }

    private static List<Float> getFloats(TextEmbeddingResult result){
        if(result == null || result.getOutput() == null || result.getOutput().getEmbeddings() ==null)
                throw new RuntimeException("DashScope API 返回空结果");
        TextEmbeddingOutput output = result.getOutput();
        List<TextEmbeddingResultItem> embeddings = output.getEmbeddings();

        if(embeddings.isEmpty())
            throw new RuntimeException("DashScope API 返回空向量列表");

        //获取第一个文本的向量
        List<Double> embeddingDoubles = embeddings.get(0).getEmbedding();

        //转换为 List<Float>
        List<Float> floatEmbedding = new ArrayList<>(embeddingDoubles.size());
        for(Double value:embeddingDoubles){
            floatEmbedding.add(value.floatValue());
        }
        return floatEmbedding;
    }
    //生成查询向量
    public List<Float> generateQueryVector(String query){
        return generateEmbedding(query);
    }
}
