package com.recommend.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recommend.shop.common.PageResult;
import com.recommend.shop.entity.UserBehavior;
import com.recommend.shop.entity.UserFavorite;
import com.recommend.shop.mapper.UserBehaviorMapper;
import com.recommend.shop.mapper.UserFavoriteMapper;
import com.recommend.shop.util.TimeUtils;
import com.recommend.shop.util.TypeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class BehaviorService {

    private final UserBehaviorMapper userBehaviorMapper;
    private final UserFavoriteMapper userFavoriteMapper;

    public PageResult<UserBehavior> listBehaviors(Long userId, int page, int pageSize) {
        LambdaQueryWrapper<UserBehavior> wrapper = Wrappers.<UserBehavior>lambdaQuery()
            .eq(UserBehavior::getUserId, userId)
            .orderByDesc(UserBehavior::getCreatedAt);
        Page<UserBehavior> pager = userBehaviorMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return new PageResult<>(pager.getTotal(), pager.getRecords());
    }

    public UserBehavior logBehavior(Long userId, Map<String, Object> payload) {
        UserBehavior behavior = new UserBehavior();
        behavior.setUserId(userId);
        behavior.setItemId(TypeUtils.toLong(payload.get("item_id")) == null ? 0L : TypeUtils.toLong(payload.get("item_id")));
        behavior.setCategoryId(TypeUtils.toLong(payload.get("category_id")));
        behavior.setBehaviorType(TypeUtils.toInt(payload.get("behavior_type")));
        behavior.setBehaviorValue(TypeUtils.toStr(payload.get("behavior_value")));
        behavior.setDuration(TypeUtils.toInt(payload.get("duration")));
        behavior.setCreatedAt(TimeUtils.utcNow());
        userBehaviorMapper.insert(behavior);
        return behavior;
    }

    public PageResult<UserFavorite> listFavorites(Long userId, int page, int pageSize) {
        LambdaQueryWrapper<UserFavorite> wrapper = Wrappers.<UserFavorite>lambdaQuery()
            .eq(UserFavorite::getUserId, userId)
            .orderByDesc(UserFavorite::getId);
        Page<UserFavorite> pager = userFavoriteMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return new PageResult<>(pager.getTotal(), pager.getRecords());
    }

    @Transactional
    public FavoriteToggleResult toggleFavorite(Long userId, Long itemId) {
        UserFavorite existing = userFavoriteMapper.selectOne(
            Wrappers.<UserFavorite>lambdaQuery()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getItemId, itemId)
                .last("limit 1")
        );
        if (existing != null) {
            userFavoriteMapper.deleteById(existing.getId());
            return new FavoriteToggleResult(null, "removed");
        }
        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(userId);
        favorite.setItemId(itemId);
        favorite.setCreatedAt(TimeUtils.utcNow());
        userFavoriteMapper.insert(favorite);
        return new FavoriteToggleResult(favorite, "added");
    }

    public record FavoriteToggleResult(UserFavorite favorite, String state) {
    }
}
