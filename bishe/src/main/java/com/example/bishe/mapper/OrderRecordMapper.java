package com.example.bishe.mapper;

import com.example.bishe.entity.OrderRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 订单表数据访问接口。
 */
@Mapper
public interface OrderRecordMapper {

    @Insert("INSERT INTO order_record(order_id, ad_id, buyer_id, is_received, logistics_info, status, after_sale_status) " +
            "VALUES(#{orderId}, #{adId}, #{buyerId}, 0, NULL, 0, 0)")
    int insert(OrderRecord order);

    @Select("SELECT * FROM order_record WHERE order_id = #{orderId}")
    OrderRecord findById(Long orderId);

    @Select("SELECT * FROM order_record WHERE buyer_id = #{buyerId} ORDER BY order_id DESC")
    List<OrderRecord> findByBuyerId(Long buyerId);

    @Select("SELECT * FROM order_record ORDER BY order_id DESC LIMIT #{limit} OFFSET #{offset}")
    List<OrderRecord> findAll(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM order_record")
    int countAll();

    /** 待发货订单数量(管理员提醒用) */
    @Select("SELECT COUNT(*) FROM order_record WHERE status = 0")
    int countPending();

    @Update("UPDATE order_record SET logistics_info = #{logisticsInfo}, status = 1 WHERE order_id = #{orderId}")
    int updateLogistics(@Param("orderId") Long orderId, @Param("logisticsInfo") String logisticsInfo);

    @Update("UPDATE order_record SET is_received = 1, status = 2 WHERE order_id = #{orderId}")
    int confirmReceived(Long orderId);

    @Update("UPDATE order_record SET after_sale_content = #{content}, after_sale_status = 1, " +
            "after_sale_time = NOW() WHERE order_id = #{orderId}")
    int submitAfterSale(@Param("orderId") Long orderId, @Param("content") String content);

    @Update("UPDATE order_record SET after_sale_status = 2, after_sale_reply = #{reply} WHERE order_id = #{orderId}")
    int replyAfterSale(@Param("orderId") Long orderId, @Param("reply") String reply);
}
