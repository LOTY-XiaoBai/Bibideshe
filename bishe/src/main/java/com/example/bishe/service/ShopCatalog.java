package com.example.bishe.service;

import com.example.bishe.common.BusinessException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class ShopCatalog {
    record Tag(String code, String label) {}

    private static final List<Tag> TAGS = List.of(new Tag("stationery", "文具"), new Tag("books", "书籍"),
            new Tag("digital", "数码配件"), new Tag("daily", "生活用品"));
    private static final List<String> CITIES = List.of(
            "北京市", "上海市", "天津市", "重庆市", "浙江省杭州市", "浙江省宁波市", "江苏省南京市", "江苏省苏州市",
            "广东省广州市", "广东省深圳市", "四川省成都市", "湖北省武汉市", "湖南省长沙市", "陕西省西安市",
            "山东省济南市", "山东省青岛市", "福建省福州市", "福建省厦门市", "河南省郑州市", "安徽省合肥市",
            "江西省南昌市", "云南省昆明市", "贵州省贵阳市", "广西壮族自治区南宁市", "辽宁省沈阳市",
            "吉林省长春市", "黑龙江省哈尔滨市", "河北省石家庄市", "山西省太原市", "海南省海口市");
    private static final Map<String, List<String>> CITY_ALIASES = cityAliases();

    private ShopCatalog() {}

    static List<Tag> tags() {
        return TAGS;
    }

    static Map<String, Object> options(List<String> activeCities) {
        return Map.of("tags", TAGS, "cities", activeCities.stream().map(ShopCatalog::canonicalCity)
                .distinct().sorted().toList());
    }

    static String canonicalCity(String city) {
        return aliases(city).get(0);
    }

    static List<String> aliases(String city) {
        return CITY_ALIASES.getOrDefault(city.trim(), List.of(city.trim()));
    }

    private static Map<String, List<String>> cityAliases() {
        Map<String, List<String>> index = new HashMap<>();
        for (String canonical : CITIES) {
            String shortName = canonical.replaceFirst("^.+?(省|自治区)", "");
            var aliases = new LinkedHashSet<>(List.of(canonical, shortName, shortName.substring(0, shortName.length() - 1)));
            if (canonical.endsWith("南宁市")) aliases.add("广西南宁市");
            List<String> values = List.copyOf(aliases);
            values.forEach(alias -> index.put(alias, values));
        }
        return Map.copyOf(index);
    }

    static List<String> productTags(Object value) {
        if (!(value instanceof Collection<?> tags)) throw new BusinessException("商品标签格式无效");
        return validateTags(tags, false);
    }

    static List<String> filterTags(String value) {
        return value.isBlank() ? List.of() : validateTags(Arrays.asList(value.split(",", -1)), true);
    }

    private static List<String> validateTags(Collection<?> values, boolean allowOther) {
        if (values.size() > TAGS.size() + (allowOther ? 1 : 0)) throw new BusinessException("商品标签数量无效");
        List<String> selected = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof String code) || !((allowOther && "other".equals(code))
                    || TAGS.stream().anyMatch(tag -> tag.code().equals(code)))) {
                throw new BusinessException("商品标签无效");
            }
            if (!selected.contains(code)) selected.add(code);
        }
        return selected;
    }

    static BigDecimal price(String value) {
        if (value.isBlank()) return null;
        if (!value.trim().matches("[0-9]{1,8}(\\.[0-9]{1,2})?")) {
            throw new BusinessException("筛选价格须为非负金额，最多两位小数");
        }
        return new BigDecimal(value.trim());
    }
}
