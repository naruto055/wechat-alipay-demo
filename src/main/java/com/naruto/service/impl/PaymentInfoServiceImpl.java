package com.naruto.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.Gson;
import com.naruto.enums.PayType;
import com.naruto.mapper.PaymentInfoMapper;
import com.naruto.model.entity.PaymentInfo;
import com.naruto.service.PaymentInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class PaymentInfoServiceImpl extends ServiceImpl<PaymentInfoMapper, PaymentInfo> implements PaymentInfoService {

    /**
     * 记录支付日志
     *
     * @param plainText 解密后明文
     */
    @Override
    public void createPaymentInfo(String plainText) {
        log.info("记录支付日志");
        Gson gson = new Gson();
        HashMap hashMap = gson.fromJson(plainText, HashMap.class);
        String tradeNo = (String) hashMap.get("out_trade_no");
        String transactionId = (String) hashMap.get("transaction_id");
        String tradeType = (String) hashMap.get("trade_type");
        String tradeStateDesc = (String) hashMap.get("trade_state_desc");
        Map amount = (Map) hashMap.get("amount");
        Integer payerTotal = ((Double) amount.get("payer_total")).intValue();

        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setOrderNo(tradeNo);
        paymentInfo.setPaymentType(PayType.WXPAY.getType());
        paymentInfo.setTransactionId(transactionId);
        paymentInfo.setTradeType(tradeType);
        paymentInfo.setTradeState(tradeStateDesc);
        paymentInfo.setPayerTotal(payerTotal);
        paymentInfo.setContent(plainText);

        //发现问题：微信支付会多次发送通知过来，然后这里也会有多条支付的日志被插入到数据库中去
        baseMapper.insert(paymentInfo);
    }
}
