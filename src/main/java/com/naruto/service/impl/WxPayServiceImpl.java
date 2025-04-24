package com.naruto.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.gson.Gson;
import com.naruto.config.WxPayConfig;
import com.naruto.enums.OrderStatus;
import com.naruto.enums.wxpay.WxApiType;
import com.naruto.enums.wxpay.WxNotifyType;
import com.naruto.enums.wxpay.WxRefundStatus;
import com.naruto.enums.wxpay.WxTradeState;
import com.naruto.model.entity.OrderInfo;
import com.naruto.model.entity.RefundInfo;
import com.naruto.service.OrderInfoService;
import com.naruto.service.PaymentInfoService;
import com.naruto.service.RefundInfoService;
import com.naruto.service.WxPayService;
import com.wechat.pay.contrib.apache.httpclient.util.AesUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    @Resource
    private RefundInfoService refundInfoService;

    @Resource
    private CloseableHttpClient wxPayNoSignClient; // 无需应答签名

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
     * 根据订单号查询微信支付查单接口，核实订单状态
     * 如果订单已支付，则更新商户端订单状态
     * 如果订单未支付，则调用关单接口关闭订单，并更新商户端订单状态
     *
     * @param orderNo
     */
    @Override
    public void checkOrderStatus(String orderNo) throws IOException {
        log.warn("根据订单号核实订单状态 ===> {}", orderNo);

        String result = this.queryOrder(orderNo);
        Gson gson = new Gson();
        HashMap resultMap = gson.fromJson(result, HashMap.class);

        Object tradeState = resultMap.get("trade_state");
        if (WxTradeState.SUCCESS.getType().equals(tradeState)) {
            log.warn("核实订单已支付 ===> {}", orderNo);

            // 如果确认已支付，则更新商户端的订单状态
            orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.SUCCESS);
            // 记录支付日志
            paymentInfoService.createPaymentInfo(result);
        }

        if (WxTradeState.NOTPAY.getType().equals(tradeState)) {
            log.warn("核实订单未支付 ===> {}", orderNo);

            // 如果订单未支付，则调用关单接口
            this.closeOrder(orderNo);

            // 更新本地订单状态
            orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.CLOSED);
        }
    }

    /**
     * 退款
     *
     * @param orderNo
     * @param reason
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void refund(String orderNo, String reason) throws IOException {
        log.info("创建退款单记录");

        // 根据订单号创建退款记录
        RefundInfo refundInfo = refundInfoService.createRefundInfo(orderNo, reason);

        // 调用退款接口
        log.info("调用退款API");
        String url = wxPayConfig.getDomain().concat(WxApiType.DOMESTIC_REFUNDS.getType());
        HttpPost httpPost = new HttpPost(url);

        Gson gson = new Gson();
        HashMap<String, Object> paramsMap = new HashMap<>();
        paramsMap.put("out_trade_no", orderNo);
        paramsMap.put("out_refund_no", refundInfo.getRefundNo());
        paramsMap.put("reason", reason);
        paramsMap.put("notify_url", wxPayConfig.getNotifyDomain().concat(WxNotifyType.REFUND_NOTIFY.getType()));

        Map<String, Object> amountMap = new HashMap<>();
        amountMap.put("refund", refundInfo.getRefund());
        amountMap.put("total", refundInfo.getTotalFee());
        amountMap.put("currency", "CNY");

        paramsMap.put("amount", amountMap);

        String paramsJson = gson.toJson(paramsMap);
        log.info("调用退款API，请求参数：{}", paramsJson);

        StringEntity stringEntity = new StringEntity(paramsJson, "UTF-8");
        stringEntity.setContentType("application/json");
        httpPost.setEntity(stringEntity);
        httpPost.setHeader("Accept", "application/json");

        // 完成签名并执行请求
        CloseableHttpResponse response = wxPayClient.execute(httpPost);

        try {
            String bodyAsString = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                // 处理成功
                log.info("成功200，退款返回结果 ===> {}", bodyAsString);
            } else if (statusCode == 204) {
                // 处理成功，无返回Body
                log.info("退款成功204");
            } else {
                log.info("退款失败,响应码 = {}，响应结果：{}", statusCode, bodyAsString);
                throw new IOException("request failed");
            }

            // 更新订单状态
            orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.REFUND_PROCESSING);

            // 更新退款单
            refundInfoService.updateRefund(bodyAsString);
        } catch (IOException e) {
            response.close();
        }
    }

    /**
     * 处理退款单
     *
     * @param bodyMap
     */
    @Override
    public void processRefund(HashMap bodyMap) throws GeneralSecurityException {
        log.info("退款单");
        String plantText = decryptFromResource(bodyMap);

        // 将明文转成map
        Gson gson = new Gson();
        HashMap plantTextMap = gson.fromJson(plantText, HashMap.class);
        String orderNo = ((String) plantTextMap.get("out_trade_no"));

        if (lock.tryLock()) {
            try {
                String orderStatus = orderInfoService.getOrderStatus(orderNo);
                if (!OrderStatus.REFUND_PROCESSING.getType().equals(orderStatus)) {
                    return;
                }

                // 更新订单状态
                orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.REFUND_SUCCESS);

                // 更新退款单
                refundInfoService.updateRefund(plantText);
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * 查询退款单，调用微信支付查询退款接口
     *
     * @param refundNo
     * @return
     */
    @Override
    public String queryRefund(String refundNo) throws IOException {
        log.info("查询退款单接口 ===> {}", refundNo);

        String url = String.format(WxApiType.DOMESTIC_REFUNDS_QUERY.getType(), refundNo);
        url = wxPayConfig.getDomain().concat(url);

        // 创建http请求对象
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Accept", "application/json");

        CloseableHttpResponse response = wxPayClient.execute(httpGet);
        try {
            String bodyAsString = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                log.info("查询退款单成功 ===> {}", bodyAsString);
            } else if (statusCode == 204) {
                log.info("成功");
            } else {
                throw new RuntimeException("查询退款异常，响应码 = " + statusCode + ", 查询退款结果返回 = " + bodyAsString);
            }
            return bodyAsString;
        } finally {
            response.close();
        }
    }

    /**
     * 根据退款单号检查退款单状态
     *
     * @param refundNo
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void checkRefundStatus(String refundNo) throws IOException {
        log.warn("根据退款单号核实退款状态 ===> {}", refundNo);

        // 调用查询退款接口
        String result = this.queryRefund(refundNo);
        Gson gson = new Gson();
        HashMap hashMap = gson.fromJson(result, HashMap.class);

        // 获取微信支付端退款状态
        String status = (String) hashMap.get("status");
        String orderNo = (String) hashMap.get("out_trade_no");

        if (WxRefundStatus.SUCCESS.getType().equals(status)) {
            log.warn("核实订单已退款成功 ===> {}", refundNo);

            // 更新订单状态
            orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.REFUND_SUCCESS);
            // 更新退款单
            refundInfoService.updateRefund(result);
        }

        if (WxRefundStatus.ABNORMAL.getType().equals(status)) {
            log.warn("核实订单退款异常 ===> {}", refundNo);

            // 如果确认退款异常，更新订单状态
            orderInfoService.updateStatusByOrderNo(orderNo, OrderStatus.REFUND_ABNORMAL);
            // 更新退款单
            refundInfoService.updateRefund(result);
        }
    }

    /**
     * 查询对账单
     *
     * @param billDate 截止日期
     * @param type     账单类型
     * @return
     */
    @Override
    public String queryBill(String billDate, String type) throws IOException {
        log.warn("申请账单接口调用{}", billDate);
        String url = "";
        if ("tradebill".equals(type)) {
            url = WxApiType.TRADE_BILLS.getType();
        } else if ("fundflowbill".equals(type)) {
            url = WxApiType.FUND_FLOW_BILLS.getType();
        } else {
            log.warn("不支持的账单类型");
            throw new RuntimeException("不支持的账单类型");
        }
        url = wxPayConfig.getDomain().concat(url).concat("?bill_date=").concat(billDate);

        // 创建http对象
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Accept", "application/json");

        CloseableHttpResponse response = wxPayClient.execute(httpGet);
        try {
            String bodyAsString = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                log.info("申请账单成功 ===> {}", bodyAsString);
            } else if (statusCode == 204) {
                log.info("成功");
            } else {
                log.error("申请账单异常，响应码 = {}，申请账单返回结果 = {}", statusCode, bodyAsString);
                throw new RuntimeException("申请账单异常，响应码 = " + statusCode + ", 申请账单返回结果 = " + bodyAsString);
            }

            Gson gson = new Gson();
            HashMap hashMap = gson.fromJson(bodyAsString, HashMap.class);
            return ((String) hashMap.get("download_url"));
        } finally {
            response.close();
        }
    }

    /**
     * 下载对账单
     *
     * @param billDate
     * @param type
     * @return
     */
    @Override
    public String downloadBill(String billDate, String type) throws IOException {
        String downloadUrl = this.queryBill(billDate, type);

        HttpGet httpGet = new HttpGet(downloadUrl);
        httpGet.setHeader("Accept", "application/json");

        // 注意：这里调用的无需签名的httpClient，在wxPayConfig中注入这样的一个httpClient
        CloseableHttpResponse response = wxPayNoSignClient.execute(httpGet);
        try {
            String bodyAsString = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                log.info("下载对账单成功 ===> {}", bodyAsString);
            } else if (statusCode == 204) {
                log.info("成功");
            } else {
                log.error("下载对账单异常，响应码 = {}，下载对账单返回结果 = {}", statusCode, bodyAsString);
                throw new RuntimeException("下载对账单异常，响应码 = " + statusCode + ", 下载对账单返回结果 = " + bodyAsString);
            }

            return bodyAsString;
        } finally {
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
