package com.example.bishe.mapper;

import com.example.bishe.entity.Student;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 学生表数据访问接口。
 */
@Mapper
public interface StudentMapper {

    /**
     * 根据学生ID查询学生。
     */
    @Select("SELECT * FROM student WHERE student_id = #{studentId}")
    Student findById(Long studentId);

    /**
     * 按学生ID列表批量查询。
     */
    @Select("<script>SELECT * FROM student WHERE student_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<Student> findByIds(@Param("ids") List<Long> ids);

    /**
     * 新增学生。
     */
    @Insert("INSERT INTO student(student_id, nickname, password, signature, avatar, comprehensive_score) " +
            "VALUES(#{studentId}, #{nickname}, #{password}, #{signature}, #{avatar}, #{comprehensiveScore})")
    int insert(Student student);

    /**
     * 更新学生综合评分(所有已结课课程成绩的平均分)。
     */
    @Update("UPDATE student SET comprehensive_score = #{score} WHERE student_id = #{studentId}")
    int updateComprehensiveScore(@Param("studentId") Long studentId, @Param("score") BigDecimal score);

    /**
     * 更新学生个人资料(昵称/个性签名/头像)。
     */
    @Update("UPDATE student SET nickname = #{nickname}, signature = #{signature}, avatar = #{avatar} " +
            "WHERE student_id = #{studentId}")
    int updateProfile(Student student);

    /**
     * 按昵称模糊搜索学生(用于加好友)。
     */
    @Select("SELECT * FROM student WHERE IFNULL(is_deleted,0)=0 AND IFNULL(is_banned,0)=0 " +
            "AND nickname LIKE CONCAT('%', #{keyword}, '%') ORDER BY student_id LIMIT 50")
    List<Student> searchByNickname(@Param("keyword") String keyword);

    @Update("UPDATE student SET is_banned = 1, ban_reason = #{banReason}, banned_by = #{bannedBy}, " +
            "banned_at = NOW() WHERE student_id = #{studentId}")
    int ban(@Param("studentId") Long studentId,
            @Param("banReason") String banReason,
            @Param("bannedBy") String bannedBy);

    @Update("UPDATE student SET is_banned = 0, ban_reason = NULL, banned_by = NULL, banned_at = NULL " +
            "WHERE student_id = #{studentId}")
    int unban(Long studentId);

    @Update("UPDATE student SET is_deleted = 1, is_banned = 1, ban_reason = #{banReason}, " +
            "banned_by = #{bannedBy}, banned_at = NOW() WHERE student_id = #{studentId}")
    int softDelete(@Param("studentId") Long studentId,
                   @Param("banReason") String banReason,
                   @Param("bannedBy") String bannedBy);

    /**
     * 管理员分页查询学生(支持昵称模糊 / ID 精确)。
     */
    @Select("<script>"
            + "SELECT * FROM student"
            + "<where>"
            + "  <if test='keyword != null and keyword != \"\"'>"
            + "    (nickname LIKE CONCAT('%', #{keyword}, '%')"
            + "    <if test='id != null'> OR student_id = #{id}</if>)"
            + "  </if>"
            + "</where>"
            + " ORDER BY student_id DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<Student> findForAdmin(@Param("keyword") String keyword,
                               @Param("id") Long id,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    @Select("<script>"
            + "SELECT COUNT(*) FROM student"
            + "<where>"
            + "  <if test='keyword != null and keyword != \"\"'>"
            + "    (nickname LIKE CONCAT('%', #{keyword}, '%')"
            + "    <if test='id != null'> OR student_id = #{id}</if>)"
            + "  </if>"
            + "</where>"
            + "</script>")
    int countForAdmin(@Param("keyword") String keyword, @Param("id") Long id);

    /**
     * 删除学生账号。
     */
    @Delete("DELETE FROM student WHERE student_id = #{studentId}")
    int deleteById(Long studentId);
}
