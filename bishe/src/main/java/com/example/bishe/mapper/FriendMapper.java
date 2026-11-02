package com.example.bishe.mapper;

import com.example.bishe.entity.Friend;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 好友表数据访问接口 (表5 friend)。
 */
@Mapper
public interface FriendMapper {

    @Insert("INSERT INTO friend(friend_a_id, friend_b_id) VALUES(#{friendAId}, #{friendBId})")
    int insert(Friend friend);

    @Select("SELECT * FROM friend WHERE friend_a_id = #{a} AND friend_b_id = #{b}")
    Friend find(@Param("a") Long friendAId, @Param("b") Long friendBId);

    @Select("SELECT * FROM friend WHERE friend_a_id = #{userId} OR friend_b_id = #{userId}")
    List<Friend> findByUserId(Long userId);

    @Delete("DELETE FROM friend WHERE friend_a_id = #{a} AND friend_b_id = #{b}")
    int delete(@Param("a") Long friendAId, @Param("b") Long friendBId);

    @Delete("DELETE FROM friend WHERE friend_a_id = #{userId} OR friend_b_id = #{userId}")
    int deleteByUserId(Long userId);
}
