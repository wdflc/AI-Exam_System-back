package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.Banner;
import com.atguigu.exam.mapper.BannerMapper;
import com.atguigu.exam.service.BannerService;

import com.atguigu.exam.service.FileUploadService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 轮播图服务实现类
 */
@Service
@Slf4j
public class BannerServiceImpl extends ServiceImpl<BannerMapper, Banner> implements BannerService {

    private final FileUploadService fileUploadService;

    public BannerServiceImpl(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @Override
    public String uploadImage(MultipartFile file) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
       if(file.isEmpty()){
           throw new RuntimeException("要上传的文件为空，不能够上传");
       }
        String contentType = file.getContentType();
       if(ObjectUtils.isEmpty(contentType)||!contentType.startsWith("image")){
           throw new RuntimeException("轮播图只能上传图片文件！");
       }
       // 1024byte*1024*5 = 5MB
       if(file.getSize()>5*1024*1024){
           throw new RuntimeException("图片文件大小不能超过5MB");
       }
       String imgUrl = fileUploadService.uploadFile(file,"banners");
       log.info("完成banners图片上传，图片的回显地址为:{}",imgUrl);
       return imgUrl;
    }

    @Override
    public void addBanner(Banner banner) {
        if(ObjectUtils.isEmpty(banner)){
            banner.setIsActive(true);
        }

        if(ObjectUtils.isEmpty(banner.getIsActive())){
            banner.setSortOrder(0);
        }
        boolean isSuccess = save(banner);
        if(!isSuccess){
            throw new RuntimeException("轮播图保存失败！");
        }
        log.info("轮播图保存成功！");
    }

    @Override
    public void updateBanner(Banner banner) {
        boolean success = this.updateById(banner);
        if(!success){
            throw new RuntimeException("轮播图更新失败");
        }
    }
}