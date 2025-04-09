package com.naruto.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.gson.Gson;
import com.naruto.config.WxPayConfig;
import com.naruto.enums.OrderStatus;
import com.naruto.enums.wxpay.WxApiType;
import com.naruto.enums.wxpay.WxNotifyType;
import com.naruto.model.entity.OrderInfo;
import com.naruto.service.OrderInfoService;
import com.naruto.service.PaymentInfoService;
import com.naruto.service.WxPayService;
import com.wechat.pay.contrib.apache.httpclient.util.AesUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 微信支付接口实现
 *
 * @Author: naruto
 * @CreateTime: 2025-04-06-18:01
 */
@Slf4j
@Service
public class WxPayServiceImpl implements WxPayService {

    @Resource
    private WxPayConfig wxPayConfig;

    @Resource
    private CloseableHttpClient wxPayClient;

    @Resource
    private OrderInfoService orderInfoService;

    @Resource
    private PaymentInfoService paymentInfoService;

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * 创建订单调用Native支付接口
     *
     * @param productId
     * @return code_url
     * @throws IOException
     */
    @Override
    public Map<String, Object> nativePay(Long productId) throws IOException {

        log.info("生成订单");

        // 生成订单
        OrderInfo orderInfo = orderInfoService.createOrderByProductId(productId);
        String codeUrl = orderInfo.getCodeUrl();
        if (orderInfo != null && !StringUtils.isEmpty(codeUrl)) {
            log.info("二维码已存在，订单已存在");
            HashMap<String, Object> map = new HashMap<>();
            map.put("codeUrl", codeUrl);
            map.put("orderNo", orderInfo.getOrderNo());
        }

        // 调用统一下单API
        HttpPost httpPost = new HttpPost(wxPayConfig.getDomain().concat(WxApiType.NATIVE_PAY.getType()));
        // 构造body参数
        HashMap<String, Object> paramsMap = new HashMap<>();
        paramsMap.put("appid", wxPayConfig.getAppid());
        paramsMap.put("mchid", wxPayConfig.getMchId());
        paramsMap.put("description", orderInfo.getTitle());
        paramsMap.put("out_trade_no", orderInfo.getOrderNo());
        paramsMap.put("notify_url", wxPayConfig.getNotifyDomain().concat(WxNotifyType.NATIVE_NOTIFY.getType()));
        // 构造订单金额map
        HashMap<String, Object> amountMap = new HashMap<>();
        amountMap.put("total", orderInfo.getTotalFee());
        amountMap.put("currency", "CNY");
        paramsMap.put("amount", amountMap);

        log.info("请求参数：{}", paramsMap);

        Gson gson = new Gson();
        String jsonParams = gson.toJson(paramsMap);
        StringEntity stringEntity = new StringEntity(jsonParams, "UTF-8");
        stringEntity.setContentType("application/json");
        httpPost.setEntity(stringEntity);
        httpPost.setHeader("Accept", "application/json");

        // 完成签名并执行请求
        CloseableHttpResponse response = wxPayClient.execute(httpPost);

        log.info("解析微信native下单响应");
        try {
            // 获取响应体并转为字符串和响应状态码
            String bodyAsString = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                // 处理成功
                log.info("成功, 返回结果 = {}", response);
            } else if (statusCode == 204) {
                // 处理成功，无返回Body
                log.info("成功");
            } else {
                log.info("Native下单失败,响应码 = {},返回结果 = {}", statusCode, bodyAsString);
                throw new IOException("request failed");
            }

            HashMap<String, String> resultMap = gson.fromJson(bodyAsString, HashMap.class);
            // 解析响应结果的二维码
            codeUrl = resultMap.get("code_url");

            // 保存二维码
            orderInfoService.saveCodeUrl(orderInfo.getOrderNo(), codeUrl);

            HashMap<String, Object> map = new HashMap<>();
            map.put("codeUrl", codeUrl);
            map.put("orderNo", orderInfo.getOrderNo());
            return map;
        } finally {
            // 为什么要关闭这个？连接资源有限？
            response.close();
        }
    }

    /**
     * 处理订单：解密报文，更新订单状态，记录支付日志
     *
     * @param bodyMap
     * @throws GeneralSecurityException
     */
    @Override
    public void processOrder(HashMap<String, Object> bodyMap) throws GeneralSecurityException {
        log.info("处理订单");
        // 解密报文
        String plainText = decryptFromResource(bodyMap);

        // 将明文转为map
        Gson gson = new Gson();
        HashMap hashMap = gson.fromJson(plainText, HashMap.class);
        String outTradeNo = (String) hashMap.get("out_trade_no");

        /*
        发生情况：假如说由于网络问题，两条通知消息同时到达，然后判断的支付状态都是未支付，就会执行两次更新订单和记录支付日志

        在对业务数据进行状态检查和处理之前，
        要采用数据锁进行并发控制
        以避免函数重入造成的数据混乱
        */
        // 处理并发情况下订单状态和支付日志问题
        // 尝试获取锁，成功获取则立即返回true，获取失败则立即返回false，不必一直等待锁释放
        if (lock.tryLock()) {
            try {
                // 处理重复通知情况下，避免重复通知后向支付日志中记录多条重复数据
                String orderStatus = orderInfoService.getOrderStatus(outTradeNo);
                if (!OrderStatus.NOTPAY.getType().equals(orderStatus)) {
                    return;
                }


                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // 更新订单状态
                orderInfoService.updateStatusByOrderNo(outTradeNo, OrderStatus.SUCCESS);
                // 记录支付日志
                paymentInfoService.createPaymentInfo(plainText);
            } finally {
                // 主动释放锁
                lock.unlock();
            }
        }
    }

    /**
     * 取消订单
     *
     * @param orderNo
     */
    @Override
    public void cancelOrder(String orderNo) throws Exception {
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getOrderNo, orderNo);
        OrderInfo orderInfo = orderInfoService.getOne(queryWrapper);
        if (!OrderStatus.NOTPAY.getType().equals(orderInfo.getOrderStatus())) {
            log.info("订单已支付，不能取消");
            return;
        }

        // 调用微信支付的关单接口
        this.closeOrder(orderNo);

        // 更新商户端的订单状态
        orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.CANCEL);
    }

    /**
     * 根据订单号查询订单
     *
     * @param orderNo
     * @return
     */
    @Override
    public String queryOrder(String orderNo) throws IOException {
        log.info("调用查单接口：{}", orderNo);
        String url = String.format(WxApiType.ORDER_QUERY_BY_NO.getType(), orderNo);
        url = wxPayConfig.getDomain().concat(url).concat("?mchid=").concat(wxPayConfig.getMchId());

        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Accept", "application/json");

        // 完成签名并执行请求
        CloseableHttpResponse response = wxPayClient.execute(httpGet);
        try {
            // 获取响应体并转为字符串和响应状态码
            String bodyAsString = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                // 处理成功
                log.info("成功, 返回结果 = {}", response);
            } else if (statusCode == 204) {
                // 处理成功，无返回Body
                log.info("成功");
            } else {
                log.error("调用微信查单接口失败,响应码 = {},返回结果 = {}", statusCode, bodyAsString);
                throw new IOException("request failed");
            }
            return bodyAsString;
        } finally {
            // 为什么要关闭这个？连接资源有限？
            response.close();
        }
    }

    /**
     * 调用微信关单接口
     *
     * @param orderNo
     */
    private void closeOrder(String orderNo) throws IOException {
        String url = String.format(WxApiType.CLOSE_ORDER_BY_NO.getType(), orderNo);
        url = wxPayConfig.getDomain().concat(url);
        HttpPost httpPost = new HttpPost(url);

        // 组装json请求体
        Gson gson = new Gson();
        HashMap<String, Object> paramsMap = new HashMap<>();
        paramsMap.put("mchid", wxPayConfig.getMchId());
        String jsonParams = gson.toJson(paramsMap);
        log.info("关单请求参数：{}", jsonParams);

        // 将请求体放入请求对象中
        StringEntity stringEntity = new StringEntity(jsonParams, "UTF-8");
        stringEntity.setContentType("application/json");
        httpPost.setEntity(stringEntity);
        httpPost.setHeader("Accept", "application/json");

        // 完成签名并执行请求
        CloseableHttpResponse response = wxPayClient.execute(httpPost);

        try {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                // 处理成功
                log.info("成功200");
            } else if (statusCode == 204) {
                // 处理成功，无返回Body
                log.info("成功204");
            } else {
                log.info("关闭订单失败,响应码 = {}", statusCode);
                throw new IOException("request failed");
            }
        } catch (IOException e) {
            response.close();
        }
    }

    /**
     * 对称解密
     *
     * @param bodyMap
     * @return
     */
    private String decryptFromResource(HashMap<String, Object> bodyMap) throws GeneralSecurityException {
        log.info("解密数据");

        // 通知数据
        Map<String, String> resourceMap = (Map) bodyMap.get("resource");
        // 数据密文
        String ciphertext = resourceMap.get("ciphertext");
        // 获取随机串
        String nonce = resourceMap.get("nonce");
        // 获取附加数据
        String associatedData = resourceMap.get("associated_data");

        AesUtil aesUtil = new AesUtil(wxPayConfig.getApiV3Key().getBytes());
        String plainText = aesUtil.decryptToString(associatedData.getBytes(StandardCharsets.UTF_8),
                nonce.getBytes(StandardCharsets.UTF_8), ciphertext);
        log.info("解密后明文：{}", plainText);
        log.info("密文：{}", ciphertext);
        return plainText;
    }
}
