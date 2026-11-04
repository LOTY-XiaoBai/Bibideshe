package com.example.bishe.service;
import com.example.bishe.common.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class UnreadService {
    private final JdbcTemplate db;
    public UnreadService(JdbcTemplate db){this.db=db;}
    public Map<String,Object> summary(){
        String owner=CommunityService.owner();Map<String,Object> result=new HashMap<>();
        result.put("notices",count("SELECT COUNT(*) FROM community_notice WHERE owner=? AND is_read=0",owner));
        if(UserRole.ADMIN.equals(AuthContext.requireLogin().getRole())){
            result.put("reports",count("SELECT COUNT(*) FROM community_report WHERE status='PENDING'"));
            result.put("appeals",count("SELECT COUNT(*) FROM community_appeal WHERE status='PENDING'"));
            result.put("violations",count("SELECT COUNT(*) FROM chat_violation WHERE status=0"));
            long questions=0;for(String table:List.of("question_choice","question_judge","question_answer"))questions+=count("SELECT COUNT(*) FROM "+table+" WHERE audit_status=0 AND is_public=1");
            result.put("questions",questions);return result;
        }
        long id=AuthContext.requireUserId();
        if(UserRole.TEACHER.equals(AuthContext.requireLogin().getRole())) result.put("courseApplications",count("SELECT COUNT(*) FROM course_application a JOIN course c ON c.course_id=a.course_id WHERE c.teacher_id=? AND a.status='PENDING'",id));
        result.put("private",db.queryForList("SELECT c.sender_id target_id,COUNT(*) unread FROM chat_record c LEFT JOIN chat_read_cursor r ON r.owner=? AND r.kind='private' AND r.target_id=c.sender_id WHERE c.receiver_id=? AND c.is_private=1 AND c.is_deleted=0 AND c.message_id>COALESCE(r.last_message_id,0) AND (c.sender_id=0 OR EXISTS(SELECT 1 FROM friend f WHERE (f.friend_a_id=? AND f.friend_b_id=c.sender_id) OR (f.friend_b_id=? AND f.friend_a_id=c.sender_id))) GROUP BY c.sender_id",owner,id,id,id));
        result.put("groups",db.queryForList("SELECT c.group_id target_id,COUNT(*) unread FROM chat_record c JOIN group_user gu ON gu.group_id=c.group_id AND gu.user_id=? JOIN group_chat g ON g.group_id=c.group_id LEFT JOIN chat_read_cursor r ON r.owner=? AND r.kind='group' AND r.target_id=c.group_id WHERE c.is_private=0 AND c.is_deleted=0 AND c.sender_id<>? AND g.is_dissolved=0 AND g.is_deleted=0 AND g.is_banned=0 AND c.message_id>COALESCE(r.last_message_id,0) GROUP BY c.group_id",id,owner,id));
        result.put("requests",count("SELECT COUNT(*) FROM friend_request WHERE receiver_id=? AND status=0",id));
        return result;
    }
    private long count(String sql,Object...args){return db.queryForObject(sql,Long.class,args);}
    public void read(String kind,long target,long last){
        long id=AuthContext.requireUserId();if(kind==null||!Set.of("private","group").contains(kind)||target<0||last<0)throw new BusinessException("会话参数无效");
        if(kind.equals("group")&&count("SELECT COUNT(*) FROM group_user WHERE group_id=? AND user_id=?",target,id)==0)throw new BusinessException(403,"不是群成员");
        String sql=kind.equals("private")?"SELECT COALESCE(MAX(message_id),0) FROM chat_record WHERE is_private=1 AND sender_id=? AND receiver_id=? AND message_id<=?":"SELECT COALESCE(MAX(message_id),0) FROM chat_record WHERE is_private=0 AND group_id=? AND sender_id<>? AND message_id<=?";
        long bounded=count(sql,target,id,last);
        db.update("INSERT INTO chat_read_cursor(owner,kind,target_id,last_message_id) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE last_message_id=GREATEST(last_message_id,VALUES(last_message_id))",CommunityService.owner(),kind,target,bounded);
    }
}
