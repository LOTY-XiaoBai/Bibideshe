package com.example.bishe.mapper;

import com.example.bishe.entity.Admin;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 管理员表数据访问接口。
 */
@Mapper
public interface AdminMapper {

    /**
     * 根据管理员ID查询管理员。
     */
    @Select("SELECT * FROM admin WHERE admin_id = #{adminId}")
    Admin findById(String adminId);

    /**
     * 统计管理员账号数量。
     */
    @Select("SELECT COUNT(*) FROM admin")
    long countAll();

    /**
     * 新增管理员。
     */
    @Insert("INSERT INTO admin(admin_id, password) VALUES(#{adminId}, #{password})")
    int insert(Admin admin);
}
