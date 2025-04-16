package com.naruto.controller;


import com.google.gson.Gson;
import com.naruto.model.vo.R;
import com.naruto.service.WxPayService;
import com.naruto.util.HttpUtils;
import com.naruto.util.WechatPay2ValidatorForRequest;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    // 注入签名验证器，由于在WxPayConfig中，ScheduledUpdateCertificatesVerifier这个类实现的Verify接口，因此直接注入接口就行
    @Resource
    private Verifier verifier;

    @ApiOperation("调用统一下单API，生成支付二维码")
    @PostMapping("/native/{productId}")
    public R nativePay(@PathVariable Long productId) throws IOException {
        log.info("发起支付请求");

        // 返回支付二维码和订单号
        Map<String, Object> map = wxPayService.nativePay(productId);
        return R.ok().setData(map);
    }

    /**
     * 处理微信支付回调发送的通知
     *
     * @param request
     * @param response
     * @return
     * @throws Exception
     */
    @ApiOperation("处理微信支付回调发送的通知")
    @PostMapping("/native/notify")
    public String nativeNotify(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Gson gson = new Gson();
        // 应答对象
        Map<String, String> map = new HashMap<>();

        // 处理通知参数
        String body = HttpUtils.readData(request);
        HashMap<String, Object> bodyMap = gson.fromJson(body, HashMap.class);
        log.info("支付通知的id：{}", bodyMap.get("id"));
        String requestId = (String) bodyMap.get("id");
        log.info("支付通知的完整数据：{}", bodyMap);

        // 签名验证
        WechatPay2ValidatorForRequest wechatPay2ValidatorForRequest = new WechatPay2ValidatorForRequest(verifier, body, requestId);
        boolean validate = wechatPay2ValidatorForRequest.validate(request);
        if (!validate) {
            log.error("通知签名验证失败");
            // 失败应答
            response.setStatus(500);
            map.put("code", "ERROR");
            map.put("message", "签名验证失败");
        }
        log.info("验签成功");

        // 对订单进行处理
        wxPayService.processOrder(bodyMap);

        // 应答超时
        TimeUnit.SECONDS.sleep(5);

        // 成功的应答
        response.setStatus(200);
        map.put("code", "SUCCESS");
        map.put("message", "成功");
        return gson.toJson(map);
    }

    /**
     * 根据订单号查询订单
     *
     * @param orderNo
     * @return
     * @throws IOException
     */
    @GetMapping("/query/{orderNo}")
    public R queryOrder(@PathVariable String orderNo) throws IOException {
        log.info("查询订单");
        String result = wxPayService.queryOrder(orderNo);

        return R.ok().setMessage("查询成功").data("result", result);
    }

    /**
     * 申请退款
     *
     * @param orderNo
     * @param reason
     * @return
     */
    @ApiOperation("申请退款")
    @PostMapping("/refunds/{orderNo}/{reason}")
    public R refunds(@PathVariable String orderNo, @PathVariable String reason) throws IOException {
        log.info("申请退款");
        wxPayService.refund(orderNo, reason);
        return R.ok();
    }

    /**
     * 退款结果通知
     * 退款状态改变后，微信会把相关退款结果发给商户
     *
     * @param request
     * @param response
     * @return
     */
    @PostMapping("/refunds/notify")
    public String refundsNotify(HttpServletRequest request, HttpServletResponse response) {
        log.info("退款通知执行");
        Gson gson = new Gson();
        Map<String, String> map = new HashMap<>();

        try {
            // 处理通知参数
            String body = HttpUtils.readData(request);
            HashMap<String, Object> bodyMap = gson.fromJson(body, HashMap.class);
            String requestId = (String) bodyMap.get("id");
            log.info("支付通知的id ===> {}", requestId);

            // 签名验证
            //签名的验证
            WechatPay2ValidatorForRequest wechatPay2ValidatorForRequest
                    = new WechatPay2ValidatorForRequest(verifier, body, requestId);
            if (!wechatPay2ValidatorForRequest.validate(request)) {
                log.error("通知验签失败");
                // 失败应答
                response.setStatus(500);
                map.put("code", "ERROR");
                map.put("message", "签名验证失败");
                return gson.toJson(map);
            }

            log.info("通知验签成功");
            // 处理退款单
            wxPayService.processRefund(bodyMap);

            // 成功应答
            response.setStatus(200);
            map.put("code", "200");
            map.put("message", "成功");
            return gson.toJson(map);
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
