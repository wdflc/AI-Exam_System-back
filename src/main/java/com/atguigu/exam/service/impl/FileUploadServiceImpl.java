package com.atguigu.exam.service.impl;

import com.atguigu.exam.config.properties.MinioProperties;
import com.atguigu.exam.service.FileUploadService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * projectName: com.atguigu.exam.service.impl
 *
 * @author: 赵伟风
 * description:
 */
@Service
@Slf4j
public class FileUploadServiceImpl implements FileUploadService {

    @Autowired
    private MinioClient minioClient;
    @Autowired
    private MinioProperties minioProperties;

    @Override
    public String uploadFile(MultipartFile file, String folder) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        //TODO
        //每次上传都要进行一次判断？ 能否在外面去进行判断呢？
        //判断当前的桶名是否存在
        boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioProperties.getBucketName()).build());
        //如果桶不存在 创建桶 然后设置访问的权限
        if(!bucketExists){
            //创建桶
            minioClient.makeBucket((MakeBucketArgs.builder().bucket(minioProperties.getBucketName()).build()));
            String config = """
                        {
                              "Statement" : [ {
                                "Action" : "s3:GetObject",
                                "Effect" : "Allow",
                                "Principal" : "*",
                                "Resource" : "arn:aws:s3:::%s/*"
                              } ],
                              "Version" : "2012-10-17"
                        }
                    """.formatted(minioProperties.getBucketName());
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(minioProperties.getBucketName()).config(config).build());
        }
        //3. 处理上传的对象名（影响，minio桶中的文件结构！）
        //现在： 桶名 / folder / ai.png  缺点： 所有文件都平铺（banner，video）不好区分！ 核心缺点，可能覆盖！
        //小知识点： x/x/x.png -> exam0625 /x/x/ x.png
        //解决覆盖问题： 确保对象和文件的名字唯一即可！！ uuid - - -
        //1.需要添加文件夹 2.添加uuid确保不重复
        String objectName = folder + "/" + new SimpleDateFormat("yyyyMMdd").format(new Date())+"/"
                + UUID.randomUUID().toString().replaceAll("-", "")+"_"
                + file.getOriginalFilename();
        log.debug("文件上传核心方法 处理后的文件对象名为:{}",objectName);
        //4. 上传文件 putObject方法
        //putObject . 上传文件数据 .steam(文件输入流)
        //uploadObject .上传文件数据 .filename(文件的磁盘地址 c:\\)


        //5. 拼接回显地址 【端点 + 桶 + 对象名】
        return "";
    }
}
