package com.recommend.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.recommend.shop.entity.Cart;
import com.recommend.shop.mapper.CartMapper;
import com.recommend.shop.util.TimeUtils;
import com.recommend.shop.util.TypeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartMapper cartMapper;

    public List<Cart> listCartItems(Long userId) {
        return cartMapper.selectList(
            Wrappers.<Cart>lambdaQuery()
                .eq(Cart::getUserId, userId)
                .orderByDesc(Cart::getId)
        );
    }

    public Cart getCartItem(Long cartId, Long userId) {
        LambdaQueryWrapper<Cart> wrapper = Wrappers.<Cart>lambdaQuery()
            .eq(Cart::getId, cartId)
            .last("limit 1");
        if (userId != null) {
            wrapper.eq(Cart::getUserId, userId);
        }
        return cartMapper.selectOne(wrapper);
    }

    @Transactional
    public Cart addCartItem(Long userId, Long itemId, Long itemSpecId, Integer quantity) {
        int safeQuantity = Math.max(1, quantity == null ? 1 : quantity);
        LambdaQueryWrapper<Cart> wrapper = Wrappers.<Cart>lambdaQuery()
            .eq(Cart::getUserId, userId)
            .eq(Cart::getItemId, itemId)
            .last("limit 1");
        if (itemSpecId == null) {
            wrapper.isNull(Cart::getItemSpecId);
        } else {
            wrapper.eq(Cart::getItemSpecId, itemSpecId);
        }
        Cart existing = cartMapper.selectOne(wrapper);
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + safeQuantity);
            existing.setUpdatedAt(TimeUtils.utcNow());
            cartMapper.updateById(existing);
            return existing;
        }

        Cart cart = new Cart();
        cart.setUserId(userId);
        cart.setItemId(itemId);
        cart.setItemSpecId(itemSpecId);
        cart.setQuantity(safeQuantity);
        cart.setIsSelected(1);
        cart.setCreatedAt(TimeUtils.utcNow());
        cart.setUpdatedAt(TimeUtils.utcNow());
        cartMapper.insert(cart);
        return cart;
    }

    public Cart updateCartItem(Cart cartItem, Map<String, Object> payload) {
        if (payload.containsKey("quantity")) {
            Integer quantity = TypeUtils.toInt(payload.get("quantity"));
            if (quantity != null) {
                cartItem.setQuantity(Math.max(1, quantity));
            }
        }
        if (payload.containsKey("item_spec_id")) {
            cartItem.setItemSpecId(TypeUtils.toLong(payload.get("item_spec_id")));
        }
        if (payload.containsKey("is_selected")) {
            Boolean selected = TypeUtils.toBool(payload.get("is_selected"));
            cartItem.setIsSelected(Boolean.TRUE.equals(selected) ? 1 : 0);
        }
        cartItem.setUpdatedAt(TimeUtils.utcNow());
        cartMapper.updateById(cartItem);
        return cartItem;
    }

    public Cart toggleSelection(Cart cartItem, boolean selected) {
        cartItem.setIsSelected(selected ? 1 : 0);
        cartItem.setUpdatedAt(TimeUtils.utcNow());
        cartMapper.updateById(cartItem);
        return cartItem;
    }

    public void selectAll(Long userId, boolean selected) {
        LambdaUpdateWrapper<Cart> update = Wrappers.<Cart>lambdaUpdate()
            .eq(Cart::getUserId, userId)
            .set(Cart::getIsSelected, selected ? 1 : 0);
        cartMapper.update(null, update);
    }

    public void removeCartItem(Cart cartItem) {
        cartMapper.deleteById(cartItem.getId());
    }

    public void clearSelected(Long userId) {
        cartMapper.delete(
            Wrappers.<Cart>lambdaQuery()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getIsSelected, 1)
        );
    }
}
