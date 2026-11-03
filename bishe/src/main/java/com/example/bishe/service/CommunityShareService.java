package com.example.bishe.service;

import com.example.bishe.common.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class CommunityShareService {
    private final JdbcTemplate db;
    private final CommunityService community;
    public CommunityShareService(JdbcTemplate db,CommunityService community){this.db=db;this.community=community;}
    public List<Map<String,Object>> targets(){
        long user=AuthContext.requireUserId();
        var rows=db.queryForList("SELECT 'private' kind,CASE WHEN f.friend_a_id=? THEN f.friend_b_id ELSE f.friend_a_id END id,COALESCE(s.nickname,t.name,'好友') name,COALESCE(s.avatar,t.avatar,'') avatar,f.created_at added_at FROM friend f LEFT JOIN student s ON s.student_id=CASE WHEN f.friend_a_id=? THEN f.friend_b_id ELSE f.friend_a_id END LEFT JOIN teacher t ON t.teacher_id=CASE WHEN f.friend_a_id=? THEN f.friend_b_id ELSE f.friend_a_id END WHERE (f.friend_a_id=? OR f.friend_b_id=?) AND (s.student_id IS NOT NULL OR t.teacher_id IS NOT NULL) AND COALESCE(s.is_deleted,t.is_deleted,0)=0 AND COALESCE(s.is_banned,t.is_banned,0)=0",user,user,user,user,user);
        rows.addAll(db.queryForList("SELECT 'group' kind,g.group_id id,g.group_name name,COALESCE(g.group_avatar,'') avatar,NULL added_at FROM group_chat g JOIN group_user u ON u.group_id=g.group_id AND u.user_id=? WHERE g.is_dissolved=0 AND IFNULL(g.is_deleted,0)=0 AND IFNULL(g.is_banned,0)=0",user));
        Map<String,Object> recent=new HashMap<>();
        for(var r:db.queryForList("SELECT CASE WHEN sender_id=? THEN receiver_id ELSE sender_id END id,MAX(send_time) last_chat FROM chat_record WHERE is_private=1 AND is_deleted=0 AND (sender_id=? OR receiver_id=?) GROUP BY CASE WHEN sender_id=? THEN receiver_id ELSE sender_id END",user,user,user,user))recent.put("private:"+r.get("id"),r.get("last_chat"));
        for(var r:db.queryForList("SELECT c.group_id id,MAX(c.send_time) last_chat FROM chat_record c JOIN group_user u ON u.group_id=c.group_id AND u.user_id=? WHERE c.is_private=0 AND c.is_deleted=0 GROUP BY c.group_id",user))recent.put("group:"+r.get("id"),r.get("last_chat"));
        for(var r:rows){String key=r.get("kind")+":"+r.get("id");r.put("key",key);r.put("last_chat",recent.get(key));}
        rows.sort(Comparator.<Map<String,Object>>comparingInt(r->r.get("last_chat")!=null?0:"private".equals(r.get("kind"))?1:2)
            .thenComparing(r->Objects.toString(r.get("last_chat"),""),Comparator.reverseOrder())
            .thenComparing(r->Objects.toString(r.get("added_at"),""),Comparator.reverseOrder())
            .thenComparing(r->r.get("key").toString()));
        return rows;
    }
    @Transactional
    public int share(long postId,Map<String,Object> body){
        AuthContext.requireUserId();Object raw=body.get("targets");
        if(!(raw instanceof List<?> selected)||selected.isEmpty()||selected.size()>20)throw new BusinessException("请选择1至20个好友或群聊");
        Set<String> allowed=new HashSet<>();for(var t:targets())allowed.add(t.get("key").toString());
        Set<String> unique=new LinkedHashSet<>();
        for(Object item:selected){if(!(item instanceof String key)||!allowed.contains(key))throw new BusinessException("转发对象无效，可能已不是好友或已退出群聊");unique.add(key);}
        // 全部目标先校验，再在同一事务内写入，避免无效目标导致部分发送。
        for(String key:unique){String[] parts=key.split(":",2);community.share(postId,Map.of(parts[0].equals("group")?"groupId":"receiverId",parts[1]));}
        return unique.size();
    }
}
