package com.naruto.service;


import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信支付接口层
 *
 * @Author: naruto
 * @CreateTime: 2025-04-06-18:01
 */
public interface WxPayService {

    /**
     * 生成支付二维码和订单号
     *
     * @param productId
     * @return
     */
    Map<String, Object> nativePay(Long productId) throws IOException;

    /**
     * 处理订单
     *
     * @param bodyMap
     */
    void processOrder(HashMap<String, Object> bodyMap) throws GeneralSecurityException;

    /**
     * 取消订单
     *
     * @param orderNo
     */
    void cancelOrder(String orderNo) throws Exception;
}
