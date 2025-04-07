package com.naruto.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.naruto.model.entity.OrderInfo;

import java.util.List;

public interface OrderInfoService extends IService<OrderInfo> {

    /**
     * 根据商品id创建订单
     *
     * @param productId
     * @return
     */
    OrderInfo createOrderByProductId(Long productId);

    /**
     * 保存二维码地址
     *
     * @param orderNo
     * @param codeUrl
     */
    void saveCodeUrl(String orderNo, String codeUrl);

    List<OrderInfo> listOrderByCreateTimeDesc();
}
