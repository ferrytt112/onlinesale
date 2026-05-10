package com.recommend.shop.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.recommend.shop.entity.Order;
import com.recommend.shop.mapper.OrderMapper;
import com.recommend.shop.util.TimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final JdbcTemplate jdbcTemplate;
    private final OrderMapper orderMapper;

    public Map<String, Object> getDashboardStats() {
        long totalUsers = queryCount("SELECT COUNT(1) FROM `user` WHERE is_deleted = 0");
        long totalItems = queryCount("SELECT COUNT(1) FROM item WHERE is_deleted = 0");
        long totalOrders = queryCount("SELECT COUNT(1) FROM `order` WHERE is_deleted = 0");

        BigDecimal totalRevenue = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(pay_amount), 0) FROM `order` WHERE status >= 1 AND is_deleted = 0",
            BigDecimal.class
        );

        long pendingOrders = queryCount("SELECT COUNT(1) FROM `order` WHERE status = 0 AND is_deleted = 0");
        long pendingReviews = queryCount("SELECT COUNT(1) FROM review WHERE status = 2 AND is_deleted = 0");

        Map<Integer, Long> orderStatusDistribution = new LinkedHashMap<>();
        jdbcTemplate.query(
            "SELECT status, COUNT(id) AS cnt FROM `order` WHERE is_deleted = 0 GROUP BY status",
            (rs, rowNum) -> {
                orderStatusDistribution.put(rs.getInt("status"), rs.getLong("cnt"));
                return null;
            }
        );

        Map<Long, Double> categorySales = new LinkedHashMap<>();
        jdbcTemplate.query(
            """
                SELECT i.category_id AS category_id, COALESCE(SUM(oi.total_price), 0) AS total
                FROM item i
                JOIN order_item oi ON i.id = oi.item_id
                JOIN `order` o ON o.id = oi.order_id
                WHERE o.status >= 1 AND o.is_deleted = 0
                GROUP BY i.category_id
                """,
            (rs, rowNum) -> {
                categorySales.put(rs.getLong("category_id"), rs.getDouble("total"));
                return null;
            }
        );

        List<Map<String, Object>> orderTrend = new ArrayList<>();
        LocalDate today = TimeUtils.utcToday();
        for (int offset = 6; offset >= 0; offset--) {
            LocalDate day = today.minusDays(offset);
            LocalDateTime start = LocalDateTime.of(day, LocalTime.MIN);
            LocalDateTime end = LocalDateTime.of(day, LocalTime.MAX);
            Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM `order` WHERE created_at >= ? AND created_at <= ? AND is_deleted = 0",
                Long.class,
                Timestamp.valueOf(start),
                Timestamp.valueOf(end)
            );
            Map<String, Object> trend = new LinkedHashMap<>();
            trend.put("date", day.toString());
            trend.put("count", count == null ? 0 : count);
            orderTrend.add(trend);
        }

        List<Order> recentOrders = orderMapper.selectList(
            Wrappers.<Order>lambdaQuery()
                .eq(Order::getIsDeleted, 0)
                .orderByDesc(Order::getCreatedAt)
                .last("limit 6")
        );

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_users", totalUsers);
        stats.put("total_items", totalItems);
        stats.put("total_orders", totalOrders);
        stats.put("total_revenue", totalRevenue == null ? 0.0 : totalRevenue.doubleValue());
        stats.put("pending_orders", pendingOrders);
        stats.put("pending_reviews", pendingReviews);
        stats.put("order_status_distribution", orderStatusDistribution);
        stats.put("category_sales", categorySales);
        stats.put("order_trend", orderTrend);
        stats.put("recent_orders", recentOrders);
        return stats;
    }

    private long queryCount(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
