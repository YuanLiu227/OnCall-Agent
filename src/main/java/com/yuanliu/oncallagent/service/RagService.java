package com.yuanliu.oncallagent.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.utils.Constants;
import com.yuanliu.oncallagent.controller.ChatController;
import io.reactivex.Flowable;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ClassName:
 * Package: com.yuanliu.oncallagent.service
 * Description:
 * RAG(Retrieval-Augmented Generation) 服务
 * 结合向量检索和大语言模型生成答案
 * @Author Yuan Liu
 * @Create 2026/6/2 15:23
 * @Version 1.0
 */
@Service
@Slf4j
public class RagService {

    @Autowired
    private VectorSearchService vectorSearchService;

    @Value("${dashscope.api.key}")
    private String apiKey;

    @Value("${rag.top-k}")
    private int topK;

    @Value("${rag.model}")
    private String model;

    private Generation generation;

    @PostConstruct
    public void init() {
        Constants.apiKey = apiKey;
        Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";

        generation = new Generation();

        log.info("RAG 服务初始化完成,model:{},topK:{}", model, topK);
    }


    //流式处理用户问题，带历史消息
    public void queryStream(String question, List<Map<String,String>> history, StreamCallback callback){
        try{
            log.info("收到RAG流式查询:{}",question);
            //1.从向量数据库检索相关文档
            List<VectorSearchService.SearchResult> searchResults =
                    vectorSearchService.searchSimilarDocuments(question, topK);
            //发送检索结果
            callback.onSearchResults(searchResults);
            if(searchResults.isEmpty()){
                log.warn("未找到相关文档");
                callback.onComplete("抱歉，我在知识库中没有找到相关信息来回答你的问题","");
                return;
            }
            //2.构建上下文和提示词
            String context = buildContent(searchResults);
            String prompt = buildPrompt(question,context);

            //3.流式调用大语言模式(传入历史消息）
            generateAnswerStream(prompt,history,callback);

        }catch (Exception e){
            log.error("RAG 流式查询失败",e);
            callback.onError(e);
        }
    }

    //生成答案，流式
    private void generateAnswerStream(String prompt,List<Map<String,String>> history, StreamCallback callback)
    throws Exception{
        //构建消息列表：历史消息+当前问题
        List<Message> messages = new ArrayList<>();

        //添加历史消息
        for(Map<String,String> historyMsg :history){
            String role = historyMsg.get("role");
            String content = historyMsg.get("content");

            if("user".equals(role)){
                messages.add(Message.builder()
                        .role(Role.USER.getValue())
                        .content(content)
                        .build());
            }else if ("assistant".equals(role)){
                messages.add(Message.builder()
                        .role(Role.ASSISTANT.getValue())
                        .content(content)
                        .build());
            }
        }

        //添加当前用户问题
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(prompt)
                .build();
        messages.add(userMsg);

        log.debug("发送给AI模型的消息数量:{}(包含{}条历史消息)", messages.size(),history.size());

        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .incrementalOutput(true)
                .resultFormat("message")
                .messages(messages)
                .build();

        log.info("开始调用AI模型流式接口...");

        Flowable<GenerationResult> result = generation.streamCall(param);

        StringBuilder reasoningContent = new StringBuilder();
        StringBuilder finalContent = new StringBuilder();

        log.info("开始接受AI模型流式响应...");

        result.blockingForEach( message->{
            if(message.getOutput()!=null &&
            message.getOutput().getChoices() !=null &&
            !message.getOutput().getChoices().isEmpty()){
                String content = message.getOutput().getChoices().get(0).getMessage().getContent();
                if(content!=null && !content.isEmpty()){
                    log.debug("收到AI模型内容块:{}",content);
                    finalContent.append(content);
                    callback.onContentChunk(content);
                    log.debug("已调用 onContentChunk 回调");
                }else{
                    log.debug("收到空内容块，跳过");
                }
            }
                }
        );

        log.info("AI模型流式响应完成，总内容长度:{}",finalContent.length());
        callback.onComplete(finalContent.toString(),reasoningContent.toString());
        log.info("已调用 onComplete 回调");

    }

    //构建上下文
    private String buildContent(List<VectorSearchService.SearchResult> searchResults){
        StringBuilder context = new StringBuilder();

        for(int i=0;i<searchResults.size();i++){
            VectorSearchService.SearchResult result = searchResults.get(i);
            context.append("【参考资料").append(i+1).append("】\n");
            context.append(result.getContent()).append("\n\n");
        }

        return context.toString();
    }

    //构建提示词
    private String buildPrompt(String question,String context){
        return String.format(
                "你是一个专业的AI助手。请根据以下参考资料回答用户的问题。\n\n"+
                        "参考资料: \n%s\n"+
                        "用户问题:%s\n\n"+
                        "请基于上述参考资料给出准确、详细的答案。如果参考资料中没有相关信息，请明确说明。",
                context,question
        );
    }

    //流式回调接口
    public interface StreamCallback{
        //AI搜索到结果时调用，通常是RAG检索相关文档
        void onSearchResults(List<VectorSearchService.SearchResult> results);
        //AI思考过程（推理链）的片段，用于流式输出
        void onReasoningChunk(String chunk);
        //AI最终回答内容的片段，用于流式输出
        void onContentChunk(String chunk);
        //AI回答完成时调用，传入完整内容和完整推理过程
        void onComplete(String fullContent,String fullReasoning);
        //出错时调用
        void onError(Exception e);
    }
}
