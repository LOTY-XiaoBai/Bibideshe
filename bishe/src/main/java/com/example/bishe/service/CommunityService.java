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
    private final CommunitySafetyService safety;
    private final ModerationService moderationService;
    private final AdminOperationService operations;
    private final CommunityDisciplineService discipline;
    public CommunityService(JdbcTemplate db, ChatService chat, CommunitySafetyService safety, ModerationService moderationService, AdminOperationService operations, CommunityDisciplineService discipline) { this.db=db; this.chat=chat; this.safety=safety; this.moderationService=moderationService; this.operations=operations; this.discipline=discipline; }
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
        return blocks(b, false);
    }
    private String blocks(Map<String,Object> b, boolean allowEmpty) {
        String s=text(b,"blocks",50000);
        if(allowEmpty && s.isBlank()) s="[]";
        if(s.isBlank()) throw new BusinessException("请填写图文内容");
        // JSON 必须由服务端解析验证；仅支持纯文字与站内图片，不接受 HTML。
        try {
            var list=new tools.jackson.databind.json.JsonMapper().readValue(s,List.class);
            if((!allowEmpty && list.isEmpty()) || list.size()>60) throw new IllegalArgumentException();
            for(Object item:list) {
                if(!(item instanceof Map m)) throw new IllegalArgumentException();
                String type=String.valueOf(m.get("type")), value=String.valueOf(m.get("value"));
                if(!Set.of("text","image").contains(type) || value.isBlank() || value.equals("null")) throw new IllegalArgumentException();
                if(type.equals("image") && !value.matches("/uploads/image/[a-zA-Z0-9._-]+")) throw new IllegalArgumentException();
            }
        } catch(Exception e) { throw new BusinessException("图文格式错误，只允许文字和本站上传图片"); }
        return s;
    }
    private void enrichAuthors(List<Map<String,Object>> rows) {
        if(rows.isEmpty()) return;
        Set<String> owners=new HashSet<>(); for(var r:rows) owners.add(r.get("owner").toString());
        String placeholders=String.join(",",Collections.nCopies(owners.size(),"?"));
        String sql="SELECT CONCAT('STUDENT:',student_id) owner,nickname display_name,avatar FROM student WHERE CONCAT('STUDENT:',student_id) IN ("+placeholders+") UNION ALL SELECT CONCAT('TEACHER:',teacher_id) owner,name display_name,avatar FROM teacher WHERE CONCAT('TEACHER:',teacher_id) IN ("+placeholders+")";
        List<Object> args=new ArrayList<>(owners); args.addAll(owners);
        Map<String,Map<String,Object>> profiles=new HashMap<>();
        for(var p:db.queryForList(sql,args.toArray()))profiles.put(p.get("owner").toString(),p);
        for(var r:rows){String key=r.get("owner").toString();var p=profiles.get(key);r.put("author_id",key.substring(key.indexOf(':')+1));r.put("avatar",p==null?"":Objects.toString(p.get("avatar"),""));r.put("display_name",p==null?r.get("author"):p.get("display_name"));}
    }
    private Map<String,Object> post(long id,boolean lock) {
        var rows=db.queryForList("SELECT * FROM community_post WHERE id=?"+(lock?" FOR UPDATE":""),id);
        if(rows.isEmpty()) throw new BusinessException(404,"帖子不存在"); return rows.get(0);
    }
    private void accessible(Map<String,Object> p) {
        if(!"PUBLIC".equals(p.get("status")) && !admin() && !owner().equals(p.get("owner"))) throw new BusinessException(404,"帖子已下架");
    }
    private void writable() {
        discipline.decay();
        db.update("INSERT IGNORE INTO community_user(owner) VALUES (?)",owner());
        var row=db.queryForMap("SELECT *, muted_until > NOW() AS muted FROM community_user WHERE owner=? FOR UPDATE",owner());
        if(((Number)row.get("muted")==null?0:((Number)row.get("muted")).intValue())==1) throw new BusinessException(403,"社区已禁言至 "+row.get("muted_until"));
    }
    private List<String> words() { return db.queryForList("SELECT word FROM special_word WHERE enabled=1 ORDER BY CHAR_LENGTH(word) DESC",String.class); }
    private String mask(String s) { for(String w:words()) s=s.replace(w,"*".repeat(w.length())); return s; }
    private void notice(String target,long postId,String kind,String message) {
        db.update("INSERT INTO community_notice(owner,post_id,kind,message) VALUES(?,?,?,?)",target,postId,kind,message.substring(0,Math.min(message.length(),500)));
    }
    public Map<String,Object> feed(String q,String sort,String scope,int page) {
        return feed(q,sort,scope,page,(Math.max(1,Math.min(100000,page))-1)*20,20);
    }
    public Map<String,Object> feed(String q,String sort,String scope,int page,int offset,int size) {
        offset=Math.max(0,Math.min(2000000,offset)); size=Math.max(1,Math.min(50,size)); if(q.length()>120) throw new BusinessException("搜索词过长"); String where=" WHERE p.status='PUBLIC'";
        List<Object> args=new ArrayList<>();
        if(scope.equals("mine")){where=" WHERE p.owner=? AND p.status<>'DELETED'";args.add(owner());}
        if(scope.equals("admin")){AuthContext.requireAdmin();where=" WHERE 1=1";}
        if(!scope.equals("mine")&&!scope.equals("admin")){
            where+=" AND NOT EXISTS(SELECT 1 FROM community_block b WHERE b.owner=? AND ((b.kind='POST' AND b.target=CAST(p.id AS CHAR)) OR (b.kind='AUTHOR' AND b.target=p.owner)))";args.add(owner());
        }
        if(scope.equals("friends")){
            long userId=AuthContext.requireUserId();
            where+=" AND EXISTS(SELECT 1 FROM friend f WHERE (f.friend_a_id=? AND p.owner IN (CONCAT('STUDENT:',f.friend_b_id),CONCAT('TEACHER:',f.friend_b_id))) OR (f.friend_b_id=? AND p.owner IN (CONCAT('STUDENT:',f.friend_a_id),CONCAT('TEACHER:',f.friend_a_id))))";
            args.add(userId);args.add(userId);
        }
        if(sort.equals("featured")&&!Set.of("mine","friends","admin").contains(scope)) where+=" AND p.featured=1";
        where+=" AND (p.title LIKE ? OR p.search_text LIKE ?)"; args.add("%"+q+"%");args.add("%"+q+"%");
        Long total=db.queryForObject("SELECT COUNT(*) FROM community_post p"+where,Long.class,args.toArray());
        String order=sort.equals("hot")&&!Set.of("mine","friends","admin").contains(scope)?"views DESC,p.created_at DESC,p.id DESC":"p.created_at DESC,p.id DESC";
        args.add(size); args.add(offset);
        args.add(0,owner());
        var list=db.queryForList("SELECT p.*, (SELECT COUNT(*) FROM community_view v WHERE v.post_id=p.id) views, (SELECT COUNT(*) FROM community_like l WHERE l.post_id=p.id) likes, (SELECT COUNT(*) FROM community_reply r WHERE r.post_id=p.id AND r.deleted=0) reply_count, EXISTS(SELECT 1 FROM community_like l WHERE l.post_id=p.id AND l.owner=?) liked FROM community_post p"+where+" ORDER BY "+(Set.of("mine","friends","admin").contains(scope)?"":"p.pinned DESC,")+order+" LIMIT ? OFFSET ?",args.toArray());
        enrichAuthors(list);
        return Map.of("list",list,"total",total,"page",page,"owner",owner(),"moderation",moderation());
    }
    public Map<String,Object> moderation() {
        discipline.decay();
        db.update("INSERT IGNORE INTO community_user(owner) VALUES(?)",owner());
        return db.queryForMap("SELECT warnings,muted_until FROM community_user WHERE owner=?",owner());
    }
    @Transactional
    public long save(Long id,Map<String,Object> b) {
        AuthContext.requireUserId();
        writable(); String title=text(b,"title",120);
        if(title.isBlank()) throw new BusinessException("标题不能为空");
        String blocks=blocks(b,true);
        String original=title+plain(blocks);
        title=mask(title);blocks=maskBlocks(blocks);String plain=plain(blocks);
        if(id==null) {
            db.update("INSERT INTO community_post(owner,author,title,blocks,search_text) VALUES(?,?,?,?,?)",owner(),author(),title,blocks,plain);
            long postId=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
            if(!original.equals(title+plain))warnMasked(postId,"帖子");
            return postId;
        }
        var p=post(id,true);
        if(!owner().equals(p.get("owner"))) throw new BusinessException(403,"只能编辑自己的帖子");
        if(Set.of("FORCED","DELETED").contains(p.get("status"))) throw new BusinessException(403,"管理员下架的帖子不能恢复或编辑");
        db.update("UPDATE community_post SET title=?,blocks=?,search_text=?,updated_at=NOW(),status='PUBLIC' WHERE id=?",title,blocks,plain,id);
        if(!original.equals(title+plain))warnMasked(id,"帖子");
        return id;
    }
    private String maskBlocks(String blocks){
        try{
            var mapper=new tools.jackson.databind.json.JsonMapper();
            var list=mapper.readValue(blocks,List.class);
            for(Object item:list){Map m=(Map)item;if("text".equals(m.get("type")))m.put("value",mask(m.get("value").toString()));}
            return mapper.writeValueAsString(list);
        }catch(Exception e){throw new BusinessException("图文格式错误");}
    }
    private void warnMasked(long postId,String kind){
        discipline.decay();
        db.update("UPDATE community_user SET warning_decay_at=IF(warnings=0,NOW(),warning_decay_at),warnings=warnings+1 WHERE owner=?",owner());
        db.update("UPDATE community_user SET muted_until=DATE_ADD(NOW(),INTERVAL 24 HOUR) WHERE owner=? AND warnings>3",owner());
        notice(owner(),postId,"WARNING",kind+"触发共用屏蔽词，已替换为 * 并记一次警告；累计超过3次禁言24小时");
    }
    private String plain(String blocks) {
        StringBuilder s=new StringBuilder();
        try { for(Object x:new tools.jackson.databind.json.JsonMapper().readValue(blocks,List.class)) { Map m=(Map)x; if("text".equals(m.get("type"))) s.append(m.get("value")).append('\n'); } }
        catch(Exception e){throw new BusinessException("图文格式错误");} return s.toString();
    }
    @Transactional
    public Map<String,Object> detail(long id) {
        var p=post(id,false); accessible(p);
        if(!admin()&&safety.blocked(p))throw new BusinessException(403,"你已屏蔽此帖子或作者，可在动态的屏蔽管理中取消");
        if("PUBLIC".equals(p.get("status"))) db.update("INSERT IGNORE INTO community_view(post_id,owner,view_day) VALUES(?,?,CURRENT_DATE)",id,owner());
        var replies=db.queryForList("SELECT r.*, (SELECT COUNT(*) FROM community_reply_like l WHERE l.reply_id=r.id) likes, EXISTS(SELECT 1 FROM community_reply_like l WHERE l.reply_id=r.id AND l.owner=?) liked FROM community_reply r WHERE post_id=? ORDER BY id LIMIT 500",owner(),id);
        for(var r:replies)if(Boolean.TRUE.equals(r.get("deleted")) || "1".equals(String.valueOf(r.get("deleted"))))r.put("content","该回复已删除");
        enrichAuthors(replies); enrichAuthors(List.of(p));
        p.put("replies",replies);
        p.put("reply_count",db.queryForObject("SELECT COUNT(*) FROM community_reply WHERE post_id=? AND deleted=0",Long.class,id));
        p.put("likes",db.queryForObject("SELECT COUNT(*) FROM community_like WHERE post_id=?",Long.class,id));
        p.put("views",db.queryForObject("SELECT COUNT(*) FROM community_view WHERE post_id=?",Long.class,id));
        p.put("liked",db.queryForObject("SELECT COUNT(*) FROM community_like WHERE post_id=? AND owner=?",Long.class,id,owner())>0);
        p.put("friend_status",safety.friendStatus(p.get("owner").toString()));
        p.put("viewer",owner()); return p;
    }
    @Transactional
    public void action(long id,String action) {
        var p=post(id,true); boolean own=owner().equals(p.get("owner"));
        if(Set.of("hide","delete","restore","forceHide").contains(action)) {
            if(action.equals("forceHide")) AuthContext.requireAdmin();
            if(!own && !admin()) throw new BusinessException(403,"无权操作");
            if("DELETED".equals(p.get("status"))||("FORCED".equals(p.get("status"))&&!(admin()&&Set.of("delete","restore").contains(action)))) throw new BusinessException("该状态不可恢复或修改；管理员隐藏的帖子可提交申诉");
            if(action.equals("restore")) {
                writable(); if(!mask(p.get("title")+plain(p.get("blocks").toString())).equals(p.get("title")+plain(p.get("blocks").toString()))) throw new BusinessException("内有不良内容，无法恢复");
            }
            String status=action.equals("delete")?"DELETED":action.equals("restore")?"PUBLIC":action.equals("forceHide") || !own?"FORCED":"HIDDEN";
            db.update("UPDATE community_post SET status=? WHERE id=?",status,id);
            if(admin() && Set.of("DELETED","FORCED").contains(status)) notice(p.get("owner").toString(),id,status,"管理员已"+(status.equals("DELETED")?"删除":"隐藏")+"你的帖子，可在申诉页面提交说明");
        } else if(Set.of("feature","pin","unfeature","unpin").contains(action)) {
            AuthContext.requireAdmin();
            if(!"PUBLIC".equals(p.get("status"))) throw new BusinessException("仅公开帖子可置顶或设精华");
            String column=action.contains("feature")?"featured":"pinned";
            db.update("UPDATE community_post SET "+column+"=? WHERE id=?",action.startsWith("un")?0:1,id);
            if(action.equals("feature")) notice(p.get("owner").toString(),id,"FEATURED","你的帖子被设为精华");
        } else if(action.equals("like")) {
            AuthContext.requireUserId();
            if(!"PUBLIC".equals(p.get("status"))) throw new BusinessException("帖子已下架");
            if(db.update("DELETE FROM community_like WHERE post_id=? AND owner=?",id,owner())==0){
                db.update("INSERT INTO community_like VALUES(?,?)",id,owner());
                if(!own) notice(p.get("owner").toString(),id,"LIKE",author()+"点赞了你的帖子");
            }
        } else throw new BusinessException("未知操作");
        operations.record(switch(action){case "forceHide","hide"->"隐藏帖子";case "delete"->"删除帖子";case "restore"->"恢复公开";case "feature"->"设置精华";case "unfeature"->"取消精华";case "pin"->"置顶帖子";case "unpin"->"取消置顶";default->action;},"POST",id,p.get("owner").toString(),id,p.get("title").toString(),"原状态："+p.get("status"));
    }
    @Transactional
    public void reply(long id,Map<String,Object> b) {
        AuthContext.requireUserId();
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
        if(!content.equals(masked))warnMasked(id,"回复");
    }
    @Transactional
    public void deleteReply(long id) {
        var rows=db.queryForList("SELECT * FROM community_reply WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw new BusinessException(404,"回复不存在"); var r=rows.get(0); long pid=((Number)r.get("post_id")).longValue(); var p=post(pid,false);
        if(!admin() && !owner().equals(r.get("owner")) && !owner().equals(p.get("owner"))) throw new BusinessException(403,"无权删除回复");
        if(db.update("UPDATE community_reply SET deleted=1 WHERE id=? AND deleted=0",id)>0){notice(r.get("owner").toString(),pid,"REPLY_DELETED","你的回复已被删除");operations.record("删除回复","REPLY",id,r.get("owner").toString(),pid,p.get("title").toString(),r.get("content").toString());}
    }
    @Transactional
    public void likeReply(long id) {
        AuthContext.requireUserId();
        var rows=db.queryForList("SELECT * FROM community_reply WHERE id=? AND deleted=0 FOR UPDATE",id);
        if(rows.isEmpty())throw new BusinessException(404,"回复不存在");var r=rows.get(0);long pid=((Number)r.get("post_id")).longValue();
        if(!"PUBLIC".equals(post(pid,false).get("status")))throw new BusinessException("帖子已下架");
        if(db.update("DELETE FROM community_reply_like WHERE reply_id=? AND owner=?",id,owner())==0){db.update("INSERT INTO community_reply_like(reply_id,owner) VALUES(?,?)",id,owner());if(!owner().equals(r.get("owner")))notice(r.get("owner").toString(),pid,"REPLY_LIKE",author()+"点赞了你的回复");}
    }
    public Map<String,Object> noticePage(int page) {
        int offset=(Math.max(1,Math.min(100000,page))-1)*30;
        var rows=db.queryForList("SELECT * FROM community_notice WHERE owner=? ORDER BY id DESC LIMIT 31 OFFSET ?",owner(),offset);
        return Map.of("list",rows.subList(0,Math.min(30,rows.size())),"hasMore",rows.size()>30);
    }
    public List<Map<String,Object>> notices(int page) {return db.queryForList("SELECT * FROM community_notice WHERE owner=? ORDER BY id DESC LIMIT 30 OFFSET ?",owner(),(Math.max(1,page)-1)*30);}
    public void readNotice(long id){db.update("UPDATE community_notice SET is_read=1 WHERE id=? AND owner=?",id,owner());}
    public void readNotices(){db.update("UPDATE community_notice SET is_read=1 WHERE owner=?",owner());}
    public List<Map<String,Object>> wordList(){AuthContext.requireAdmin();return db.queryForList("SELECT word_id id,word,enabled FROM special_word ORDER BY word_id DESC");}
    public void addWord(Map<String,Object> b){String adminId=AuthContext.requireAdminId();var request=new com.example.bishe.dto.SpecialWordRequest();request.setWord(text(b,"word",100));moderationService.addWord(request,adminId);}
    public void deleteWord(long id){AuthContext.requireAdmin();moderationService.deleteWord(id);}
    public List<Map<String,Object>> muted(){AuthContext.requireAdmin();discipline.decay();return db.queryForList("SELECT * FROM community_user WHERE warnings>0 OR muted_until>NOW() ORDER BY muted_until DESC,owner LIMIT 200");}
    public Map<String,Object> mutedPage(String role,int page,int size){
        AuthContext.requireAdmin();discipline.decay();size=PageSize.checked(size);
        if(!Set.of("ALL","STUDENT","TEACHER").contains(role))throw new BusinessException("用户类型无效");
        String where=" WHERE (warnings>0 OR muted_until>NOW()) AND owner LIKE ?";
        String filter=role.equals("ALL")?"%":role+":%";
        return Map.of("list",db.queryForList("SELECT * FROM community_user"+where+" ORDER BY muted_until DESC,owner LIMIT ? OFFSET ?",filter,size,PageSize.offset(page,size)),"total",db.queryForObject("SELECT COUNT(*) FROM community_user"+where,Long.class,filter));
    }
    @Transactional
    public void unmute(String target){AuthContext.requireAdmin();if(db.update("UPDATE community_user SET muted_until=NULL WHERE owner=?",target)==0)throw new BusinessException("用户不存在警告或禁言记录");operations.record("解除社区禁言","USER",target,target,null,"","解除社区禁言，保留警告次数");}
    @Transactional
    public void clearWarnings(String target){AuthContext.requireAdmin();if(db.update("UPDATE community_user SET warnings=0,warning_decay_at=NOW() WHERE owner=?",target)==0)throw new BusinessException("用户不存在记录");operations.record("清零社区警告","USER",target,target,null,"","保留禁言状态");}
    @Transactional
    public void moderateUser(Map<String,Object> b){
        AuthContext.requireAdmin();discipline.decay();String target=text(b,"owner",90),action=text(b,"action",20),reason=text(b,"reason",500);
        if(target.matches("ALL:[0-9]+")){
            String rawId=target.substring(4);
            long students=db.queryForObject("SELECT COUNT(*) FROM student WHERE student_id=?",Long.class,rawId),teachers=db.queryForObject("SELECT COUNT(*) FROM teacher WHERE teacher_id=?",Long.class,rawId);
            if(students+teachers!=1)throw new BusinessException("用户不存在或ID重复，请选择学生或教师");target=(students==1?"STUDENT:":"TEACHER:")+rawId;
        }
        if(!target.matches("(STUDENT|TEACHER):[0-9]+")||!Set.of("WARN","MUTE").contains(action)||reason.isBlank())throw new BusinessException("请选择有效用户、操作并填写原因");
        String[] parts=target.split(":");long userId;
        try{userId=Long.parseLong(parts[1]);}catch(NumberFormatException e){throw new BusinessException("账号ID无效");}
        String sql=parts[0].equals("STUDENT")?"SELECT COUNT(*) FROM student WHERE student_id=?":"SELECT COUNT(*) FROM teacher WHERE teacher_id=?";
        if(db.queryForObject(sql,Long.class,userId)==0)throw new BusinessException("用户不存在");
        Long postId=null;String title="";
        if(b.get("postId")!=null){try{postId=Long.valueOf(b.get("postId").toString());}catch(NumberFormatException e){throw new BusinessException("帖子ID无效");}var p=post(postId,false);if(!target.equals(p.get("owner")))throw new BusinessException("帖子作者与处理用户不一致");title=p.get("title").toString();}
        db.update("INSERT IGNORE INTO community_user(owner) VALUES(?)",target);
        if(action.equals("WARN")){db.update("UPDATE community_user SET warning_decay_at=IF(warnings=0,NOW(),warning_decay_at),warnings=warnings+1 WHERE owner=?",target);db.update("UPDATE community_user SET muted_until=DATE_ADD(NOW(),INTERVAL 24 HOUR) WHERE owner=? AND warnings>3",target);}
        else discipline.mute(target,b.getOrDefault("muteMinutes",1440).toString(),reason,postId,title);
        notice(target,postId==null?0:postId,"WARNING","管理员"+(action.equals("WARN")?"警告":"禁言")+"："+reason);
        if(action.equals("WARN"))operations.record("警告用户","USER",target,target,postId,title,reason);
    }
    @Transactional
    public void share(long id,Map<String,Object> b){
        var p=post(id,false); if(!"PUBLIC".equals(p.get("status")))throw new BusinessException("帖子已下架");
        ChatSendRequest req=new ChatSendRequest(); req.setContent("[社区帖子] "+p.get("title")+"\n/community/post/"+id);req.setMessageType(0);
        try {if(b.get("groupId")!=null){req.setGroupId(Long.valueOf(b.get("groupId").toString()));chat.sendGroup(AuthContext.requireUserId(),req);}else{req.setReceiverId(Long.valueOf(b.get("receiverId").toString()));chat.sendPrivate(AuthContext.requireUserId(),req);}}
        catch(NumberFormatException|NullPointerException e){throw new BusinessException("请选择有效会话");}
        db.update("UPDATE community_post SET share_count=share_count+1 WHERE id=?",id);
    }
}
