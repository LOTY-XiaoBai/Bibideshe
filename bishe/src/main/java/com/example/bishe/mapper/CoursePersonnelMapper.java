package com.example.bishe.mapper;

import com.example.bishe.dto.CourseMemberVO;
import com.example.bishe.dto.StudentScoreVO;
import com.example.bishe.entity.CoursePersonnel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 课程人员(选课)表数据访问接口 (表22 course_personnel)。
 */
@Mapper
public interface CoursePersonnelMapper {

    /** 新增一条选课记录 */
    int insert(CoursePersonnel coursePersonnel);

    /** 查询某学生在某课程的选课记录, 用于判断是否已选 */
    CoursePersonnel find(@Param("courseId") Long courseId, @Param("studentId") Long studentId);

    /** 查询某课程的全部选课记录(班级成员) */
    List<CoursePersonnel> findByCourseId(Long courseId);

    /** 查询某课程班级成员(含昵称) */
    List<CourseMemberVO> findMembersByCourseId(Long courseId);

    /** 查询某学生已选的全部课程记录 */
    List<CoursePersonnel> findByStudentId(Long studentId);

    /** 删除某学生在某课程的选课记录(退课 / 教师移除) */
    int delete(@Param("courseId") Long courseId, @Param("studentId") Long studentId);

    /** 删除某课程的全部选课记录 */
    int deleteByCourseId(Long courseId);

    /** 删除某学生的全部选课记录 */
    int deleteByStudentId(Long studentId);

    /** 更新某学生在某课程的成绩 */
    int updateStudentScore(@Param("courseId") Long courseId,
                           @Param("studentId") Long studentId,
                           @Param("studentScore") BigDecimal studentScore);

    /** 查询某学生所有已结课课程的成绩列表(用于计算平均分) */
    List<CoursePersonnel> findFinishedCourseScoresByStudentId(Long studentId);

    /** 查询某学生总分: 所有已结课课程 student_score 的平均分 */
    StudentScoreVO getStudentTotalScore(Long studentId);

    // ==================== 学生课程分数聚合查询(供 score 重算使用) ====================

    /** 汇总某学生在某课程的全部考试得分 */
    BigDecimal getExamScoreSumByCourseAndStudent(@Param("courseId") Long courseId,
                                                  @Param("studentId") Long studentId);

    /** 汇总某课程全部考试的满分总分(所有考试的各题分值之和) */
    BigDecimal getExamMaxScoreSumByCourse(Long courseId);

    /** 汇总某学生在某课程的全部作业得分 */
    BigDecimal getHomeworkScoreSumByCourseAndStudent(@Param("courseId") Long courseId,
                                                      @Param("studentId") Long studentId);

    /** 汇总某课程全部作业的满分总分 */
    BigDecimal getHomeworkMaxScoreSumByCourse(Long courseId);

    /**
     * 查询某学生在某课程的签到统计:
     * totalScore(课程签到满分合计), earnedScore(学生已得签到分),
     * total(总签到次数), attended(已签到+迟到次数)。
     */
    Map<String, Object> getAttendanceStatsByCourseAndStudent(@Param("courseId") Long courseId,
                                                              @Param("studentId") Long studentId);
}
