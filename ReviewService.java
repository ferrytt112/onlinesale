package com.recommend.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recommend.shop.common.PageResult;
import com.recommend.shop.common.ServiceResult;
import com.recommend.shop.entity.Order;
import com.recommend.shop.entity.OrderItem;
import com.recommend.shop.entity.Review;
import com.recommend.shop.mapper.OrderItemMapper;
import com.recommend.shop.mapper.OrderMapper;
import com.recommend.shop.mapper.ReviewMapper;
import com.recommend.shop.util.TimeUtils;
import com.recommend.shop.util.TypeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderMapper orderMapper;

    public PageResult<Review> listReviews(int page, int pageSize, Long itemId, Long userId, Integer status) {
        LambdaQueryWrapper<Review> wrapper = Wrappers.<Review>lambdaQuery()
            .eq(Review::getIsDeleted, 0)
            .eq(itemId != null, Review::getItemId, itemId)
            .eq(userId != null, Review::getUserId, userId)
            .eq(status != null, Review::getStatus, status)
            .orderByDesc(Review::getCreatedAt);
        Page<Review> pager = reviewMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return new PageResult<>(pager.getTotal(), pager.getRecords());
    }

    public Review getReviewByOrderItem(Long orderItemId) {
        return reviewMapper.selectOne(
            Wrappers.<Review>lambdaQuery()
                .eq(Review::getOrderItemId, orderItemId)
                .eq(Review::getIsDeleted, 0)
                .last("limit 1")
        );
    }

    public ServiceResult<Review> createReview(Long userId, Map<String, Object> payload) {
        Long orderItemId = TypeUtils.toLong(payload.get("order_item_id"));
        OrderItem orderItem = orderItemMapper.selectById(orderItemId);
        if (orderItem == null) {
            return ServiceResult.fail("order_item_not_found");
        }
        Order order = orderMapper.selectById(orderItem.getOrderId());
        if (order == null || !userId.equals(order.getUserId())) {
            return ServiceResult.fail("not_allowed");
        }
        if (getReviewByOrderItem(orderItem.getId()) != null) {
            return ServiceResult.fail("already_reviewed");
        }

        Review review = new Review();
        review.setUserId(userId);
        review.setItemId(orderItem.getItemId());
        review.setOrderId(orderItem.getOrderId());
        review.setOrderItemId(orderItem.getId());
        review.setRating(TypeUtils.toInt(payload.get("rating")) == null ? 5 : TypeUtils.toInt(payload.get("rating")));
        review.setContent(TypeUtils.toStr(payload.get("content")));
        review.setImages(TypeUtils.toStr(payload.get("images")));
        review.setIsAnonymous(Boolean.TRUE.equals(TypeUtils.toBool(payload.get("is_anonymous"))) ? 1 : 0);
        review.setReplyContent(null);
        review.setReplyTime(null);
        review.setStatus(TypeUtils.toInt(payload.get("status")) == null ? 1 : TypeUtils.toInt(payload.get("status")));
        review.setLikeCount(0);
        review.setCreatedAt(TimeUtils.utcNow());
        review.setUpdatedAt(TimeUtils.utcNow());
        review.setIsDeleted(0);
        reviewMapper.insert(review);
        return ServiceResult.ok(review);
    }

    public Review updateReviewStatus(Review review, Integer status) {
        review.setStatus(status);
        review.setUpdatedAt(TimeUtils.utcNow());
        reviewMapper.updateById(review);
        return review;
    }

    public Review replyReview(Review review, String replyContent) {
        review.setReplyContent(replyContent);
        review.setReplyTime(TimeUtils.utcNow());
        review.setStatus(1);
        review.setUpdatedAt(TimeUtils.utcNow());
        reviewMapper.updateById(review);
        return review;
    }

    public Review getReviewById(Long reviewId) {
        return reviewMapper.selectOne(
            Wrappers.<Review>lambdaQuery()
                .eq(Review::getId, reviewId)
                .eq(Review::getIsDeleted, 0)
                .last("limit 1")
        );
    }
}
