package com.yuanliu.oncallagent.service;
import com.yuanliu.oncallagent.dto.*;
import com.yuanliu.oncallagent.config.DocumentChunkConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ClassName:
 * Package: com.yuanliu.oncallagent.service
 * Description:
 *
 * @Author Yuan Liu
 * @Create 2026/5/22 10:47
 * @Version 1.0
 */
@Service
@Slf4j
public class DocumentChunkService {

    @Autowired
    private DocumentChunkConfig chunkConfig;

    public List<DocumentChunk> chunkDocument(String content,String filePath){
        List<DocumentChunk> chunks = new ArrayList<>();
        if(content==null || content.trim().isEmpty()){
            log.warn("文档内容为空:{}",filePath);
            return chunks;
        }
        //1.尝试按照标题分割
        List<Section> sections = splitByHeadings(content);

        //2.对每一个章节进行分片
        int globalChunkIndex = 0;
        for(Section section : sections){
            List<DocumentChunk> sectionChunks = chunkSection(section,globalChunkIndex);
            chunks.addAll(sectionChunks);
            globalChunkIndex+=sectionChunks.size();
        }
        log.info("文档分片完成: {} -> {} 个分片",filePath,chunks.size());
        return chunks;
    }

    //按照MarkDown标题分割文档
    private List<Section> splitByHeadings(String content) {
        List<Section> sections = new ArrayList<>();
        //匹配Markdown标题: # 标题
        Pattern headingPattern = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = headingPattern.matcher(content);

        int lastEnd = 0;
        String currentTitle = null;

        while (matcher.find()) {
            if (lastEnd < matcher.start()) {
                String sectionContent = content.substring(lastEnd, matcher.start()).trim();
                if (!sectionContent.isEmpty()) {
                    sections.add(new Section(currentTitle, sectionContent, lastEnd));
                }
            }
        }
        currentTitle = matcher.group(2).trim();
        lastEnd = matcher.start();

        if(lastEnd < content.length()){
            String sectionContent = content.substring(lastEnd).trim();
            if(!sectionContent.isEmpty()){
                sections.add(new Section(currentTitle, sectionContent, lastEnd));
            }
        }

        if(sections.isEmpty()){
            sections.add(new Section(null,content,0));
        }
        return sections;
    }

    /**
     * 对单个章节进行分片
     * 分片策略：
     * 1. 如果章节内容小于最大尺寸，直接作为一个分片
     * 2. 否则就按段落分割，依次加入分片
     * 3. 当分片即将超过最大尺寸时，保存当前分片并创建新分片
     * 4. 新分片会包含重叠部分以保持上下文连续性
     */
    private List<DocumentChunk> chunkSection(Section section, int startChunkIndex){
        List<DocumentChunk> chunks = new ArrayList<>();
        String content = section.content;
        String title = section.title;

        //如果章节内容小于最大尺寸，直接作为一个分片
        if(content.length() <= chunkConfig.getMaxSize()){
            DocumentChunk chunk  = new DocumentChunk(
                    content,
                    section.startIndex,
                    section.startIndex+content.length(),
                    startChunkIndex
            );
            chunk.setTitle(title);
            chunks.add(chunk);
            return chunks;
        }
        //章节内容较长，需要进一步分片
        //按照段落分割文本
        List<String> paragraphs = splitByParagraphs(content);

        StringBuilder currentChunk = new StringBuilder();
        int currentStartIndex = section.startIndex;
        int chunkIndex = startChunkIndex;
        for(String paragraph : paragraphs){
            if(currentChunk.length() > 0 && currentChunk.length() + paragraph.length() > chunkConfig.getMaxSize()){
                String chunkContent = currentChunk.toString().trim();
                DocumentChunk chunk  = new DocumentChunk(
                        chunkContent,
                        currentStartIndex,
                        currentStartIndex+chunkContent.length(),
                        chunkIndex++
                );
                chunk.setTitle(title);
                chunks.add(chunk);

                //开始新分片，包含重叠部分
                String overlap = getOverlapText(chunkContent);
                currentChunk = new StringBuilder(overlap);
                currentStartIndex = currentStartIndex + chunkContent.length() - overlap.length();
            }
            currentChunk.append(paragraph).append("\n\n");
        }

        //保存最后一个分片
        if(currentChunk.length() > 0){
            String chunkcontent = currentChunk.toString().trim();
            DocumentChunk chunk  = new DocumentChunk(
                    chunkcontent,
                    currentStartIndex,
                    currentStartIndex+chunkcontent.length(),
                    chunkIndex
            );
            chunk.setTitle(title);
            chunks.add(chunk);
        }
        return chunks;
    }

    //按照段落分割文本
    private List<String> splitByParagraphs(String content){
        List<String> paragraphs = new ArrayList<>();

        //按照双换行分割段落
        String[] parts = content.split("\n\n+");
        for(String part:parts){
            String trimmed = part.trim();
            if(!trimmed.isEmpty()){
                paragraphs.add(trimmed);
            }
        }
        return paragraphs;
    }

    /**
     * 获取重叠文本
     * 从文本末尾提取指定长度的内容作为下一个分片的开头
     */
    private String getOverlapText(String text){
        int overlapSize = Math.min(chunkConfig.getOverlap(),text.length());
        if(overlapSize<=0){
            return "";
        }
        //从文本末尾提取重叠内容
        String overlap = text.substring(text.length()-overlapSize);
        int lastSentenceEnd = Math.max(
                overlap.lastIndexOf('。'),
                Math.max(overlap.lastIndexOf('？'),overlap.lastIndexOf('！'))
        );
        if(lastSentenceEnd < overlapSize/2){
            return overlap.substring(lastSentenceEnd+1).trim();
        }
        return overlap.trim();
    }

    //章节数据类
    private static class Section{
        String title;
        String content;
        int startIndex;

        Section(String title,String content,int startIndex){
            this.title = title;
            this.content = content;
            this.startIndex = startIndex;
        }
    }
}
