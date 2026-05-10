package com.recommend.shop.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.recommend.shop.entity.Item;
import com.recommend.shop.entity.UserBehavior;
import com.recommend.shop.mapper.ItemMapper;
import com.recommend.shop.mapper.UserBehaviorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final Map<Integer, Integer> BEHAVIOR_WEIGHT = Map.of(
        1, 1,
        2, 2,
        3, 3,
        4, 4,
        5, 1
    );

    private final UserBehaviorMapper userBehaviorMapper;
    private final ItemMapper itemMapper;

    public List<Item> recommendForUser(Long userId, int limit) {
        List<UserBehavior> behaviors = userBehaviorMapper.selectList(Wrappers.lambdaQuery(UserBehavior.class));
        Map<Long, Map<Long, Double>> userItemScores = buildUserItemScores(behaviors);
        SimilarityData similarityData = computeSimilarity(userItemScores);

        Map<Long, Double> userItems = userItemScores.getOrDefault(userId, Map.of());
        Map<Long, Double> candidateScores = new HashMap<>();

        for (Map.Entry<Long, Double> userEntry : userItems.entrySet()) {
            Long itemId = userEntry.getKey();
            Double score = userEntry.getValue();
            Map<Long, Double> related = similarityData.similarity().getOrDefault(itemId, Map.of());
            for (Map.Entry<Long, Double> relatedEntry : related.entrySet()) {
                Long otherId = relatedEntry.getKey();
                if (userItems.containsKey(otherId)) {
                    continue;
                }
                double next = candidateScores.getOrDefault(otherId, 0.0) + relatedEntry.getValue() * score;
                candidateScores.put(otherId, next);
            }
        }

        List<Item> activeItems = itemMapper.selectList(
            Wrappers.<Item>lambdaQuery()
                .eq(Item::getStatus, 1)
                .eq(Item::getIsDeleted, 0)
        );
        Map<Long, Item> activeMap = new LinkedHashMap<>();
        for (Item item : activeItems) {
            activeMap.put(item.getId(), item);
        }

        List<Map.Entry<Long, Double>> ranked = candidateScores.entrySet().stream()
            .filter(entry -> activeMap.containsKey(entry.getKey()))
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .toList();

        if (ranked.isEmpty()) {
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

        List<Item> result = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : ranked) {
            Item item = activeMap.get(entry.getKey());
            if (item != null) {
                result.add(item);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private Map<Long, Map<Long, Double>> buildUserItemScores(List<UserBehavior> behaviors) {
        Map<Long, Map<Long, Double>> userItemScores = new HashMap<>();
        for (UserBehavior behavior : behaviors) {
            int weight = BEHAVIOR_WEIGHT.getOrDefault(behavior.getBehaviorType(), 1);
            userItemScores
                .computeIfAbsent(behavior.getUserId(), ignored -> new HashMap<>())
                .merge(behavior.getItemId(), (double) weight, Double::sum);
        }
        return userItemScores;
    }

    private SimilarityData computeSimilarity(Map<Long, Map<Long, Double>> userItemScores) {
        Map<Long, Double> itemNorms = new HashMap<>();
        Map<Long, Map<Long, Double>> coScores = new HashMap<>();

        for (Map<Long, Double> items : userItemScores.values()) {
            List<Map.Entry<Long, Double>> itemList = items.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .toList();
            for (Map.Entry<Long, Double> entry : itemList) {
                itemNorms.merge(entry.getKey(), entry.getValue() * entry.getValue(), Double::sum);
            }
            for (Map.Entry<Long, Double> entry : itemList) {
                for (Map.Entry<Long, Double> other : itemList) {
                    if (entry.getKey().equals(other.getKey())) {
                        continue;
                    }
                    coScores
                        .computeIfAbsent(entry.getKey(), ignored -> new HashMap<>())
                        .merge(other.getKey(), entry.getValue() * other.getValue(), Double::sum);
                }
            }
        }

        Map<Long, Map<Long, Double>> similarity = new HashMap<>();
        for (Map.Entry<Long, Map<Long, Double>> row : coScores.entrySet()) {
            Map<Long, Double> related = new HashMap<>();
            for (Map.Entry<Long, Double> other : row.getValue().entrySet()) {
                double denom = Math.sqrt(itemNorms.getOrDefault(row.getKey(), 0.0))
                    * Math.sqrt(itemNorms.getOrDefault(other.getKey(), 0.0));
                related.put(other.getKey(), denom == 0 ? 0 : other.getValue() / denom);
            }
            similarity.put(row.getKey(), related);
        }
        return new SimilarityData(similarity);
    }

    private record SimilarityData(Map<Long, Map<Long, Double>> similarity) {
    }
}
