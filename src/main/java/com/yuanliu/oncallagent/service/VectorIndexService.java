package com.yuanliu.oncallagent.service;

import com.google.gson.JsonObject;
import com.yuanliu.oncallagent.constant.MilvusConstants;
import com.yuanliu.oncallagent.dto.DocumentChunk;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.highlevel.dml.response.InsertResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * ClassName:
 * Package: com.yuanliu.oncallagent.controller
 * Description:
 *
 * @Author Yuan Liu
 * @Create 2026/5/22 8:46
 * @Version 1.0
 */
@Slf4j
@Service
public class VectorIndexService {

    @Autowired
    private MilvusServiceClient milvusClient;
    @Autowired
    private DocumentChunkService chunkService;
    @Autowired
    private VectorEmbeddingService embeddingService;

    public void indexSingleFile(String filePath) throws Exception {
        Path path = Paths.get(filePath).normalize();
        File file = path.toFile();
        if(!file.exists() || !file.isFile()){
            throw new IllegalArgumentException("文件不存在:"+filePath);
        }
        //1.读取文件内容
        log.info("开始索引文件:{}",path);
        String content = Files.readString(path);
        log.info("读取文件：{}，内容长度：{}字符",path,content.length());
        //2.如果存在的话，删除该文件的旧数据
        deleteExistingData(path.toString());
        //3.文档分片
        List<DocumentChunk> chunks = chunkService.chunkDocument(content,path.toString());
        //4.为每个分片生成向量并插入 Milvus
        for(int i=0; i<chunks.size() ;i++){
            DocumentChunk chunk = chunks.get(i);
            try{
                //生成向量
                List<Float> vector = embeddingService.generateEmbedding(chunk.getContent());
                //构建元数据(包含文件信息)
                Map<String,Object> metadata = buildMetadata(path.toString(),chunk,chunks.size());
                //插入到Milvus
                insertToMilvus(chunk.getContent(),vector,metadata,chunk.getChunkIndex());
                log.info("分片{}/{}插入成功",i+1,chunks.size());
            }catch(Exception e){
                log.info("分片{}/{}插入失败",i+1,chunks.size());
                throw new RuntimeException("分片插入失败:"+e.getMessage(),e);
            }
            log.info("文件分片完成并插入成功:{},共{}个分片",filePath,chunks.size());
        }
    }

    private void deleteExistingData(String filePath){
        try{
            Path path = Paths.get(filePath).normalize();
            String normalizedPath = path.toString().replace(File.separator,"/");
            String expr = String.format("metadata[\"_source\"]==\"%s\"",normalizedPath);
            log.info("准备删除旧数据，路径:{}，表达式:{}",normalizedPath,expr);
            //删除操作之前需要集合已经加载了
            R<RpcStatus> loadResponse = milvusClient.loadCollection(
                    LoadCollectionParam.newBuilder()
                            .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                            .build()
            );
            //状态码65535表示集合已经加载，不是错误
            if(loadResponse.getStatus()!=0 && loadResponse.getStatus()!=65535){
                log.warn("加载 collection 失败:{}",loadResponse.getMessage());
                return;
            }

            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withExpr(expr)
                    .build();

            R<MutationResult> response = milvusClient.delete(deleteParam);
            if(response.getStatus()!=0){
                log.warn("删除旧数据时出现警告:{}",response.getMessage());
            }else{
                long deleteCount = response.getData().getDeleteCnt();
                log.info("已删除文件的旧数据:{}，删除记录数:{}",normalizedPath,deleteCount);
            }
        }catch(Exception e){
            log.warn("删除旧数据失败:{}",e.getMessage());
        }
    }

    //构建元数据(包含文件信息)

    /**
     *  {
     *       "_source": "C:/docs/report.pdf",
     *       "_extension": ".pdf",
     *       "_file_name": "report.pdf",
     *       "chunkIndex": 2,
     *       "totalChunks": 5,
     *       "title": "概述"
     *   }
     */
    private Map<String,Object> buildMetadata(String filePath, DocumentChunk chunk, int totalChunks){
        Map<String,Object> metadata = new HashMap<>();
        Path path  = Paths.get(filePath).normalize();
        String normalizedPath = path.toString().replace(File.separator,"/");
        Path fileName = path.getFileName();
        String fileNameStr = fileName!=null?fileName.toString():"";
        String extension="";
        int dotIndex = fileNameStr.lastIndexOf(".");
        if(dotIndex>0){
            extension = fileNameStr.substring(dotIndex);
        }

        metadata.put("_source",normalizedPath);
        metadata.put("_extension",extension);
        metadata.put("_file_name",fileNameStr);
        metadata.put("chunkIndex",chunk.getChunkIndex());
        metadata.put("totalChunks",totalChunks);
        if(chunk.getTitle()!=null && !chunk.getTitle().isEmpty()){
            metadata.put("title",chunk.getTitle());
        }
        return metadata;
    }

    //插入向量到 Milvus
    private void insertToMilvus(String content,List<Float>vector,Map<String,Object> metadata,int chunkIndex) throws Exception{
        try{
            R<RpcStatus> loadResponse = milvusClient.loadCollection(
                    LoadCollectionParam.newBuilder()
                            .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                            .build()
            );
            if(loadResponse.getStatus()!=0 && loadResponse.getStatus()!=65535){
                throw new RuntimeException("加载Collection失败:"+loadResponse.getMessage());
            }
            //生成唯一ID
            String source = (String)metadata.get("_source");
            String id = UUID.nameUUIDFromBytes((source+"_"+chunkIndex).getBytes()).toString();
            //构建字段数据
            List<InsertParam.Field> fields = new ArrayList<>();
            fields.add(new InsertParam.Field("id",Collections.singletonList(id)));
            fields.add(new InsertParam.Field("content",Collections.singletonList(content)));
            fields.add(new InsertParam.Field("vector",Collections.singletonList(vector)));
            //metadata字段(JSON 对象)
            com.google.gson.Gson gson = new com.google.gson.Gson();
            JsonObject metadataJson = gson.toJsonTree(metadata).getAsJsonObject();
            fields.add(new InsertParam.Field("metadata",Collections.singletonList(metadataJson)));
            //构建插入参数
            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withFields(fields)
                    .build();
            //执行插入
            R<MutationResult> insertResponse = milvusClient.insert(insertParam);
            if(insertResponse.getStatus()!=0){
                throw new RuntimeException("插入向量失败:"+ insertResponse.getMessage());
            }

            log.info("向量插入成功: id={}, source={}, chunk={}",id,source,chunkIndex);

        }catch (Exception e){
            log.error("插入向量到Milvus失败",e);
            throw e;
        }
    }
}
