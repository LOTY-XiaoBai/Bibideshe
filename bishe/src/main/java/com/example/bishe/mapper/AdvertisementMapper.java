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
    @Select("SELECT p.*,m.name AS merchant_name FROM advertisement p JOIN merchant m ON m.merchant_id=p.merchant_id WHERE p.is_deleted=0 AND p.review_status='APPROVED' AND m.is_banned=0 AND (p.title LIKE #{pattern} ESCAPE '!' OR p.content LIKE #{pattern} ESCAPE '!' OR m.name LIKE #{pattern} ESCAPE '!' OR m.city LIKE #{pattern} ESCAPE '!') ORDER BY p.ad_id DESC")
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    List<Advertisement> searchActive(@Param("pattern") String pattern);

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
