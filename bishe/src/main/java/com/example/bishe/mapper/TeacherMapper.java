package com.example.bishe.mapper;

import com.example.bishe.entity.Teacher;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 教师表数据访问接口。
 */
@Mapper
public interface TeacherMapper {

    /**
     * 根据教师ID查询教师。
     */
    @Select("SELECT * FROM teacher WHERE teacher_id = #{teacherId}")
    Teacher findById(Long teacherId);

    @Select("SELECT COUNT(*) FROM course WHERE teacher_id = #{teacherId} AND IFNULL(is_finished,0)=0")
    int countActiveCourses(Long teacherId);

    /**
     * 按教师ID列表批量查询。
     */
    @Select("<script>SELECT * FROM teacher WHERE teacher_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<Teacher> findByIds(@Param("ids") List<Long> ids);

    /**
     * 新增教师。
     */
    @Insert("INSERT INTO teacher(teacher_id, name, signature, password, avatar, comprehensive_score, course_count) " +
            "VALUES(#{teacherId}, #{name}, #{signature}, #{password}, #{avatar}, #{comprehensiveScore}, #{courseCount})")
    int insert(Teacher teacher);

    /**
     * 已有课程数量 +1。
     */
    @Update("UPDATE teacher SET course_count = course_count + 1 WHERE teacher_id = #{teacherId}")
    int incrementCourseCount(Long teacherId);

    /**
     * 已有课程数量 -1(不小于0)。
     */
    @Update("UPDATE teacher SET course_count = GREATEST(course_count - 1, 0) WHERE teacher_id = #{teacherId}")
    int decrementCourseCount(Long teacherId);

    /**
     * 已有课程数量归零(用于删除教师全部课程后重置)。
     */
    @Update("UPDATE teacher SET course_count = 0 WHERE teacher_id = #{teacherId}")
    int resetCourseCount(Long teacherId);

    /**
     * 更新教师个人资料(姓名/个性签名/头像)。
     */
    @Update("UPDATE teacher SET name = #{name}, signature = #{signature}, avatar = #{avatar} " +
            "WHERE teacher_id = #{teacherId}")
    int updateProfile(Teacher teacher);

    /**
     * 更新教师综合评分(学生打分平均值)。
     */
    @Update("UPDATE teacher SET comprehensive_score = #{score} WHERE teacher_id = #{teacherId}")
    int updateComprehensiveScore(@Param("teacherId") Long teacherId, @Param("score") BigDecimal score);

    /**
     * 按姓名模糊搜索教师(用于加好友)。
     */
    @Select("SELECT * FROM teacher WHERE IFNULL(is_deleted,0)=0 AND IFNULL(is_banned,0)=0 " +
            "AND name LIKE CONCAT('%', #{keyword}, '%') ORDER BY teacher_id LIMIT 50")
    List<Teacher> searchByName(@Param("keyword") String keyword);

    @Update("UPDATE teacher SET is_banned = 1, ban_reason = #{banReason}, banned_by = #{bannedBy}, " +
            "banned_at = NOW() WHERE teacher_id = #{teacherId}")
    int ban(@Param("teacherId") Long teacherId,
            @Param("banReason") String banReason,
            @Param("bannedBy") String bannedBy);

    @Update("UPDATE teacher SET is_banned = 0, ban_reason = NULL, banned_by = NULL, banned_at = NULL " +
            "WHERE teacher_id = #{teacherId}")
    int unban(Long teacherId);

    @Update("UPDATE teacher SET is_deleted = 1, is_banned = 1, ban_reason = #{banReason}, " +
            "banned_by = #{bannedBy}, banned_at = NOW() WHERE teacher_id = #{teacherId}")
    int softDelete(@Param("teacherId") Long teacherId,
                   @Param("banReason") String banReason,
                   @Param("bannedBy") String bannedBy);

    /**
     * 管理员分页查询教师(支持姓名模糊 / ID 精确)。
     */
    @Select("<script>"
            + "SELECT * FROM teacher"
            + "<where>"
            + "  <if test='keyword != null and keyword != \"\"'>"
            + "    (name LIKE CONCAT('%', #{keyword}, '%')"
            + "    <if test='id != null'> OR teacher_id = #{id}</if>)"
            + "  </if>"
            + "</where>"
            + " ORDER BY teacher_id DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<Teacher> findForAdmin(@Param("keyword") String keyword,
                               @Param("id") Long id,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    @Select("<script>"
            + "SELECT COUNT(*) FROM teacher"
            + "<where>"
            + "  <if test='keyword != null and keyword != \"\"'>"
            + "    (name LIKE CONCAT('%', #{keyword}, '%')"
            + "    <if test='id != null'> OR teacher_id = #{id}</if>)"
            + "  </if>"
            + "</where>"
            + "</script>")
    int countForAdmin(@Param("keyword") String keyword, @Param("id") Long id);

    /**
     * 删除教师账号。
     */
    @Delete("DELETE FROM teacher WHERE teacher_id = #{teacherId}")
    int deleteById(Long teacherId);
}
