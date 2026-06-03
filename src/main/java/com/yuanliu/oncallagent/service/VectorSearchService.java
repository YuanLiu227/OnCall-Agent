package com.yuanliu.oncallagent.service;

import ch.qos.logback.classic.Logger;
import com.yuanliu.oncallagent.constant.MilvusConstants;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.naming.directory.SearchResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ClassName:
 * Package: com.yuanliu.oncallagent.service
 * Description:
 *
 * @Author Yuan Liu
 * @Create 2026/5/26 13:30
 * @Version 1.0
 */
@Service
@Slf4j
public class VectorSearchService {

    @Autowired
    private MilvusServiceClient milvusClient;
    @Autowired
    private VectorEmbeddingService embeddingService;

    /**
     * 搜索相似的文档
     */
    public List<SearchResult> searchSimilarDocuments(String query,int topK){
        try{
            log.info("开始搜索相似的文档,查询:{},topK:{}",query,topK);
            //1.将查询文本向量化
            List<Float> queryVector = embeddingService.generateQueryVector(query);
            log.debug("查询向量生成成功，维度:{}",queryVector.size());
            //2.构建搜索参数
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withVectorFieldName("vector")
                    .withVectors(Collections.singletonList(queryVector))
                    .withTopK(topK)
                    .withMetricType(MetricType.L2)
                    .withOutFields(List.of("id","content","metadata"))
                    .withParams("{\"nprobe\":10}")
                    .build();
            //3.执行向量搜索
            R<RpcStatus> loadResponse = milvusClient.loadCollection(
                    LoadCollectionParam.newBuilder()
                            .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                            .build()
            );
            if(loadResponse.getStatus()!=0 && loadResponse.getStatus()!=65535){
                throw new RuntimeException("加载Collection失败:"+loadResponse.getMessage());
            }
            R<SearchResults> searchResponse = milvusClient.search(searchParam);

            if(searchResponse.getStatus()!=0){
                throw new RuntimeException("向量搜索失败:"+searchResponse.getMessage());
            }

            //4.解析搜索结果
            SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResponse.getData().getResults());
            List<SearchResult> results = new ArrayList<>();

            for(int i=0;i<wrapper.getRowRecords(0).size();i++){
                SearchResult result = new SearchResult();
                result.setId((String)wrapper.getIDScore(0).get(i).get("id"));
                result.setContent((String)wrapper.getFieldData("content",0).get(i));
                result.setScore(wrapper.getIDScore(0).get(i).getScore());
                Object metadataObj = wrapper.getFieldData("metadata",0).get(i);
                if(metadataObj !=null){
                    result.setMetadata(metadataObj.toString());
                }
                results.add(result);
            }
            log.info("搜索完成，找到{}个相似文档",results.size());
            return results;
        }catch(Exception e){
            log.error("搜索相似文档失败",e);
            throw new RuntimeException("搜索失败:"+e.getMessage(),e);
        }
    }
    /**
     * 搜索结果类
     */
    @Getter
    @Setter
    public static class SearchResult {
        private String id;
        private String content;
        private float score;
        private String metadata;
    }
}
