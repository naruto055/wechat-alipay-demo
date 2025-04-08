package com.naruto.controller;


import com.naruto.enums.OrderStatus;
import com.naruto.model.entity.OrderInfo;
import com.naruto.model.vo.R;
import com.naruto.service.OrderInfoService;
import com.naruto.service.WxPayService;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 商品订单管理接口
 *
 * @Author: naruto
 * @CreateTime: 2025-04-07-22:44
 */
@Slf4j
@CrossOrigin
@Api(tags = "商品订单管理")
@RestController
@RequestMapping("/api/order-info")
public class OrderInfoController {

    @Resource
    private OrderInfoService orderInfoService;

    @Resource
    private WxPayService wxPayService;

    @GetMapping("/list")
    public R list() {
        List<OrderInfo> list = orderInfoService.listOrderByCreateTimeDesc();
        return R.ok().data("list", list);
    }

    /**
     * 查询订单状态
     *
     * @param orderNo
     * @return
     */
    @GetMapping("/query-order-status/{orderNo}")
    public R getOrderStatus(@PathVariable String orderNo) {
        String orderStatus = orderInfoService.getOrderStatus(orderNo);
        if (OrderStatus.SUCCESS.getType().equals(orderStatus)) {
            return R.ok().setMessage("支付成功");
        }
        return R.ok().setCode(101).setMessage("支付中......");
    }

    /**
     * 取消订单
     *
     * @param orderNo
     * @return
     */
    @PostMapping("/cancel/{orderNo}")
    public R cancel(@PathVariable String orderNo) throws Exception {
        log.info("取消订单");
        wxPayService.cancelOrder(orderNo);
        return R.ok().setMessage("订单取消成功");
    }
}
