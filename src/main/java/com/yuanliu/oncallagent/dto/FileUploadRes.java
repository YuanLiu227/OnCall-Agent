package com.yuanliu.oncallagent.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * ClassName:
 * Package: com.yuanliu.oncallagent.dto
 * Description:
 *
 * @Author Yuan Liu
 * @Create 2026/5/26 14:48
 * @Version 1.0
 */
@Getter
@Setter
public class FileUploadRes {
    private String fileName;
    private String filePath;
    private Long fileSize;

    public FileUploadRes() {
    }

    public FileUploadRes(String fileName, String filePath, Long fileSize) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
    }
}
