package com.yuanliu.oncallagent.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.yuanliu.oncallagent.agent.tool.DateTimeTools;
import com.yuanliu.oncallagent.agent.tool.InternalDocsTools;
import com.yuanliu.oncallagent.agent.tool.QueryLogsTools;
import com.yuanliu.oncallagent.agent.tool.QueryMetricsTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * ClassName:
 * Package: com.yuanliu.oncallagent.service
 * Description:
 *
 * @Author Yuan Liu
 * @Create 2026/6/1 9:52
 * @Version 1.0
 */
@Service
@Slf4j
public class ChatService {

    @Autowired
    private InternalDocsTools internalDocsTools;

    @Autowired
    private DateTimeTools dateTimeTools;

    @Autowired
    private QueryMetricsTools queryMetricsTools;

    @Autowired(required = false)
    private QueryLogsTools queryLogsTools;

    @Autowired
    private ToolCallbackProvider tools;

    @Value("${dashscope.api.key}")
    private String dashScopeApiKey;

    //创建DashScope API 实例
    public DashScopeApi createDashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(dashScopeApiKey)
                .build();
    }
    /**
     * 创建ChatModel
     * temperature:控制随机性，值越低，回答越确定性；值越高，回答越随机多样
     * maxToken: 最大输出长度
     * topP：核采样参数，影响输出随机性，值越多采样越多
     */
    public DashScopeChatModel createChatModel(DashScopeApi dashScopeApi, double temperature, int maxToken, double topP) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                        .withTemperature(temperature)
                        .withMaxToken(maxToken)
                        .withTopP(topP)
                        .build())
                .build();
    }

    //创建标准对话 ChatModel
    public DashScopeChatModel createStandardChatModel(DashScopeApi dashScopeApi){
        return createChatModel(dashScopeApi,0.7,2000,0.9);
    }

    /**
     * 构建系统提示词（包含历史消息）
     * AI根据此提示词理解自己的角色定位核能力范围
     * history：历史消息列表，每个Map包含"role" 和 "content"
     */
    public String buildSystemPrompt(List<Map<String,String>> history){
        StringBuilder systemPromptBuilder = new StringBuilder();
        //==========基础系统提示词================
        //明确告知AI有哪些工具可用以及何时使用
        systemPromptBuilder.append("你是一个专业的智能助手，可以获取当前时间，搜索内部文档知识库以及查询Prometheus告警信息。\n");
        systemPromptBuilder.append("当用户询问时间相关问题时，使用getCurrentDateTime工具。\n");
        systemPromptBuilder.append("当用户需要查询公司内部文档、流程、最佳实践或者技术指南的时候，使用 queryInternalDocs 工具。\n");
        systemPromptBuilder.append("当用户需要查询Prometheus告警、监控指标或系统告警状态时，使用queryPrometheusAlerts工具。\n");
        systemPromptBuilder.append("当用户需要查询腾讯云日志时，请调用腾讯云mcp服务查询，默认查询地域为ap-guangzhou，查询时间范围为近一个月。\n\n");
        //添加历史消息
        if(!history.isEmpty()){
            systemPromptBuilder.append("--- 对话历史 ---\n");
            for(Map<String,String> msg : history){
                String role = msg.get("role");
                String content = msg.get("content");
                if("user".equals(role)){
                    systemPromptBuilder.append("用户: ").append(content).append("\n");
                }else if("assistant".equals(role)){
                    systemPromptBuilder.append("助手: ").append(content).append("\n");
                }
            }
            systemPromptBuilder.append("--- 对话历史记录 ---\n\n");
        }
        systemPromptBuilder.append("请基于以上对话历史，回答用户的新问题。");
        return systemPromptBuilder.toString();
    }

    /**
     * 动态构建方法工具数组
     * 根据cls.mock-enabled 决定是否包含 QueryLogsTools
     */
    public Object[] buildMethodToolsArray(){
        if(queryLogsTools!=null){
            //Mock模式：包含QueryLogsTools
            return new Object[]{dateTimeTools,internalDocsTools,queryMetricsTools,queryLogsTools};
        }else{
            //真实模式：不包含包含QueryLogsTools(由MCP提供日志查询功能)
            return new Object[]{dateTimeTools,internalDocsTools,queryMetricsTools};
        }
    }

    /**
     * 获取工具回调列表，mcp服务提供的工具
     */
    public ToolCallback[] getToolCallbacks(){
        return tools.getToolCallbacks();
    }

    /**
     * 记录可用工具列表，mcp服务提供的工具
     */
    public void logAvailableTools(){
        ToolCallback[] toolCallbacks = tools.getToolCallbacks();
        log.info("可用工具列表:");
        for(ToolCallback toolCallback : toolCallbacks){
            log.info(">>> {}",toolCallback.getToolDefinition().name());
        }
    }

    /**
     * 创建ReactAgent
     */
    public ReactAgent createReactAgent(DashScopeChatModel chatModel, String systemPrompt){
        return ReactAgent.builder()
                .name("intelligent_assistant")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .methodTools(buildMethodToolsArray())
                .tools(getToolCallbacks())
                .build();
    }

    /**
     * 执行ReactAgent对话（非流式）
     */
    public String executeChat(ReactAgent agent, String question) throws GraphRunnerException{
        log.info("执行 ReactAgent.call() - 自动处理工具调用");
        AssistantMessage response = agent.call(question);
        String answer = response.getText();
        log.info("ReactAgent 对话完成，答案长度:{}",answer.length());
        return answer;
    }
}
