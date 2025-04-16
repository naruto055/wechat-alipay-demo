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

    /**
     * 根据订单号查询订单
     *
     * @param orderNo
     * @return
     */
    String queryOrder(String orderNo) throws IOException;

    void checkOrderStatus(String orderNo) throws IOException;

    /**
     * 申请退款
     *
     * @param orderNo
     * @param reason
     * @throws IOException
     */
    void refund(String orderNo, String reason) throws IOException;

    /**
     * 处理退款单
     *
     * @param bodyMap
     */
    void processRefund(HashMap bodyMap) throws GeneralSecurityException;

    /**
     * 根据退款单号查询退款单
     *
     * @param refundNo
     * @return
     */
    String queryRefund(String refundNo) throws IOException;

    /**
     * 根据退款单号核实退款单状态
     *
     * @param refundNo
     */
    void checkRefundStatus(String refundNo) throws IOException;
}
