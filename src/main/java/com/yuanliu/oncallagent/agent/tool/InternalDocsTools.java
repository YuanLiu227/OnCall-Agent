package com.yuanliu.oncallagent.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuanliu.oncallagent.service.VectorSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ClassName:
 * Package: com.yuanliu.oncallagent.agent.tool
 * Description:
 * 内部文档查询工具
 * 使用RAG从内部知识库检索相关文档
 * @Author Yuan Liu
 * @Create 2026/6/1 9:53
 * @Version 1.0
 */
@Component
@Slf4j
public class InternalDocsTools {

    // 工具名常量，用于动态构建提示词
    public static final String TOOL_QUERY_INTERNAL_DOCS = "queryInternalDocs";

    @Autowired
    private VectorSearchService vectorSearchService;

    @Value("3")
    private int topK=3;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 内部文档查询工具
     */
    @Tool(description = "Use this tool to search internal documentation and knowledge base for relevant information."+
    "It performs RAG (Retrieval-Augmented Generation) to find similar documents and extract processing steps."+
    "This is useful when you need to understand internal procedures, best practices, or step-by-step guides."+
    "stored in the company's documentation.")
    public String queryInternalDocs(@ToolParam(description = "Search query describing what information you are looking for")  String query) {
        try{
            //使用向量搜索服务检索相关文档
            List<VectorSearchService.SearchResult> searchResults = vectorSearchService.searchSimilarDocuments(query,topK);

            if(searchResults.isEmpty()){
                return "{\"status\":\"no_results\",\"message\":\"No relevant documents found in the knowledge base.\"}";
            }

            //将搜索结果转换为JSON格式
            String resultJson = objectMapper.writeValueAsString(searchResults);

            return resultJson;
        }catch(Exception e){
            log.error("[工具错误] queryInternalDocs 执行失败",e);
            return String.format("{\"status\":\"error\",\"message\":\"Failed to query internal docs:%s\"}",e.getMessage());
        }
    }
}
