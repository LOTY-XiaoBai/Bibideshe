package com.example.bishe.mapper;

import com.example.bishe.entity.GroupAdmin;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 群聊管理员表数据访问接口 (表9 group_admin)。
 */
@Mapper
public interface GroupAdminMapper {

    @Insert("INSERT INTO group_admin(group_id, admin_id, admin_level) VALUES(#{groupId}, #{adminId}, #{adminLevel})")
    int insert(GroupAdmin admin);

    @Select("SELECT * FROM group_admin WHERE group_id = #{groupId} AND admin_id = #{adminId}")
    GroupAdmin find(@Param("groupId") Long groupId, @Param("adminId") Long adminId);

    @Select("SELECT * FROM group_admin WHERE group_id = #{groupId}")
    List<GroupAdmin> findByGroupId(Long groupId);

    @Select("SELECT * FROM group_admin WHERE group_id = #{groupId} AND admin_level = 1")
    GroupAdmin findOwner(Long groupId);

    @Update("UPDATE group_admin SET admin_level = #{adminLevel} WHERE group_id = #{groupId} AND admin_id = #{adminId}")
    int updateLevel(@Param("groupId") Long groupId, @Param("adminId") Long adminId, @Param("adminLevel") Integer adminLevel);

    @Delete("DELETE FROM group_admin WHERE group_id = #{groupId} AND admin_id = #{adminId}")
    int delete(@Param("groupId") Long groupId, @Param("adminId") Long adminId);

    @Delete("DELETE FROM group_admin WHERE group_id = #{groupId}")
    int deleteByGroupId(Long groupId);
}
