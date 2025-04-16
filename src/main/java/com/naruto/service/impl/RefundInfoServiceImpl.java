package com.naruto.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.Gson;
import com.naruto.mapper.RefundInfoMapper;
import com.naruto.model.entity.OrderInfo;
import com.naruto.model.entity.RefundInfo;
import com.naruto.service.OrderInfoService;
import com.naruto.service.RefundInfoService;
import com.naruto.util.OrderNoUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;

@Service
public class RefundInfoServiceImpl extends ServiceImpl<RefundInfoMapper, RefundInfo> implements RefundInfoService {

    @Resource
    private OrderInfoService orderInfoService;

    /**
     * 创建退款单记录
     *
     * @param orderNo
     * @param reason
     * @return
     */
    @Override
    public RefundInfo createRefundInfo(String orderNo, String reason) {
        OrderInfo orderInfo = orderInfoService.getOrderByOrderNo(orderNo);

        RefundInfo refundInfo = new RefundInfo();
        refundInfo.setReason(reason);
        refundInfo.setOrderNo(orderNo);
        refundInfo.setRefundNo(OrderNoUtils.getRefundNo());
        refundInfo.setTotalFee(orderInfo.getTotalFee());
        refundInfo.setRefund(orderInfo.getTotalFee());

        baseMapper.insert(refundInfo);
        return refundInfo;
    }

    /**
     * 根据微信支付返回值更新退款单
     *
     * @param bodyAsString
     */
    @Override
    public void updateRefund(String bodyAsString) {
        Gson gson = new Gson();
        HashMap<String, String> hashMap = gson.fromJson(bodyAsString, HashMap.class);

        QueryWrapper<RefundInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("refund_no", hashMap.get("out_refund_no"));

        RefundInfo refundInfo = new RefundInfo();
        refundInfo.setRefundId(hashMap.get("refund_id"));

        // 查询退款和申请退款中的返回参数
        if (hashMap.get("status") != null) {
            refundInfo.setRefundStatus(hashMap.get("status"));
            refundInfo.setContentReturn(bodyAsString);
        }
        // 退款回调中的回调参数
        if (hashMap.get("refund_status") != null) {
            refundInfo.setRefundStatus(hashMap.get("refund_status"));
        refundInfo.setContentNotify(bodyAsString);
        }


        baseMapper.update(refundInfo, queryWrapper);
    }
}
