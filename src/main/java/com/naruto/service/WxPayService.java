package com.naruto.service;


import java.io.IOException;
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
}
