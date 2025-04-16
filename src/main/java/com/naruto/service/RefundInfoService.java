package com.naruto.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.naruto.model.entity.RefundInfo;

import java.util.List;

public interface RefundInfoService extends IService<RefundInfo> {

    RefundInfo createRefundInfo(String orderNo, String reason);

    /**
     * 根据微信支付返回值更新退款单
     *
     * @param bodyAsString
     */
    void updateRefund(String bodyAsString);

    /**
     * 查询超过minutes分钟未退款的订单
     *
     * @param minutes
     * @return
     */
    List<RefundInfo> getNoRefundOrderByDuration(int minutes);
}
