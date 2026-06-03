package com.yuanliu.oncallagent.client;

import ch.qos.logback.classic.Logger;
import com.yuanliu.oncallagent.config.MilvusProperties;
import com.yuanliu.oncallagent.constant.MilvusConstants;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.constant.MilvusClientConstant;
import io.milvus.grpc.DataType;
import io.milvus.param.*;
import io.milvus.param.collection.CollectionSchemaParam;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.index.CreateIndexParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * ClassName:
 * Package: com.yuanliu.oncallagent.client
 * Description:
 *
 * @Author Yuan Liu
 * @Create 2026/5/22 9:21
 * @Version 1.0
 */
@Component
@Slf4j
public class MilvusClientFactory {

    @Autowired
    private MilvusProperties milvusProperties;

    /**
     * 创建并且初始化Milvus客户端
     */
    public MilvusServiceClient createClient() {
        MilvusServiceClient client = null;
        try{
            //1.连接到Milvus
            log.info("正在连接到Milvus:{}:{}",milvusProperties.getHost(),milvusProperties.getPort());
            client = connectToMilvus();
            log.info("成功连接到了Milvus");
            //2.检查并且创建biz collection(如果不存在)
            if(!collectionExists(client,MilvusConstants.MILVUS_COLLECTION_NAME)){
                log.info("collection '{}' 不存在，正在创建...", MilvusConstants.MILVUS_COLLECTION_NAME);
                createBizCollection(client);
                log.info("成功创建collection '{}'", MilvusConstants.MILVUS_COLLECTION_NAME);
                //创建索引
                createIndexes(client);
                log.info("成功创建索引");
            }else{
                log.info("collection '{}' 已经存在",MilvusConstants.MILVUS_COLLECTION_NAME);
            }
            return client;
        }catch (Exception e){
            log.error("创建Milvus客户端失败",e);
            if(client != null){
                client.close();
            }
            throw new RuntimeException("创建Milvus客户端失败:"+e.getMessage(),e);
        }
    }

    /**
     * 连接到Milvus
     */
    private MilvusServiceClient connectToMilvus(){
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(milvusProperties.getHost())
                .withPort(milvusProperties.getPort())
                .withConnectTimeout(milvusProperties.getTimeout(), TimeUnit.MILLISECONDS);
        //如果配置了用户名和密码
        if(milvusProperties.getUsername()!=null && milvusProperties.getPassword()!=null){
            builder.withAuthorization(milvusProperties.getUsername(),milvusProperties.getPassword());
        }

        return new MilvusServiceClient(builder.build());
    }
    /**
     * 检查Collection是否存在
     */
    private boolean collectionExists(MilvusServiceClient client,String collectionName){
        R<Boolean> response = client.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build());
        if(response.getStatus()!=0){
            throw new RuntimeException("检查 collection 失败:"+response.getMessage());
        }
        return response.getData();
    }
    /**
     * 创建biz collection
     */
    private void createBizCollection(MilvusServiceClient client){
        //id
        FieldType idFiled = FieldType.newBuilder()
                .withName("id")
                .withDataType(DataType.VarChar)
                .withMaxLength(MilvusConstants.ID_MAX_LENGTH)
                .withPrimaryKey(true)
                .build();
        //vector
        FieldType vectorField = FieldType.newBuilder()
                .withName("vector")
                .withDataType(DataType.FloatVector)
                .withDimension(MilvusConstants.VECTOR_DIM)
                .build();
        //content
        FieldType contentField = FieldType.newBuilder()
                .withName("content")
                .withDataType(DataType.VarChar)
                .withMaxLength(MilvusConstants.CONTENT_MAX_LENGTH)
                .build();
        //metadata
        FieldType metadataField = FieldType.newBuilder()
                .withName("metadata")
                .withDataType(DataType.JSON)
                .build();

        CollectionSchemaParam schema = CollectionSchemaParam.newBuilder()
                .withEnableDynamicField(false)
                .addFieldType(idFiled)
                .addFieldType(vectorField)
                .addFieldType(contentField)
                .addFieldType(metadataField)
                .build();
        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                .withDescription("Business knowledge collection")
                .withSchema(schema)
                .withShardsNum(MilvusConstants.DEFAULT_SHARD_NUMBER)
                .build();
        R<RpcStatus> response = client.createCollection(createParam);
        if(response.getStatus()!=0){
            throw new RuntimeException("创建Collection失败:"+response.getMessage());
        }
    }
    /**
     * 为collection创建索引
     */
    private void createIndexes(MilvusServiceClient client){
        CreateIndexParam vectorIndexParam = CreateIndexParam.newBuilder()
                .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                .withFieldName("vector")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.L2)
                .withExtraParam("{\"nlist\":128}")
                .withSyncMode(Boolean.FALSE)
                .build();
        R<RpcStatus> response = client.createIndex(vectorIndexParam);
        if(response.getStatus()!=0){
            throw new RuntimeException("创建 vector 索引失败:"+response.getMessage());
        }
        log.info("成功为vector字段创建索引");
    }
}
