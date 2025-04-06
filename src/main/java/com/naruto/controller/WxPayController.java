package com.naruto.controller;


import com.naruto.model.vo.R;
import com.naruto.service.WxPayService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Map;

/**
 * 微信支付控制层
 *
 * @Author: naruto
 * @CreateTime: 2025-04-06-17:59
 */
@Api("微信支付相关接口")
@CrossOrigin
@RestController
@RequestMapping("/api/wx-pay")
@Slf4j
public class WxPayController {

    @Resource
    private WxPayService wxPayService;

    @ApiOperation("调用统一下单API，生成支付二维码")
    @PostMapping("/native/{productId}")
    public R nativePay(@PathVariable Long productId) throws IOException {
        log.info("发起支付请求");

        // 返回支付二维码和订单号
        Map<String, Object> map = wxPayService.nativePay(productId);
        return R.ok().setData(map);
    }
}
