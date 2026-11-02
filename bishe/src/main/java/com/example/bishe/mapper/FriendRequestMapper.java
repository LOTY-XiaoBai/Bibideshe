package com.example.bishe.mapper;

import com.example.bishe.entity.FriendRequest;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 好友申请表数据访问接口 (表5.1 friend_request)。
 */
@Mapper
public interface FriendRequestMapper {

    @Insert("INSERT INTO friend_request(requester_id, receiver_id, status) " +
            "VALUES(#{requesterId}, #{receiverId}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "requestId")
    int insert(FriendRequest request);

    @Select("SELECT * FROM friend_request WHERE request_id = #{requestId}")
    FriendRequest findById(Long requestId);

    @Select("SELECT * FROM friend_request WHERE receiver_id = #{receiverId} AND status = 0 ORDER BY apply_time DESC")
    List<FriendRequest> findPendingByReceiver(Long receiverId);

    @Select("SELECT * FROM friend_request WHERE requester_id = #{requesterId} ORDER BY apply_time DESC")
    List<FriendRequest> findByRequester(Long requesterId);

    @Select("SELECT * FROM friend_request WHERE requester_id = #{requesterId} AND receiver_id = #{receiverId} AND status = 0")
    FriendRequest findPending(@Param("requesterId") Long requesterId, @Param("receiverId") Long receiverId);

    @Update("UPDATE friend_request SET status = #{status} WHERE request_id = #{requestId}")
    int updateStatus(@Param("requestId") Long requestId, @Param("status") Integer status);
}
