package com.recommend.shop.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recommend.shop.common.PageResult;
import com.recommend.shop.entity.Payment;
import com.recommend.shop.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;

    public PageResult<Payment> listPayments(int page, int pageSize, Long userId, Integer payStatus) {
        Page<Payment> pager = paymentMapper.selectPage(
            new Page<>(page, pageSize),
            Wrappers.<Payment>lambdaQuery()
                .eq(userId != null, Payment::getUserId, userId)
                .eq(payStatus != null, Payment::getPayStatus, payStatus)
                .orderByDesc(Payment::getCreatedAt)
        );
        return new PageResult<>(pager.getTotal(), pager.getRecords());
    }
}
