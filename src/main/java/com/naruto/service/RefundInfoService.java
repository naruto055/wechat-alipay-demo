package com.naruto.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.naruto.model.entity.RefundInfo;

public interface RefundInfoService extends IService<RefundInfo> {

    RefundInfo createRefundInfo(String orderNo, String reason);

    /**
     * 根据微信支付返回值更新退款单
     *
     * @param bodyAsString
     */
    void updateRefund(String bodyAsString);
}
