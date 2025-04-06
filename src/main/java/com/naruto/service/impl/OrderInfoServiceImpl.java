package com.naruto.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.naruto.mapper.OrderInfoMapper;
import com.naruto.model.entity.OrderInfo;
import com.naruto.service.OrderInfoService;
import org.springframework.stereotype.Service;

@Service
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {

}
