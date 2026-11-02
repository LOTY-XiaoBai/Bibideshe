package com.example.bishe.mapper;

import com.example.bishe.entity.ErrorBook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 错题本表数据访问接口 (表25 error_book)。
 */
@Mapper
public interface ErrorBookMapper {

    /** 新增一条错题记录 */
    int insert(ErrorBook errorBook);

    /** 查询某学生是否已经记录过某道错题 */
    ErrorBook find(@Param("studentId") Long studentId,
                    @Param("questionId") Long questionId,
                    @Param("questionType") Integer questionType);

    /** 查询某学生的全部错题 */
    List<ErrorBook> findByStudentId(Long studentId);

    /** 从错题本移除一条记录 */
    int delete(@Param("studentId") Long studentId,
               @Param("questionId") Long questionId,
               @Param("questionType") Integer questionType);

    /** 按学生删除全部错题(注销账号使用) */
    int deleteByStudentId(Long studentId);
}
