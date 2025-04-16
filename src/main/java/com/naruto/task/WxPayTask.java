package com.naruto.task;


import com.naruto.model.entity.OrderInfo;
import com.naruto.model.entity.RefundInfo;
import com.naruto.service.OrderInfoService;
import com.naruto.service.RefundInfoService;
import com.naruto.service.WxPayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.List;

/**
 * @Author: naruto
 * @CreateTime: 2025-04-09-23:45
 */
@Slf4j
@Component
public class WxPayTask {

    @Resource
    private OrderInfoService orderInfoService;

    @Resource
    private WxPayService wxPayService;

    @Resource
    private RefundInfoService refundInfoService;

    /**
     * 定时任务1
     */
    @Scheduled(cron = "0 * * * * *")
    public void task1() {
        log.info("定时任务1被执行");
    }

    @Scheduled(cron = "0/30 * * * * ?")
    public void orderConfirm() throws IOException {
        log.info("orderConfirm被执行");
        List<OrderInfo> orderInfoList = orderInfoService.getNoPayOrderByDuration(5);

        for (OrderInfo orderInfo : orderInfoList) {
            String orderNo = orderInfo.getOrderNo();
            log.warn("超时订单id：{}", orderNo);

            // 核实订单状态：调用微信支付查单接口
            wxPayService.checkOrderStatus(orderNo);
        }
    }

    /**
     * 每三十秒执行一次，查询创建超过5分钟，并且未成功退款的退款单
     */
    @Scheduled(cron = "0/30 * * * * ?")
    public void refundConfirm() throws IOException {
        log.info("refundConfirm执行");
        List<RefundInfo> refundInfoList = refundInfoService.getNoRefundOrderByDuration(5);
        for (RefundInfo refundInfo : refundInfoList) {
            String refundNo = refundInfo.getRefundNo();
            log.warn("超时未退款订单id：{}", refundNo);

            // 核实订单状态，调用微信支付查询退款接口
            wxPayService.checkRefundStatus(refundNo);
        }
    }
}
