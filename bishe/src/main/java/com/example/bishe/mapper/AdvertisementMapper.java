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
    Advertisement findById(Long adId);

    @Select("SELECT * FROM advertisement WHERE IFNULL(is_deleted, 0) = 0 ORDER BY ad_id DESC")
    List<Advertisement> findAllActive();

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
