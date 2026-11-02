package com.example.bishe.service;

import com.example.bishe.common.*;
import com.example.bishe.dto.ChatSendRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class CommunityService {
    private final JdbcTemplate db;
    private final ChatService chat;
    public CommunityService(JdbcTemplate db, ChatService chat) { this.db=db; this.chat=chat; }
    public static String owner() { AuthUser u=AuthContext.requireLogin(); return u.getRole()+":"+u.getSubject(); }
    private boolean admin() { return UserRole.ADMIN.equals(AuthContext.requireLogin().getRole()); }
    private String author() {
        AuthUser u=AuthContext.requireLogin();
        if (admin()) return "管理员 " + u.getSubject();
        String sql=UserRole.STUDENT.equals(u.getRole()) ? "SELECT nickname FROM student WHERE student_id=?" : "SELECT name FROM teacher WHERE teacher_id=?";
        return db.queryForObject(sql,String.class,u.getUserId());
    }
    public String text(Map<String,Object> b,String k,int max) {
        Object raw=b.get(k); String s=raw==null?"":raw.toString().trim();
        if(s.length()>max) throw new BusinessException(k+" 内容过长"); return s;
    }
    public String blocks(Map<String,Object> b) {
        String s=text(b,"blocks",50000);
        if(s.isBlank()) throw new BusinessException("请填写图文内容");
        // JSON 必须由服务端解析验证；仅支持纯文字与站内图片，不接受 HTML。
        try {
            var list=new tools.jackson.databind.json.JsonMapper().readValue(s,List.class);
            if(list.isEmpty() || list.size()>60) throw new IllegalArgumentException();
            for(Object item:list) {
                if(!(item instanceof Map m)) throw new IllegalArgumentException();
                String type=String.valueOf(m.get("type")), value=String.valueOf(m.get("value"));
                if(!Set.of("text","image").contains(type) || value.isBlank() || value.equals("null")) throw new IllegalArgumentException();
                if(type.equals("image") && !value.matches("/uploads/image/[a-zA-Z0-9._-]+")) throw new IllegalArgumentException();
            }
        } catch(Exception e) { throw new BusinessException("图文格式错误，只允许文字和本站上传图片"); }
        return s;
    }
    private Map<String,Object> post(long id,boolean lock) {
        var rows=db.queryForList("SELECT * FROM community_post WHERE id=?"+(lock?" FOR UPDATE":""),id);
        if(rows.isEmpty()) throw new BusinessException(404,"帖子不存在"); return rows.get(0);
    }
    private void accessible(Map<String,Object> p) {
        if(!"PUBLIC".equals(p.get("status")) && !admin() && !owner().equals(p.get("owner"))) throw new BusinessException(404,"帖子已下架");
    }
    private void writable() {
        db.update("INSERT IGNORE INTO community_user(owner) VALUES (?)",owner());
        var row=db.queryForMap("SELECT *, muted_until > NOW() AS muted FROM community_user WHERE owner=? FOR UPDATE",owner());
        if(((Number)row.get("muted")==null?0:((Number)row.get("muted")).intValue())==1) throw new BusinessException(403,"社区已禁言至 "+row.get("muted_until"));
    }
    private List<String> words() { return db.queryForList("SELECT word FROM community_word ORDER BY CHAR_LENGTH(word) DESC",String.class); }
    private String mask(String s) { for(String w:words()) s=s.replace(w,"*".repeat(w.length())); return s; }
    private void notice(String target,long postId,String kind,String message) {
        db.update("INSERT INTO community_notice(owner,post_id,kind,message) VALUES(?,?,?,?)",target,postId,kind,message);
    }
    public Map<String,Object> feed(String q,String sort,String scope,int page) {
        page=Math.max(1,Math.min(100000,page)); if(q.length()>120) throw new BusinessException("搜索词过长"); String where=" WHERE p.status='PUBLIC'";
        List<Object> args=new ArrayList<>();
        if(scope.equals("mine")){where=" WHERE p.owner=? AND p.status<>'DELETED'";args.add(owner());}
        if(scope.equals("admin")){AuthContext.requireAdmin();where=" WHERE 1=1";}
        if(sort.equals("featured")) where+=" AND p.featured=1";
        where+=" AND (p.title LIKE ? OR p.search_text LIKE ?)"; args.add("%"+q+"%");args.add("%"+q+"%");
        Long total=db.queryForObject("SELECT COUNT(*) FROM community_post p"+where,Long.class,args.toArray());
        String order=sort.equals("hot")?"views DESC,p.created_at DESC":"p.created_at DESC";
        args.add((page-1)*20);
        var list=db.queryForList("SELECT p.*, (SELECT COUNT(*) FROM community_view v WHERE v.post_id=p.id) views, (SELECT COUNT(*) FROM community_like l WHERE l.post_id=p.id) likes FROM community_post p"+where+" ORDER BY p.pinned DESC,"+order+" LIMIT 20 OFFSET ?",args.toArray());
        return Map.of("list",list,"total",total,"page",page,"owner",owner(),"moderation",moderation());
    }
    public Map<String,Object> moderation() {
        db.update("INSERT IGNORE INTO community_user(owner) VALUES(?)",owner());
        return db.queryForMap("SELECT warnings,muted_until FROM community_user WHERE owner=?",owner());
    }
    @Transactional
    public long save(Long id,Map<String,Object> b) {
        writable(); String title=text(b,"title",120), blocks=blocks(b);
        if(title.isBlank()) throw new BusinessException("标题不能为空");
        String plain=plain(blocks);
        if(!mask(title+plain).equals(title+plain)) throw new BusinessException("内有不良内容，无法发布");
        if(id==null) {
            db.update("INSERT INTO community_post(owner,author,title,blocks,search_text) VALUES(?,?,?,?,?)",owner(),author(),title,blocks,plain);
            return db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        }
        var p=post(id,true);
        if(!owner().equals(p.get("owner"))) throw new BusinessException(403,"只能编辑自己的帖子");
        if(Set.of("FORCED","DELETED").contains(p.get("status"))) throw new BusinessException(403,"管理员下架的帖子不能恢复或编辑");
        db.update("UPDATE community_post SET title=?,blocks=?,search_text=?,updated_at=NOW(),status='PUBLIC' WHERE id=?",title,blocks,plain,id);
        return id;
    }
    private String plain(String blocks) {
        StringBuilder s=new StringBuilder();
        try { for(Object x:new tools.jackson.databind.json.JsonMapper().readValue(blocks,List.class)) { Map m=(Map)x; if("text".equals(m.get("type"))) s.append(m.get("value")).append('\n'); } }
        catch(Exception e){throw new BusinessException("图文格式错误");} return s.toString();
    }
    @Transactional
    public Map<String,Object> detail(long id) {
        var p=post(id,false); accessible(p);
        if("PUBLIC".equals(p.get("status"))) db.update("INSERT IGNORE INTO community_view(post_id,owner,view_day) VALUES(?,?,CURRENT_DATE)",id,owner());
        p.put("replies",db.queryForList("SELECT * FROM community_reply WHERE post_id=? AND deleted=0 ORDER BY id LIMIT 500",id));
        p.put("likes",db.queryForObject("SELECT COUNT(*) FROM community_like WHERE post_id=?",Long.class,id));
        p.put("views",db.queryForObject("SELECT COUNT(*) FROM community_view WHERE post_id=?",Long.class,id));
        p.put("liked",db.queryForObject("SELECT COUNT(*) FROM community_like WHERE post_id=? AND owner=?",Long.class,id,owner())>0);
        p.put("viewer",owner()); return p;
    }
    @Transactional
    public void action(long id,String action) {
        var p=post(id,true); boolean own=owner().equals(p.get("owner"));
        if(Set.of("hide","delete","restore","forceHide").contains(action)) {
            if(action.equals("forceHide")) AuthContext.requireAdmin();
            if(!own && !admin()) throw new BusinessException(403,"无权操作");
            if(Set.of("DELETED","FORCED").contains(p.get("status"))) throw new BusinessException("该状态不可恢复或修改");
            if(action.equals("restore")) {
                writable(); if(!mask(p.get("title")+plain(p.get("blocks").toString())).equals(p.get("title")+plain(p.get("blocks").toString()))) throw new BusinessException("内有不良内容，无法恢复");
            }
            String status=action.equals("delete")?"DELETED":action.equals("restore")?"PUBLIC":action.equals("forceHide") || !own?"FORCED":"HIDDEN";
            db.update("UPDATE community_post SET status=? WHERE id=?",status,id);
            if(admin() && Set.of("DELETED","FORCED").contains(status)) notice(p.get("owner").toString(),id,status,"管理员已"+(status.equals("DELETED")?"删除":"强制隐藏")+"你的帖子，无法恢复");
        } else if(Set.of("feature","pin","unfeature","unpin").contains(action)) {
            AuthContext.requireAdmin();
            if(!"PUBLIC".equals(p.get("status"))) throw new BusinessException("仅公开帖子可置顶或设精华");
            String column=action.contains("feature")?"featured":"pinned";
            db.update("UPDATE community_post SET "+column+"=? WHERE id=?",action.startsWith("un")?0:1,id);
            if(action.equals("feature")) notice(p.get("owner").toString(),id,"FEATURED","你的帖子被设为精华");
        } else if(action.equals("like")) {
            if(!"PUBLIC".equals(p.get("status"))) throw new BusinessException("帖子已下架");
            if(db.update("DELETE FROM community_like WHERE post_id=? AND owner=?",id,owner())==0){
                db.update("INSERT INTO community_like VALUES(?,?)",id,owner());
                if(!own) notice(p.get("owner").toString(),id,"LIKE",author()+"点赞了你的帖子");
            }
        } else throw new BusinessException("未知操作");
    }
    @Transactional
    public void reply(long id,Map<String,Object> b) {
        writable(); var p=post(id,true);
        if(!"PUBLIC".equals(p.get("status"))) throw new BusinessException("帖子已下架");
        String content=text(b,"content",2000); if(content.isBlank()) throw new BusinessException("回复不能为空");
        Object parent=b.get("parentId"); String target=p.get("owner").toString();
        if(parent!=null) {
            var rows=db.queryForList("SELECT owner FROM community_reply WHERE id=? AND post_id=? AND deleted=0",parent,id);
            if(rows.isEmpty()) throw new BusinessException("引用回复不存在"); target=rows.get(0).get("owner").toString();
        }
        String masked=mask(content);
        db.update("INSERT INTO community_reply(post_id,owner,author,content,parent_id) VALUES(?,?,?,?,?)",id,owner(),author(),masked,parent);
        if(!target.equals(owner())) notice(target,id,"REPLY",author()+"回复了你："+masked.substring(0,Math.min(180,masked.length())));
        if(!target.equals(p.get("owner")) && !owner().equals(p.get("owner"))) notice(p.get("owner").toString(),id,"REPLY","你的帖子有新的回复");
        if(!content.equals(masked)) {
            db.update("UPDATE community_user SET warnings=warnings+1 WHERE owner=?",owner());
            db.update("UPDATE community_user SET muted_until=DATE_ADD(NOW(),INTERVAL 24 HOUR) WHERE owner=? AND warnings>3",owner());
            notice(owner(),id,"WARNING","回复触发屏蔽词，已替换为 * 并记一次警告；累计超过3次禁言24小时");
        }
    }
    @Transactional
    public void deleteReply(long id) {
        var rows=db.queryForList("SELECT * FROM community_reply WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw new BusinessException(404,"回复不存在"); var r=rows.get(0); long pid=((Number)r.get("post_id")).longValue(); var p=post(pid,false);
        if(!admin() && !owner().equals(r.get("owner")) && !owner().equals(p.get("owner"))) throw new BusinessException(403,"无权删除回复");
        if(db.update("UPDATE community_reply SET deleted=1 WHERE id=? AND deleted=0",id)>0) notice(r.get("owner").toString(),pid,"REPLY_DELETED","你的回复已被删除");
    }
    public List<Map<String,Object>> notices(int page) {return db.queryForList("SELECT * FROM community_notice WHERE owner=? ORDER BY id DESC LIMIT 30 OFFSET ?",owner(),(Math.max(1,page)-1)*30);}
    public void readNotices(){db.update("UPDATE community_notice SET is_read=1 WHERE owner=?",owner());}
    public List<Map<String,Object>> wordList(){AuthContext.requireAdmin();return db.queryForList("SELECT * FROM community_word ORDER BY id DESC");}
    public void addWord(Map<String,Object> b){AuthContext.requireAdmin();String w=text(b,"word",80);if(w.isBlank())throw new BusinessException("监控词不能为空");db.update("INSERT IGNORE INTO community_word(word) VALUES(?)",w);}
    public void deleteWord(long id){AuthContext.requireAdmin();db.update("DELETE FROM community_word WHERE id=?",id);}
    public List<Map<String,Object>> muted(){AuthContext.requireAdmin();return db.queryForList("SELECT * FROM community_user WHERE warnings>0 ORDER BY muted_until DESC LIMIT 200");}
    public void unmute(String target){AuthContext.requireAdmin();db.update("UPDATE community_user SET muted_until=NULL,warnings=0 WHERE owner=?",target);}
    @Transactional
    public void share(long id,Map<String,Object> b){
        var p=post(id,false); if(!"PUBLIC".equals(p.get("status")))throw new BusinessException("帖子已下架");
        ChatSendRequest req=new ChatSendRequest(); req.setContent("[社区帖子] "+p.get("title")+"\n/community/post/"+id);req.setMessageType(0);
        try {if(b.get("groupId")!=null){req.setGroupId(Long.valueOf(b.get("groupId").toString()));chat.sendGroup(AuthContext.requireUserId(),req);}else{req.setReceiverId(Long.valueOf(b.get("receiverId").toString()));chat.sendPrivate(AuthContext.requireUserId(),req);}}
        catch(NumberFormatException|NullPointerException e){throw new BusinessException("请选择有效会话");}
    }
}
