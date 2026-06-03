package com.yuanliu.oncallagent.controller;

import com.yuanliu.oncallagent.config.FileUploadConfig;
import com.yuanliu.oncallagent.dto.FileUploadRes;
import com.yuanliu.oncallagent.service.VectorIndexService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * ClassName:
 * Package: com.yuanliu.oncallagent.controller
 * Description:
 *
 * @Author Yuan Liu
 * @Create 2026/5/26 14:14
 * @Version 1.0
 */
@RestController
@Slf4j
public class FileUploadController {

    @Autowired
    private FileUploadConfig fileUploadConfig;
    @Autowired
    private VectorIndexService vectorIndexService;

    @PostMapping(value="/api/upload",consumes = "multipart/form-data")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        if(file.isEmpty()){
            return ResponseEntity.badRequest().body("文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if(originalFilename == null || originalFilename.isEmpty()){
            return ResponseEntity.badRequest().body("文件名不能为空");
        }

        String fileExtension = getFileExtension(originalFilename);
        if(!isAllowedExtension(fileExtension)){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("不支持的文件格式，仅支持:"+fileUploadConfig.getAllowedExtensions());
        }
        try{
            String uploadPath = fileUploadConfig.getPath();
            Path uploadDir = Paths.get(uploadPath).normalize();
            if(!Files.exists(uploadDir)){
                Files.createDirectories(uploadDir);
            }
            //使用原始文件名，而不是使用UUID，以便实现基于文件名的去重
            Path filePath = uploadDir.resolve(originalFilename).normalize();
            //如果文件已存在，先删除旧文件（实现覆盖更新）
            if(Files.exists(filePath)){
                log.info("文件已存在，将覆盖:{}",filePath);
                Files.delete(filePath);
            }
            Files.copy(file.getInputStream(), filePath);
            log.info("文件上传成功:{}",filePath);
            //文件上传成功后，自动调用向量索引服务
            try{
                log.info("开始为上传文件创建向量索引:{}",filePath);
                vectorIndexService.indexSingleFile(filePath.toString());
                log.info("向量索引创建成功:{}",filePath);
            }catch(Exception e){
                log.error("向量索引创建失败:{},错误:{}",filePath,e.getMessage(),e);
            }
            FileUploadRes response = new FileUploadRes(
                    originalFilename,
                    filePath.toString(),
                    file.getSize()
            );
            ApiResponse<FileUploadRes> apiresponse = new ApiResponse<>();
            apiresponse.setCode(200);
            apiresponse.setMessage("success");
            apiresponse.setData(response);

            return ResponseEntity.ok(apiresponse);
        }catch (Exception e){
            ApiResponse<String> errorResponse = new ApiResponse<>();
            errorResponse.setCode(500);
            errorResponse.setMessage("文件上传失败:"+e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    //统一的API响应格式
    public static class ApiResponse<T>{
        private int code;
        private String message;
        private T data;

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public T getData() {
            return data;
        }

        public void setData(T data) {
            this.data = data;
        }
    }

    private String getFileExtension(String filename) {
        int lastIndexOf = filename.lastIndexOf(".");
        if(lastIndexOf == -1){
            return "";
        }
        return filename.substring(lastIndexOf+1).toLowerCase();
    }

    private boolean isAllowedExtension(String extension){
        String allowedExtensions = fileUploadConfig.getAllowedExtensions();
        if(allowedExtensions == null || allowedExtensions.isEmpty()){
            return false;
        }
        List<String> allowedList = Arrays.asList(allowedExtensions.split(","));
        return allowedList.contains(extension.toLowerCase());
    }
}
