package com.yuanliu.oncallagent.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * ClassName:
 * Package: com.yuanliu.oncallagent.dto
 * Description:
 *
 * @Author Yuan Liu
 * @Create 2026/5/22 10:48
 * @Version 1.0
 */
@Setter
@Getter
public class DocumentChunk {
    //分片内容
    private String content;
    //分片在原文档中的起始位置
    private int startIndex;
    //分片在原文档中的结束位置
    private int endIndex;
    //分片序号（从0开始）
    private int chunkIndex;
    //分片标题或者上下文信息
    private String title;

    public DocumentChunk() {
    }

    public DocumentChunk(String content, int startIndex, int endIndex, int chunkIndex) {
        this.content = content;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.chunkIndex = chunkIndex;
    }

    @Override
    public String toString() {
        return "DocumentChunk{" +
                "content='" + content + '\'' +
                ", startIndex=" + startIndex +
                ", endIndex=" + endIndex +
                ", chunkIndex=" + chunkIndex +
                ", title='" + title + '\'' +
                '}';
    }
}
