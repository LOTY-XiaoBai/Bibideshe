package com.example.bishe.mapper;

import com.example.bishe.entity.OrderEvaluation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单评价表数据访问接口。
 */
@Mapper
public interface OrderEvaluationMapper {

    @Insert("INSERT INTO order_evaluation(order_id, buyer_id, rating, feedback_content) " +
            "VALUES(#{orderId}, #{buyerId}, #{rating}, #{feedbackContent})")
    @Options(useGeneratedKeys = true, keyProperty = "evaluationId")
    int insert(OrderEvaluation evaluation);

    @Select("SELECT * FROM order_evaluation WHERE evaluation_id = #{evaluationId}")
    OrderEvaluation findById(Long evaluationId);

    @Select("SELECT * FROM order_evaluation WHERE order_id = #{orderId}")
    OrderEvaluation findByOrderId(Long orderId);

    @Select("SELECT * FROM order_evaluation ORDER BY evaluation_id DESC LIMIT #{limit} OFFSET #{offset}")
    List<OrderEvaluation> findAll(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM order_evaluation")
    int countAll();

    @Select("SELECT * FROM order_evaluation WHERE order_id IN " +
            "(SELECT order_id FROM order_record WHERE ad_id = #{adId}) ORDER BY evaluation_id DESC")
    List<OrderEvaluation> findByAdId(Long adId);

    @Select("SELECT COALESCE(AVG(rating), 0) FROM order_evaluation WHERE order_id IN " +
            "(SELECT order_id FROM order_record WHERE ad_id = #{adId})")
    BigDecimal avgRatingByAdId(Long adId);
}
