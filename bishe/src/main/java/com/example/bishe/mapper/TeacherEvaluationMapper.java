package com.example.bishe.mapper;

import com.example.bishe.entity.TeacherEvaluation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 教师评分记录表数据访问接口。
 */
@Mapper
public interface TeacherEvaluationMapper {

    @Insert("INSERT INTO teacher_evaluation(teacher_id, student_id, course_id, score) " +
            "VALUES(#{teacherId}, #{studentId}, #{courseId}, #{score})")
    int insert(TeacherEvaluation evaluation);

    @Update("UPDATE teacher_evaluation SET score = #{score}, course_id = #{courseId} " +
            "WHERE evaluation_id = #{evaluationId}")
    int updateScore(TeacherEvaluation evaluation);

    @Select("SELECT * FROM teacher_evaluation WHERE teacher_id = #{teacherId} AND student_id = #{studentId} LIMIT 1")
    TeacherEvaluation findByTeacherAndStudent(@Param("teacherId") Long teacherId,
                                              @Param("studentId") Long studentId);

    @Select("SELECT * FROM teacher_evaluation WHERE teacher_id = #{teacherId} ORDER BY evaluation_id DESC")
    List<TeacherEvaluation> findByTeacherId(Long teacherId);

    @Select("SELECT COUNT(*) FROM teacher_evaluation WHERE teacher_id = #{teacherId}")
    int countByTeacherId(Long teacherId);

    @Select("<script>SELECT teacher_id AS teacherId, COUNT(*) AS ratingCount FROM teacher_evaluation "
            + "WHERE teacher_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY teacher_id</script>")
    List<TeacherRatingCount> countGroupedByTeacherIds(@Param("ids") List<Long> ids);

    @Select("SELECT * FROM teacher_evaluation WHERE student_id = #{studentId}")
    List<TeacherEvaluation> findByStudentId(Long studentId);

    @Select("SELECT AVG(score) FROM teacher_evaluation WHERE teacher_id = #{teacherId}")
    BigDecimal avgScoreByTeacherId(Long teacherId);
}
