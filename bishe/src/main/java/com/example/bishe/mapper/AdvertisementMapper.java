package com.example.bishe.mapper;

import com.example.bishe.entity.Advertisement;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 广告/商品表数据访问接口。
 */
@Mapper
public interface AdvertisementMapper {

    @Insert("INSERT INTO advertisement(title, content, image_url, price, publisher_id, score, is_deleted) " +
            "VALUES(#{title}, #{content}, #{imageUrl}, #{price}, #{publisherId}, #{score}, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "adId")
    int insert(Advertisement ad);

    @Select("SELECT * FROM advertisement WHERE ad_id = #{adId}")
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    Advertisement findById(Long adId);

    @Select("SELECT * FROM advertisement WHERE is_deleted=0 AND merchant_id IS NOT NULL AND review_status='APPROVED' ORDER BY ad_id DESC")
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    List<Advertisement> findAllActive();
    String ACTIVE_PRODUCTS = " FROM advertisement p JOIN merchant m ON m.merchant_id=p.merchant_id"
            + " WHERE p.is_deleted=0 AND p.review_status='APPROVED' AND m.is_banned=0";
    String TAG_CODES = "(SELECT GROUP_CONCAT(t.tag_code ORDER BY t.tag_code) FROM shop_product_tag t WHERE t.product_id=p.ad_id)";

    @Select("<script>SELECT p.*,m.name AS merchant_name,m.city AS ship_city," + TAG_CODES + " AS tag_codes"
            + ACTIVE_PRODUCTS + " " + """
            AND (p.title LIKE #{pattern} ESCAPE '!' OR p.content LIKE #{pattern} ESCAPE '!'
                 OR m.name LIKE #{pattern} ESCAPE '!' OR m.city LIKE #{pattern} ESCAPE '!')
            <if test="minPrice != null">AND p.price &gt;= #{minPrice}</if>
            <if test="maxPrice != null">AND p.price &lt;= #{maxPrice}</if>
            <if test="!cities.isEmpty()">AND TRIM(m.city) IN
              <foreach collection="cities" item="city" open="(" separator="," close=")">#{city}</foreach>
            </if>
            <if test="other or !tags.isEmpty()">
              AND (
                <if test="other">NOT EXISTS (SELECT 1 FROM shop_product_tag t WHERE t.product_id=p.ad_id)</if>
                <if test="other and !tags.isEmpty()">OR</if>
                <if test="!tags.isEmpty()">
                  EXISTS (SELECT 1 FROM shop_product_tag t WHERE t.product_id=p.ad_id AND t.tag_code IN
                    <foreach collection="tags" item="tag" open="(" separator="," close=")">#{tag}</foreach>)
                </if>
              )
            </if>
            ORDER BY p.ad_id DESC
            </script>
            """)
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    List<Advertisement> searchActive(@Param("pattern") String pattern, @Param("minPrice") BigDecimal minPrice,
                                     @Param("maxPrice") BigDecimal maxPrice, @Param("cities") List<String> cities,
                                     @Param("tags") List<String> tags, @Param("other") boolean other);

    @Select("SELECT DISTINCT TRIM(m.city)" + ACTIVE_PRODUCTS + " AND TRIM(m.city) != ''")
    List<String> activeCities();

    @Select("SELECT * FROM advertisement ORDER BY ad_id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Advertisement> findAllForAdmin(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM advertisement")
    int countAll();

    @Update("UPDATE advertisement SET title=#{title}, content=#{content}, image_url=#{imageUrl}, " +
            "price=#{price} WHERE ad_id=#{adId}")
    int update(Advertisement ad);

    @Update("UPDATE advertisement SET is_deleted = 1 WHERE ad_id = #{adId}")
    int softDelete(Long adId);

    @Update("UPDATE advertisement SET score = #{score} WHERE ad_id = #{adId}")
    int updateScore(@Param("adId") Long adId, @Param("score") BigDecimal score);
}
