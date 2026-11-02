package com.example.bishe.mapper;

import com.example.bishe.entity.GroupUser;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 群聊与用户关联表数据访问接口 (表7 group_user)。
 */
@Mapper
public interface GroupUserMapper {

    @Insert("INSERT INTO group_user(group_id, user_id) VALUES(#{groupId}, #{userId})")
    int insert(GroupUser groupUser);

    @Select("SELECT * FROM group_user WHERE group_id = #{groupId} AND user_id = #{userId}")
    GroupUser find(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Select("SELECT * FROM group_user WHERE group_id = #{groupId}")
    List<GroupUser> findByGroupId(Long groupId);

    @Select("SELECT gu.* FROM group_user gu " +
            "INNER JOIN group_chat gc ON gu.group_id = gc.group_id " +
            "WHERE gu.user_id = #{userId} AND gc.is_dissolved = 0 " +
            "AND IFNULL(gc.is_deleted, 0) = 0 AND IFNULL(gc.is_banned, 0) = 0")
    List<GroupUser> findActiveByUserId(Long userId);

    @Select("SELECT COUNT(*) FROM group_user WHERE group_id = #{groupId}")
    int countByGroupId(Long groupId);

    @Delete("DELETE FROM group_user WHERE group_id = #{groupId} AND user_id = #{userId}")
    int delete(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Delete("DELETE FROM group_user WHERE group_id = #{groupId}")
    int deleteByGroupId(Long groupId);
}
