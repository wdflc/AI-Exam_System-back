package com.atguigu.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class BaseEntity implements Serializable {

    @Schema(description = "主键")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "修改时间")
    //这个注解是为了去适配时区
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    //对于修改时间这个字段 其实是可以需要的同时也可以不需要 所以我们最好加上
    //@JsonIgnore注解 让他去避免返回 这个字段
    @JsonIgnore
    private Date updateTime;

    @Schema(description = "逻辑删除")
    @TableLogic
    @TableField("is_deleted")
    private Byte isDeleted;

}