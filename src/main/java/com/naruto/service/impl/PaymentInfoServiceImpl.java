package com.naruto.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.naruto.mapper.PaymentInfoMapper;
import com.naruto.model.entity.PaymentInfo;
import com.naruto.service.PaymentInfoService;
import org.springframework.stereotype.Service;

@Service
public class PaymentInfoServiceImpl extends ServiceImpl<PaymentInfoMapper, PaymentInfo> implements PaymentInfoService {

}
