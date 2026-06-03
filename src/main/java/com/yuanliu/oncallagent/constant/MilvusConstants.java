package com.yuanliu.oncallagent.constant;

/**
 * ClassName:
 * Package: com.yuanliu.oncallagent.constant
 * Description:
 *
 * @Author Yuan Liu
 * @Create 2026/5/22 9:48
 * @Version 1.0
 */
public class MilvusConstants {
    //Milvus数据库名称
    public static final String MILVUS_DB_NAME = "default";
    //Milvus集合名称
    public static final String MILVUS_COLLECTION_NAME = "biz";
    //向量维度
    public static final int VECTOR_DIM = 1024;
    //ID字段最大长度
    public static final int ID_MAX_LENGTH = 256;
    //Content字段最大长度
    public static final int CONTENT_MAX_LENGTH =8192;
    //默认分片数
    public static final int DEFAULT_SHARD_NUMBER = 2;

    private MilvusConstants() {
    }
}
