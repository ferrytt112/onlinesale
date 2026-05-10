package com.recommend.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recommend.shop.common.PageResult;
import com.recommend.shop.common.ServiceResult;
import com.recommend.shop.entity.Cart;
import com.recommend.shop.entity.Item;
import com.recommend.shop.entity.ItemSpec;
import com.recommend.shop.entity.Order;
import com.recommend.shop.entity.OrderItem;
import com.recommend.shop.entity.Payment;
import com.recommend.shop.entity.UserAddress;
import com.recommend.shop.entity.UserBehavior;
import com.recommend.shop.mapper.CartMapper;
import com.recommend.shop.mapper.ItemMapper;
import com.recommend.shop.mapper.ItemSpecMapper;
import com.recommend.shop.mapper.OrderItemMapper;
import com.recommend.shop.mapper.OrderMapper;
import com.recommend.shop.mapper.PaymentMapper;
import com.recommend.shop.mapper.UserBehaviorMapper;
import com.recommend.shop.util.IdUtils;
import com.recommend.shop.util.TimeUtils;
import com.recommend.shop.util.TypeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderService {

    public static final int ORDER_STATUS_PENDING = 0;
    public static final int ORDER_STATUS_PAID = 1;
    public static final int ORDER_STATUS_SHIPPED = 2;
    public static final int ORDER_STATUS_COMPLETED = 3;
    public static final int ORDER_STATUS_CANCELED = 4;
    public static final int ORDER_STATUS_REFUNDED = 5;

    public static final int PAY_STATUS_PENDING = 0;
    public static final int PAY_STATUS_SUCCESS = 1;
    public static final int PAY_STATUS_FAILED = 2;
    public static final int PAY_STATUS_REFUNDED = 3;

    private final ItemMapper itemMapper;
    private final ItemSpecMapper itemSpecMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final PaymentMapper paymentMapper;
    private final CartMapper cartMapper;
    private final UserBehaviorMapper userBehaviorMapper;

    @Transactional
    public ServiceResult<Order> createOrder(
        Long userId,
        List<Map<String, Object>> items,
        UserAddress address,
        String remark,
        boolean useCart
    ) {
        if (items == null || items.isEmpty()) {
            return ServiceResult.fail("empty_items");
        }
        List<OrderLine> orderLines = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (Map<String, Object> entry : items) {
            Long itemId = TypeUtils.toLong(entry.get("item_id"));
            Item item = itemMapper.selectOne(
                Wrappers.<Item>lambdaQuery()
                    .eq(Item::getId, itemId)
                    .eq(Item::getIsDeleted, 0)
                    .last("limit 1")
            );
            if (item == null || item.getStatus() == null || item.getStatus() != 1) {
                return ServiceResult.fail("item_unavailable");
            }

            ItemSpec spec = null;
            Long itemSpecId = TypeUtils.toLong(entry.get("item_spec_id"));
            if (itemSpecId != null) {
                spec = itemSpecMapper.selectOne(
                    Wrappers.<ItemSpec>lambdaQuery()
                        .eq(ItemSpec::getId, itemSpecId)
                        .eq(ItemSpec::getItemId, item.getId())
                        .last("limit 1")
                );
                if (spec == null) {
                    return ServiceResult.fail("spec_not_found");
                }
            }

            int quantity = TypeUtils.safeInt(entry.get("quantity"), 1);
            if (quantity < 1) {
                quantity = 1;
            }

            Integer stockSource = spec != null ? spec.getStock() : item.getStock();
            if (stockSource != null && stockSource < quantity) {
                return ServiceResult.fail("insufficient_stock");
            }

            BigDecimal price = calculatePrice(item, spec);
            BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(quantity));
            totalAmount = totalAmount.add(lineTotal);
            orderLines.add(new OrderLine(item, spec, quantity, price, lineTotal, TypeUtils.toLong(entry.get("cart_id"))));
        }

        String orderNo = IdUtils.generateOrderNo();
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setPayAmount(totalAmount);
        order.setFreightAmount(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setStatus(ORDER_STATUS_PENDING);
        order.setReceiverName(address.getReceiverName());
        order.setReceiverPhone(address.getReceiverPhone());
        order.setReceiverAddress(
            safe(address.getProvince())
                + safe(address.getCity())
                + safe(address.getDistrict())
                + safe(address.getDetailAddress())
        );
        order.setRemark(remark);
        order.setCreatedAt(TimeUtils.utcNow());
        order.setUpdatedAt(TimeUtils.utcNow());
        order.setIsDeleted(0);
        orderMapper.insert(order);

        for (OrderLine line : orderLines) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setOrderNo(orderNo);
            orderItem.setItemId(line.item().getId());
            orderItem.setItemName(line.item().getName());
            orderItem.setItemImage(line.item().getMainImage());
            orderItem.setItemSpecId(line.spec() == null ? null : line.spec().getId());
            orderItem.setItemSpecInfo(line.spec() == null ? "" : safe(line.spec().getSpecValue()));
            orderItem.setPrice(line.price());
            orderItem.setQuantity(line.quantity());
            orderItem.setTotalPrice(line.total());
            orderItem.setCreatedAt(TimeUtils.utcNow());
            orderItem.setUpdatedAt(TimeUtils.utcNow());
            orderItemMapper.insert(orderItem);
        }

        if (useCart) {
            List<Long> cartIds = orderLines.stream()
                .map(OrderLine::cartId)
                .filter(id -> id != null && id > 0)
                .toList();
            if (!cartIds.isEmpty()) {
                cartMapper.delete(
                    Wrappers.<Cart>lambdaQuery()
                        .eq(Cart::getUserId, userId)
                        .in(Cart::getId, cartIds)
                );
            }
        }

        return ServiceResult.ok(order);
    }

    public PageResult<Order> listOrders(
        Long userId,
        int page,
        int pageSize,
        Integer status,
        String orderNo,
        boolean includeDeleted
    ) {
        LambdaQueryWrapper<Order> wrapper = Wrappers.<Order>lambdaQuery();
        if (userId != null) {
            wrapper.eq(Order::getUserId, userId);
        }
        if (!includeDeleted) {
            wrapper.eq(Order::getIsDeleted, 0);
        }
        if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        if (orderNo != null && !orderNo.isBlank()) {
            wrapper.eq(Order::getOrderNo, orderNo);
        }
        wrapper.orderByDesc(Order::getCreatedAt);
        Page<Order> pager = orderMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return new PageResult<>(pager.getTotal(), pager.getRecords());
    }

    public Order getOrderById(Long orderId, Long userId) {
        LambdaQueryWrapper<Order> wrapper = Wrappers.<Order>lambdaQuery()
            .eq(Order::getId, orderId)
            .last("limit 1");
        if (userId != null) {
            wrapper.eq(Order::getUserId, userId);
        }
        return orderMapper.selectOne(wrapper);
    }

    public List<OrderItem> getOrderItems(Long orderId) {
        return orderItemMapper.selectList(
            Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, orderId)
                .orderByAsc(OrderItem::getId)
        );
    }

    public Payment getPaymentByOrder(Long orderId) {
        return paymentMapper.selectOne(
            Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderId, orderId)
                .last("limit 1")
        );
    }

    @Transactional
    public ServiceResult<Payment> payOrder(Order order, Integer payType) {
        if (order.getStatus() == null || order.getStatus() != ORDER_STATUS_PENDING) {
            return ServiceResult.fail("invalid_status");
        }
        order.setStatus(ORDER_STATUS_PAID);
        order.setPayType(payType);
        order.setPayTime(TimeUtils.utcNow());
        order.setUpdatedAt(TimeUtils.utcNow());
        orderMapper.updateById(order);

        Payment payment = paymentMapper.selectOne(
            Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderId, order.getId())
                .last("limit 1")
        );
        if (payment == null) {
            payment = new Payment();
            payment.setPaymentNo(IdUtils.generatePaymentNo());
            payment.setOrderId(order.getId());
            payment.setOrderNo(order.getOrderNo());
            payment.setUserId(order.getUserId());
            payment.setPayAmount(order.getPayAmount());
            payment.setPayType(payType);
            payment.setPayStatus(PAY_STATUS_SUCCESS);
            payment.setPayTime(TimeUtils.utcNow());
            payment.setCallbackTime(TimeUtils.utcNow());
            payment.setCallbackContent("payment success");
            payment.setCreatedAt(TimeUtils.utcNow());
            payment.setUpdatedAt(TimeUtils.utcNow());
            paymentMapper.insert(payment);
        } else {
            payment.setPayStatus(PAY_STATUS_SUCCESS);
            payment.setPayType(payType);
            payment.setPayTime(TimeUtils.utcNow());
            payment.setCallbackTime(TimeUtils.utcNow());
            payment.setCallbackContent("payment success");
            payment.setUpdatedAt(TimeUtils.utcNow());
            paymentMapper.updateById(payment);
        }

        List<OrderItem> orderItems = orderItemMapper.selectList(
            Wrappers.<OrderItem>lambdaQuery().eq(OrderItem::getOrderId, order.getId())
        );
        for (OrderItem row : orderItems) {
            Item product = itemMapper.selectById(row.getItemId());
            if (product != null) {
                int stock = product.getStock() == null ? 0 : product.getStock();
                product.setStock(Math.max(0, stock - (row.getQuantity() == null ? 0 : row.getQuantity())));
                int sales = product.getSales() == null ? 0 : product.getSales();
                product.setSales(sales + (row.getQuantity() == null ? 0 : row.getQuantity()));
                product.setUpdatedAt(TimeUtils.utcNow());
                itemMapper.updateById(product);
            }
            if (row.getItemSpecId() != null) {
                ItemSpec spec = itemSpecMapper.selectById(row.getItemSpecId());
                if (spec != null) {
                    int specStock = spec.getStock() == null ? 0 : spec.getStock();
                    spec.setStock(Math.max(0, specStock - (row.getQuantity() == null ? 0 : row.getQuantity())));
                    spec.setUpdatedAt(TimeUtils.utcNow());
                    itemSpecMapper.updateById(spec);
                }
            }
            UserBehavior behavior = new UserBehavior();
            behavior.setUserId(order.getUserId());
            behavior.setItemId(row.getItemId());
            behavior.setCategoryId(product == null ? null : product.getCategoryId());
            behavior.setBehaviorType(4);
            behavior.setBehaviorValue(null);
            behavior.setDuration(null);
            behavior.setCreatedAt(TimeUtils.utcNow());
            userBehaviorMapper.insert(behavior);
        }

        return ServiceResult.ok(payment);
    }

    public ServiceResult<Order> cancelOrder(Order order) {
        if (order.getStatus() == null || order.getStatus() != ORDER_STATUS_PENDING) {
            return ServiceResult.fail("invalid_status");
        }
        order.setStatus(ORDER_STATUS_CANCELED);
        order.setUpdatedAt(TimeUtils.utcNow());
        orderMapper.updateById(order);
        return ServiceResult.ok(order);
    }

    public ServiceResult<Order> shipOrder(Order order) {
        if (order.getStatus() == null || order.getStatus() != ORDER_STATUS_PAID) {
            return ServiceResult.fail("invalid_status");
        }
        order.setStatus(ORDER_STATUS_SHIPPED);
        order.setDeliveryTime(TimeUtils.utcNow());
        order.setUpdatedAt(TimeUtils.utcNow());
        orderMapper.updateById(order);
        return ServiceResult.ok(order);
    }

    public ServiceResult<Order> confirmOrder(Order order) {
        if (order.getStatus() == null || order.getStatus() != ORDER_STATUS_SHIPPED) {
            return ServiceResult.fail("invalid_status");
        }
        order.setStatus(ORDER_STATUS_COMPLETED);
        order.setReceiveTime(TimeUtils.utcNow());
        order.setUpdatedAt(TimeUtils.utcNow());
        orderMapper.updateById(order);
        return ServiceResult.ok(order);
    }

    @Transactional
    public ServiceResult<Order> refundOrder(Order order) {
        if (order.getStatus() == null || (order.getStatus() != ORDER_STATUS_PAID && order.getStatus() != ORDER_STATUS_SHIPPED)) {
            return ServiceResult.fail("invalid_status");
        }
        order.setStatus(ORDER_STATUS_REFUNDED);
        order.setUpdatedAt(TimeUtils.utcNow());
        orderMapper.updateById(order);

        Payment payment = paymentMapper.selectOne(
            Wrappers.<Payment>lambdaQuery()
                .eq(Payment::getOrderId, order.getId())
                .last("limit 1")
        );
        if (payment != null) {
            payment.setPayStatus(PAY_STATUS_REFUNDED);
            payment.setCallbackTime(TimeUtils.utcNow());
            payment.setCallbackContent("refund success");
            payment.setUpdatedAt(TimeUtils.utcNow());
            paymentMapper.updateById(payment);
        }
        return ServiceResult.ok(order);
    }

    private BigDecimal calculatePrice(Item item, ItemSpec spec) {
        BigDecimal base = item.getPrice() == null ? BigDecimal.ZERO : item.getPrice();
        BigDecimal adjust = spec == null || spec.getPriceAdjust() == null ? BigDecimal.ZERO : spec.getPriceAdjust();
        return base.add(adjust);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record OrderLine(Item item, ItemSpec spec, int quantity, BigDecimal price, BigDecimal total, Long cartId) {
    }
}
