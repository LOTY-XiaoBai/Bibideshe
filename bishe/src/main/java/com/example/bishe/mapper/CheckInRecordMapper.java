package com.example.bishe.mapper;

import com.example.bishe.entity.CheckInRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 签到发布记录表数据访问接口 (表23 check_in_record)。
 */
@Mapper
public interface CheckInRecordMapper {

    /** 新增签到记录, 自增主键回填 */
    int insert(CheckInRecord record);

    /** 按签到ID查询 */
    CheckInRecord findById(Long checkInId);

    /** 查询某课程的全部签到记录(按发布时间倒序) */
    List<CheckInRecord> findByCourseId(@Param("courseId") Long courseId,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    /** 统计某课程的签到记录总数 */
    int countByCourseId(Long courseId);

    /** 更新签到状态(结束签到)及实际结束时间 */
    int endCheckIn(@Param("checkInId") Long checkInId,
                   @Param("endTime") java.time.LocalDateTime endTime);

    /** 更新签到人数 */
    int updateAttendeeCount(@Param("checkInId") Long checkInId,
                            @Param("count") int count);
}
