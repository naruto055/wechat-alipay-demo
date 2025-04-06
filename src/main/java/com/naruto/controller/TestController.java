package com.naruto.controller;


import com.naruto.config.WxPayConfig;
import com.naruto.model.vo.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Date;

/**
 * 测试控制器
 *
 * @Author: naruto
 * @CreateTime: 2025-04-05-21:25
 */
@Api(tags = "测试接口")
@RestController
@RequestMapping("/api/test")
public class TestController {

    @Resource
    private WxPayConfig wxPayConfig;

    @ApiOperation("测试接口")
    @GetMapping("/test")
    public R getWxPayConfig() {

        String mchId = wxPayConfig.getMchId();

        return R
                .ok()
                .data("mchId", mchId)
                .data("now", new Date());
    }


}
