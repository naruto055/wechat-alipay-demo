package com.naruto.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.naruto.enums.OrderStatus;
import com.naruto.mapper.OrderInfoMapper;
import com.naruto.mapper.ProductMapper;
import com.naruto.model.entity.OrderInfo;
import com.naruto.model.entity.Product;
import com.naruto.service.OrderInfoService;
import com.naruto.util.OrderNoUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {

    @Resource
    private ProductMapper productMapper;

    /**
     * 根据商品id生成订单
     *
     * @param productId
     * @return
     */
    @Override
    public OrderInfo createOrderByProductId(Long productId) {

        // 查找已存在，但未支付的订单
        OrderInfo orderInfo = this.getNoPayOrderByProductId(productId);
        if (orderInfo != null) {
            return orderInfo;
        }

        // 获取商品信息
        Product product = productMapper.selectById(productId);

        // 生成订单
        orderInfo = new OrderInfo();
        orderInfo.setTitle("test");
        orderInfo.setOrderNo(OrderNoUtils.getOrderNo());
        orderInfo.setProductId(productId);
        orderInfo.setTotalFee(product.getPrice());
        orderInfo.setOrderStatus(OrderStatus.NOTPAY.getType());

        int insert = baseMapper.insert(orderInfo);
        return insert > 0 ? orderInfo : null;
    }

    /**
     * 保存二维码地址
     *
     * @param orderNo
     * @param codeUrl
     */
    @Override
    public void saveCodeUrl(String orderNo, String codeUrl) {
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_no", orderNo);

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setCodeUrl(codeUrl);
        baseMapper.update(orderInfo, queryWrapper);
    }

    /**
     * 根据商品id查询未支付订单
     *
     * @param productId
     * @return
     */
    private OrderInfo getNoPayOrderByProductId(Long productId) {
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("product_id", productId);
        queryWrapper.eq("order_status", OrderStatus.NOTPAY.getType());
        return baseMapper.selectOne(queryWrapper);
    }
}
