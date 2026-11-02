package com.example.bishe.mapper;

import com.example.bishe.dto.CheckInDetailVO;
import com.example.bishe.dto.CheckInStudentRecordVO;
import com.example.bishe.entity.CheckInUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 学生签到明细表数据访问接口 (表24 check_in_user)。
 */
@Mapper
public interface CheckInUserMapper {

    /** 批量插入签到学生明细(发布签到时为全班学生创建缺勤记录) */
    int batchInsert(@Param("list") List<CheckInUser> list);

    /** 学生签到: 更新签到状态与签到时间 */
    int checkIn(@Param("checkInId") Long checkInId,
                @Param("studentId") Long studentId,
                @Param("status") int status,
                @Param("checkInTime") java.time.LocalDateTime checkInTime);

    /** 查询某学生在某次签到中的记录 */
    CheckInUser findByCheckInIdAndStudentId(@Param("checkInId") Long checkInId,
                                            @Param("studentId") Long studentId);

    /** 教师修改学生签到状态 */
    int updateStatus(@Param("checkInId") Long checkInId,
                     @Param("studentId") Long studentId,
                     @Param("status") int status,
                     @Param("checkInTime") java.time.LocalDateTime checkInTime);

    /** 查询某次签到的全部学生签到详情(含学生昵称) */
    List<CheckInDetailVO> findDetailsByCheckInId(Long checkInId);

    /** 查询某学生的全部签到记录(含课程名, 分页) */
    List<CheckInStudentRecordVO> findRecordsByStudentId(@Param("studentId") Long studentId,
                                                        @Param("offset") int offset,
                                                        @Param("limit") int limit);

    /** 统计某学生的签到记录总数 */
    int countByStudentId(Long studentId);

    /** 查询某学生某课程下的漏签记录(缺勤的签到) */
    List<CheckInStudentRecordVO> findMissedByStudentIdAndCourseId(@Param("studentId") Long studentId,
                                                                   @Param("courseId") Long courseId);

    /** 查询某学生全部漏签记录(分页) */
    List<CheckInStudentRecordVO> findAllMissedByStudentId(@Param("studentId") Long studentId,
                                                          @Param("offset") int offset,
                                                          @Param("limit") int limit);

    /** 统计某学生的漏签总数 */
    int countMissedByStudentId(Long studentId);
}
