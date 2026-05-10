package com.recommend.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recommend.shop.common.PageResult;
import com.recommend.shop.entity.Category;
import com.recommend.shop.entity.Item;
import com.recommend.shop.entity.ItemImage;
import com.recommend.shop.entity.ItemSpec;
import com.recommend.shop.mapper.CategoryMapper;
import com.recommend.shop.mapper.ItemImageMapper;
import com.recommend.shop.mapper.ItemMapper;
import com.recommend.shop.mapper.ItemSpecMapper;
import com.recommend.shop.util.Serializer;
import com.recommend.shop.util.TimeUtils;
import com.recommend.shop.util.TypeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItemService {

    private final CategoryMapper categoryMapper;
    private final ItemMapper itemMapper;
    private final ItemImageMapper itemImageMapper;
    private final ItemSpecMapper itemSpecMapper;
    private final Serializer serializer;

    public List<Map<String, Object>> buildCategoryTree(List<Category> categories) {
        Map<Long, Map<String, Object>> nodes = new LinkedHashMap<>();
        for (Category category : categories) {
            Map<String, Object> node = serializer.toMap(category);
            node.put("children", new ArrayList<>());
            nodes.put(category.getId(), node);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Category category : categories) {
            Map<String, Object> node = nodes.get(category.getId());
            Long parentId = category.getParentId();
            if (parentId != null && parentId != 0 && nodes.containsKey(parentId)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) nodes.get(parentId).get("children");
                children.add(node);
            } else {
                roots.add(node);
            }
        }
        Comparator<Map<String, Object>> sorter = Comparator
            .comparing((Map<String, Object> row) -> TypeUtils.safeInt(row.get("sort_order"), 0))
            .thenComparing(row -> TypeUtils.toLong(row.get("id")), Comparator.nullsLast(Long::compareTo));
        for (Map<String, Object> node : nodes.values()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
            children.sort(sorter);
        }
        roots.sort(sorter);
        return roots;
    }

    public PageResult<Category> listCategories(int page, int pageSize, String keyword, Integer status, boolean includeDeleted) {
        LambdaQueryWrapper<Category> wrapper = Wrappers.<Category>lambdaQuery();
        if (!includeDeleted) {
            wrapper.eq(Category::getIsDeleted, 0);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(q -> q.like(Category::getName, keyword).or().like(Category::getDescription, keyword));
        }
        if (status != null) {
            wrapper.eq(Category::getStatus, status);
        }
        wrapper.orderByAsc(Category::getSortOrder, Category::getId);
        Page<Category> pager = categoryMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return new PageResult<>(pager.getTotal(), pager.getRecords());
    }

    public List<Category> getActiveCategories() {
        return categoryMapper.selectList(
            Wrappers.<Category>lambdaQuery()
                .eq(Category::getIsDeleted, 0)
                .eq(Category::getStatus, 1)
                .orderByAsc(Category::getSortOrder, Category::getId)
        );
    }

    public List<Map<String, Object>> getCategoryTree() {
        return buildCategoryTree(getActiveCategories());
    }

    public int computeCategoryLevel(Long parentId) {
        if (parentId == null || parentId == 0) {
            return 1;
        }
        Category parent = categoryMapper.selectOne(
            Wrappers.<Category>lambdaQuery()
                .eq(Category::getId, parentId)
                .eq(Category::getIsDeleted, 0)
                .last("limit 1")
        );
        return parent == null ? 1 : (parent.getLevel() == null ? 1 : parent.getLevel() + 1);
    }

    public Category createCategory(Map<String, Object> payload) {
        Category category = new Category();
        Long parentId = TypeUtils.toLong(payload.get("parent_id"));
        category.setParentId(parentId == null ? 0L : parentId);
        category.setName(TypeUtils.toStr(payload.get("name")));
        category.setIcon(TypeUtils.toStr(payload.get("icon")));
        category.setDescription(TypeUtils.toStr(payload.get("description")));
        category.setSortOrder(TypeUtils.toInt(payload.get("sort_order")) == null ? 0 : TypeUtils.toInt(payload.get("sort_order")));
        category.setLevel(computeCategoryLevel(parentId));
        category.setStatus(TypeUtils.toInt(payload.get("status")) == null ? 1 : TypeUtils.toInt(payload.get("status")));
        category.setCreatedAt(TimeUtils.utcNow());
        category.setUpdatedAt(TimeUtils.utcNow());
        category.setIsDeleted(0);
        categoryMapper.insert(category);
        return category;
    }

    public Category updateCategory(Category category, Map<String, Object> payload) {
        if (payload.containsKey("parent_id")) {
            Long parentId = TypeUtils.toLong(payload.get("parent_id"));
            category.setParentId(parentId == null ? 0L : parentId);
            category.setLevel(computeCategoryLevel(category.getParentId()));
        }
        if (payload.containsKey("name")) {
            category.setName(TypeUtils.toStr(payload.get("name")));
        }
        if (payload.containsKey("icon")) {
            category.setIcon(TypeUtils.toStr(payload.get("icon")));
        }
        if (payload.containsKey("description")) {
            category.setDescription(TypeUtils.toStr(payload.get("description")));
        }
        if (payload.containsKey("sort_order")) {
            category.setSortOrder(TypeUtils.toInt(payload.get("sort_order")));
        }
        if (payload.containsKey("status")) {
            category.setStatus(TypeUtils.toInt(payload.get("status")));
        }
        category.setUpdatedAt(TimeUtils.utcNow());
        categoryMapper.updateById(category);
        return category;
    }

    public Category deleteCategory(Category category) {
        category.setIsDeleted(1);
        category.setUpdatedAt(TimeUtils.utcNow());
        categoryMapper.updateById(category);
        return category;
    }

    public PageResult<Item> listItems(
        int page,
        int pageSize,
        String keyword,
        Long categoryId,
        Integer status,
        Integer isHot,
        Integer isNew,
        Integer isRecommend,
        boolean includeDeleted
    ) {
        LambdaQueryWrapper<Item> wrapper = Wrappers.<Item>lambdaQuery();
        if (!includeDeleted) {
            wrapper.eq(Item::getIsDeleted, 0);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(q -> q.like(Item::getName, keyword).or().like(Item::getSubtitle, keyword).or().like(Item::getBrand, keyword));
        }
        if (categoryId != null) {
            List<Category> categories = categoryMapper.selectList(
                Wrappers.<Category>lambdaQuery().eq(Category::getIsDeleted, 0)
            );
            Map<Long, List<Long>> childrenMap = new HashMap<>();
            for (Category category : categories) {
                childrenMap.computeIfAbsent(category.getParentId(), ignore -> new ArrayList<>()).add(category.getId());
            }
            List<Long> ids = new ArrayList<>();
            ids.add(categoryId);
            ArrayDeque<Long> stack = new ArrayDeque<>();
            stack.push(categoryId);
            while (!stack.isEmpty()) {
                Long current = stack.pop();
                for (Long childId : childrenMap.getOrDefault(current, List.of())) {
                    if (!ids.contains(childId)) {
                        ids.add(childId);
                        stack.push(childId);
                    }
                }
            }
            wrapper.in(Item::getCategoryId, ids);
        }
        if (status != null) {
            wrapper.eq(Item::getStatus, status);
        }
        if (isHot != null) {
            wrapper.eq(Item::getIsHot, isHot);
        }
        if (isNew != null) {
            wrapper.eq(Item::getIsNew, isNew);
        }
        if (isRecommend != null) {
            wrapper.eq(Item::getIsRecommend, isRecommend);
        }
        wrapper.orderByAsc(Item::getSortOrder)
            .orderByDesc(Item::getId);
        Page<Item> pager = itemMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return new PageResult<>(pager.getTotal(), pager.getRecords());
    }

    public Item getItemById(Long itemId, boolean includeDeleted) {
        LambdaQueryWrapper<Item> wrapper = Wrappers.<Item>lambdaQuery()
            .eq(Item::getId, itemId)
            .last("limit 1");
        if (!includeDeleted) {
            wrapper.eq(Item::getIsDeleted, 0);
        }
        return itemMapper.selectOne(wrapper);
    }

    public Item createItem(Map<String, Object> payload) {
        Item item = new Item();
        item.setCategoryId(TypeUtils.toLong(payload.get("category_id")));
        item.setName(TypeUtils.toStr(payload.get("name")));
        item.setSubtitle(TypeUtils.toStr(payload.get("subtitle")));
        item.setMainImage(TypeUtils.toStr(payload.get("main_image")));
        item.setDetail(TypeUtils.toStr(payload.get("detail")));
        item.setPrice(TypeUtils.toDecimal(payload.get("price")) == null ? BigDecimal.ZERO : TypeUtils.toDecimal(payload.get("price")));
        item.setOriginalPrice(TypeUtils.toDecimal(payload.get("original_price")));
        item.setStock(TypeUtils.toInt(payload.get("stock")) == null ? 0 : TypeUtils.toInt(payload.get("stock")));
        item.setSales(TypeUtils.toInt(payload.get("sales")) == null ? 0 : TypeUtils.toInt(payload.get("sales")));
        item.setUnit(TypeUtils.toStr(payload.get("unit")));
        item.setWeight(TypeUtils.toDecimal(payload.get("weight")));
        item.setBrand(TypeUtils.toStr(payload.get("brand")));
        item.setStatus(TypeUtils.toInt(payload.get("status")) == null ? 0 : TypeUtils.toInt(payload.get("status")));
        item.setIsHot(Boolean.TRUE.equals(TypeUtils.toBool(payload.get("is_hot"))) ? 1 : 0);
        item.setIsNew(Boolean.TRUE.equals(TypeUtils.toBool(payload.get("is_new"))) ? 1 : 0);
        item.setIsRecommend(Boolean.TRUE.equals(TypeUtils.toBool(payload.get("is_recommend"))) ? 1 : 0);
        item.setViewCount(TypeUtils.toInt(payload.get("view_count")) == null ? 0 : TypeUtils.toInt(payload.get("view_count")));
        item.setSortOrder(TypeUtils.toInt(payload.get("sort_order")) == null ? 0 : TypeUtils.toInt(payload.get("sort_order")));
        item.setCreatedAt(TimeUtils.utcNow());
        item.setUpdatedAt(TimeUtils.utcNow());
        item.setIsDeleted(0);
        itemMapper.insert(item);
        return item;
    }

    public Item updateItem(Item item, Map<String, Object> payload) {
        if (payload.containsKey("category_id")) {
            item.setCategoryId(TypeUtils.toLong(payload.get("category_id")));
        }
        if (payload.containsKey("name")) {
            item.setName(TypeUtils.toStr(payload.get("name")));
        }
        if (payload.containsKey("subtitle")) {
            item.setSubtitle(TypeUtils.toStr(payload.get("subtitle")));
        }
        if (payload.containsKey("main_image")) {
            item.setMainImage(TypeUtils.toStr(payload.get("main_image")));
        }
        if (payload.containsKey("detail")) {
            item.setDetail(TypeUtils.toStr(payload.get("detail")));
        }
        if (payload.containsKey("price")) {
            item.setPrice(TypeUtils.toDecimal(payload.get("price")));
        }
        if (payload.containsKey("original_price")) {
            item.setOriginalPrice(TypeUtils.toDecimal(payload.get("original_price")));
        }
        if (payload.containsKey("stock")) {
            item.setStock(TypeUtils.toInt(payload.get("stock")));
        }
        if (payload.containsKey("sales")) {
            item.setSales(TypeUtils.toInt(payload.get("sales")));
        }
        if (payload.containsKey("unit")) {
            item.setUnit(TypeUtils.toStr(payload.get("unit")));
        }
        if (payload.containsKey("weight")) {
            item.setWeight(TypeUtils.toDecimal(payload.get("weight")));
        }
        if (payload.containsKey("brand")) {
            item.setBrand(TypeUtils.toStr(payload.get("brand")));
        }
        if (payload.containsKey("status")) {
            item.setStatus(TypeUtils.toInt(payload.get("status")));
        }
        if (payload.containsKey("is_hot")) {
            Boolean value = TypeUtils.toBool(payload.get("is_hot"));
            item.setIsHot(Boolean.TRUE.equals(value) ? 1 : 0);
        }
        if (payload.containsKey("is_new")) {
            Boolean value = TypeUtils.toBool(payload.get("is_new"));
            item.setIsNew(Boolean.TRUE.equals(value) ? 1 : 0);
        }
        if (payload.containsKey("is_recommend")) {
            Boolean value = TypeUtils.toBool(payload.get("is_recommend"));
            item.setIsRecommend(Boolean.TRUE.equals(value) ? 1 : 0);
        }
        if (payload.containsKey("view_count")) {
            item.setViewCount(TypeUtils.toInt(payload.get("view_count")));
        }
        if (payload.containsKey("sort_order")) {
            item.setSortOrder(TypeUtils.toInt(payload.get("sort_order")));
        }
        item.setUpdatedAt(TimeUtils.utcNow());
        itemMapper.updateById(item);
        return item;
    }

    public Item deleteItem(Item item) {
        item.setIsDeleted(1);
        item.setUpdatedAt(TimeUtils.utcNow());
        itemMapper.updateById(item);
        return item;
    }

    public List<ItemImage> listItemImages(Long itemId) {
        return itemImageMapper.selectList(
            Wrappers.<ItemImage>lambdaQuery()
                .eq(ItemImage::getItemId, itemId)
                .orderByAsc(ItemImage::getSortOrder, ItemImage::getId)
        );
    }

    @Transactional
    public ItemImage createItemImage(Long itemId, Map<String, Object> payload) {
        ItemImage image = new ItemImage();
        image.setItemId(itemId);
        image.setImageUrl(TypeUtils.toStr(payload.get("image_url")));
        image.setSortOrder(TypeUtils.toInt(payload.get("sort_order")) == null ? 0 : TypeUtils.toInt(payload.get("sort_order")));
        image.setIsMain(Boolean.TRUE.equals(TypeUtils.toBool(payload.get("is_main"))) ? 1 : 0);
        image.setCreatedAt(TimeUtils.utcNow());
        image.setUpdatedAt(TimeUtils.utcNow());
        if (image.getIsMain() != null && image.getIsMain() == 1) {
            itemImageMapper.update(null,
                Wrappers.<ItemImage>lambdaUpdate()
                    .eq(ItemImage::getItemId, itemId)
                    .set(ItemImage::getIsMain, 0)
            );
            itemMapper.update(null,
                Wrappers.<Item>lambdaUpdate()
                    .eq(Item::getId, itemId)
                    .set(Item::getMainImage, image.getImageUrl())
            );
        }
        itemImageMapper.insert(image);
        return image;
    }

    @Transactional
    public ItemImage updateItemImage(ItemImage image, Map<String, Object> payload) {
        if (payload.containsKey("image_url")) {
            image.setImageUrl(TypeUtils.toStr(payload.get("image_url")));
        }
        if (payload.containsKey("sort_order")) {
            image.setSortOrder(TypeUtils.toInt(payload.get("sort_order")));
        }
        if (payload.containsKey("is_main")) {
            Boolean value = TypeUtils.toBool(payload.get("is_main"));
            image.setIsMain(Boolean.TRUE.equals(value) ? 1 : 0);
        }
        if (Boolean.TRUE.equals(TypeUtils.toBool(payload.get("is_main")))) {
            itemImageMapper.update(null,
                Wrappers.<ItemImage>lambdaUpdate()
                    .eq(ItemImage::getItemId, image.getItemId())
                    .set(ItemImage::getIsMain, 0)
            );
            image.setIsMain(1);
            itemMapper.update(null,
                Wrappers.<Item>lambdaUpdate()
                    .eq(Item::getId, image.getItemId())
                    .set(Item::getMainImage, image.getImageUrl())
            );
        }
        image.setUpdatedAt(TimeUtils.utcNow());
        itemImageMapper.updateById(image);
        return image;
    }

    public void deleteItemImage(ItemImage image) {
        itemImageMapper.deleteById(image.getId());
    }

    public List<ItemSpec> listItemSpecs(Long itemId) {
        return itemSpecMapper.selectList(
            Wrappers.<ItemSpec>lambdaQuery()
                .eq(ItemSpec::getItemId, itemId)
                .orderByAsc(ItemSpec::getId)
        );
    }

    public ItemSpec createItemSpec(Long itemId, Map<String, Object> payload) {
        ItemSpec spec = new ItemSpec();
        spec.setItemId(itemId);
        spec.setSpecName(TypeUtils.toStr(payload.get("spec_name")));
        spec.setSpecValue(TypeUtils.toStr(payload.get("spec_value")));
        spec.setPriceAdjust(TypeUtils.toDecimal(payload.get("price_adjust")) == null ? BigDecimal.ZERO : TypeUtils.toDecimal(payload.get("price_adjust")));
        spec.setStock(TypeUtils.toInt(payload.get("stock")) == null ? 0 : TypeUtils.toInt(payload.get("stock")));
        spec.setImageUrl(TypeUtils.toStr(payload.get("image_url")));
        spec.setCreatedAt(TimeUtils.utcNow());
        spec.setUpdatedAt(TimeUtils.utcNow());
        itemSpecMapper.insert(spec);
        return spec;
    }

    public ItemSpec updateItemSpec(ItemSpec spec, Map<String, Object> payload) {
        if (payload.containsKey("spec_name")) {
            spec.setSpecName(TypeUtils.toStr(payload.get("spec_name")));
        }
        if (payload.containsKey("spec_value")) {
            spec.setSpecValue(TypeUtils.toStr(payload.get("spec_value")));
        }
        if (payload.containsKey("price_adjust")) {
            spec.setPriceAdjust(TypeUtils.toDecimal(payload.get("price_adjust")));
        }
        if (payload.containsKey("stock")) {
            spec.setStock(TypeUtils.toInt(payload.get("stock")));
        }
        if (payload.containsKey("image_url")) {
            spec.setImageUrl(TypeUtils.toStr(payload.get("image_url")));
        }
        spec.setUpdatedAt(TimeUtils.utcNow());
        itemSpecMapper.updateById(spec);
        return spec;
    }

    public void deleteItemSpec(ItemSpec spec) {
        itemSpecMapper.deleteById(spec.getId());
    }

    public Item incrementItemView(Long itemId) {
        Item item = itemMapper.selectOne(
            Wrappers.<Item>lambdaQuery()
                .eq(Item::getId, itemId)
                .eq(Item::getIsDeleted, 0)
                .last("limit 1")
        );
        if (item == null) {
            return null;
        }
        Integer current = item.getViewCount() == null ? 0 : item.getViewCount();
        item.setViewCount(current + 1);
        item.setUpdatedAt(TimeUtils.utcNow());
        itemMapper.updateById(item);
        return item;
    }

    public List<Item> listActiveItemsForRecommendation(int limit) {
        return itemMapper.selectList(
            Wrappers.<Item>lambdaQuery()
                .eq(Item::getStatus, 1)
                .eq(Item::getIsDeleted, 0)
                .orderByDesc(Item::getIsRecommend)
                .orderByDesc(Item::getIsHot)
                .orderByDesc(Item::getIsNew)
                .orderByDesc(Item::getSales)
                .last("limit " + limit)
        );
    }

    public ItemImage getItemImageById(Long imageId) {
        return itemImageMapper.selectById(imageId);
    }

    public ItemSpec getItemSpecById(Long specId) {
        return itemSpecMapper.selectById(specId);
    }

    public Category getCategoryById(Long categoryId, boolean includeDeleted) {
        LambdaQueryWrapper<Category> wrapper = Wrappers.<Category>lambdaQuery()
            .eq(Category::getId, categoryId)
            .last("limit 1");
        if (!includeDeleted) {
            wrapper.eq(Category::getIsDeleted, 0);
        }
        return categoryMapper.selectOne(wrapper);
    }

    public List<Item> findActiveItemsByIds(List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        return itemMapper.selectList(
            Wrappers.<Item>lambdaQuery()
                .in(Item::getId, itemIds.stream().filter(Objects::nonNull).collect(Collectors.toList()))
                .eq(Item::getStatus, 1)
                .eq(Item::getIsDeleted, 0)
        );
    }
}
