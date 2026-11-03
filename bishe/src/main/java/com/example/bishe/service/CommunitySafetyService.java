package com.example.bishe.service;

import com.example.bishe.common.*;
import com.example.bishe.mapper.FriendRequestMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class CommunitySafetyService {
    private final JdbcTemplate db;
    private final FriendService friends;
    private final FriendRequestMapper friendRequests;
    private final AdminOperationService operations;
    private final CommunityDisciplineService discipline;
    public CommunitySafetyService(JdbcTemplate db, FriendService friends, FriendRequestMapper friendRequests,AdminOperationService operations,CommunityDisciplineService discipline){
        this.db=db; this.friends=friends; this.friendRequests=friendRequests;this.operations=operations;this.discipline=discipline;
    }
    private String owner(){return CommunityService.owner();}
    private Map<String,Object> publicOther(long id){
        var rows=db.queryForList("SELECT * FROM community_post WHERE id=? AND status='PUBLIC'",id);
        if(rows.isEmpty())throw new BusinessException(404,"帖子不存在或已下架");
        var p=rows.get(0);if(owner().equals(p.get("owner")))throw new BusinessException(400,"不能对自己的帖子执行此操作");return p;
    }
    public boolean blocked(Map<String,Object> post){return db.queryForObject("SELECT COUNT(*) FROM community_block WHERE owner=? AND ((kind='POST' AND target=?) OR (kind='AUTHOR' AND target=?))",Long.class,owner(),post.get("id").toString(),post.get("owner"))>0;}
    /** 帖子上的关注入口复用既有好友申请，不建立独立的单向关注。 */
    public String friendStatus(String author){
        var viewer=AuthContext.requireLogin();
        if(author.equals(owner()))return "SELF";
        if(!Set.of(UserRole.STUDENT,UserRole.TEACHER).contains(viewer.getRole())||viewer.getUserId()==null)return "UNAVAILABLE";
        String[] parts=author.split(":",2);
        if(parts.length!=2||!Set.of(UserRole.STUDENT,UserRole.TEACHER).contains(parts[0]))return "UNAVAILABLE";
        long target;
        try{target=Long.parseLong(parts[1]);}catch(NumberFormatException e){return "UNAVAILABLE";}
        if(viewer.getUserId().equals(target))return "SELF";
        if(friends.areFriends(viewer.getUserId(),target))return "FRIEND";
        if(friendRequests.findPending(viewer.getUserId(),target)!=null)return "REQUEST_SENT";
        if(friendRequests.findPending(target,viewer.getUserId())!=null)return "REQUEST_RECEIVED";
        return "NONE";
    }
    public void block(long id,String kind){
        var p=publicOther(id);if(!Set.of("POST","AUTHOR").contains(kind))throw new BusinessException("屏蔽类型无效");
        String target=kind.equals("POST")?Long.toString(id):p.get("owner").toString();
        db.update("INSERT IGNORE INTO community_block(owner,kind,target,label) VALUES(?,?,?,?)",owner(),kind,target,kind.equals("POST")?p.get("title"):p.get("author"));
    }
    public List<Map<String,Object>> blocks(){return db.queryForList("SELECT * FROM community_block WHERE owner=? ORDER BY id DESC",owner());}
    public void unblock(long id){if(db.update("DELETE FROM community_block WHERE id=? AND owner=?",id,owner())==0)throw new BusinessException(404,"屏蔽记录不存在");}
    public void report(long id,Map<String,String> body){
        var p=publicOther(id);String reason=body.getOrDefault("reason","").trim(),details=body.getOrDefault("details","").trim();
        if(!Set.of("违法违规","辱骂攻击","广告骚扰","虚假信息","侵犯隐私","其他").contains(reason))throw new BusinessException("请选择举报原因");
        if(details.length()>1000)throw new BusinessException("举报说明最多1000字");
        int added=db.update("INSERT IGNORE INTO community_report(reporter,post_id,post_owner,post_title,content_snapshot,reason,details) VALUES(?,?,?,?,?,?,?)",owner(),id,p.get("owner"),p.get("title"),p.get("search_text"),reason,details);
        if(added==0)throw new BusinessException(409,"你已经举报过该帖子，请等待管理员处理");
    }
    public Map<String,Object> reports(String status,int page){return reports(status,page,20);}
    public Map<String,Object> reports(String status,int page,int size){
        size=PageSize.checked(size);
        AuthContext.requireAdmin();if(!Set.of("PENDING","RESOLVED","DISMISSED","OVERTURNED","ALL").contains(status))throw new BusinessException("处理状态无效");
        String where=status.equals("ALL")?"":" WHERE status=?";List<Object> args=new ArrayList<>();if(!status.equals("ALL"))args.add(status);
        long total=db.queryForObject("SELECT COUNT(*) FROM community_report"+where,Long.class,args.toArray());args.add(size);args.add(PageSize.offset(page,size));
        return Map.of("list",db.queryForList("SELECT * FROM community_report"+where+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",args.toArray()),"total",total,"pending",db.queryForObject("SELECT COUNT(*) FROM community_report WHERE status='PENDING'",Long.class));
    }
    @Transactional
    public void resolve(long id,Map<String,String> body){
        AuthContext.requireAdmin();String decision=body.getOrDefault("decision",""),feedback=body.getOrDefault("feedback","").trim();
        if(!Set.of("hide","delete","dismiss").contains(decision)||feedback.isBlank()||feedback.length()>500)throw new BusinessException("请选择处理结果并填写500字以内的处理说明");
        var rows=db.queryForList("SELECT * FROM community_report WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw new BusinessException(404,"举报不存在");var report=rows.get(0);
        if(!"PENDING".equals(report.get("status")))throw new BusinessException(409,"该举报已经处理");
        var posts=db.queryForList("SELECT * FROM community_post WHERE id=? FOR UPDATE",report.get("post_id"));
        if(!decision.equals("dismiss")&&!posts.isEmpty()){
            var p=posts.get(0);String target=decision.equals("delete")?"DELETED":"FORCED";
            if(!"DELETED".equals(p.get("status"))&&!target.equals(p.get("status"))){db.update("UPDATE community_post SET status=? WHERE id=?",target,report.get("post_id"));notice(p.get("owner").toString(),report.get("post_id"),target,"管理员处理举报后已"+(decision.equals("delete")?"删除":"隐藏")+"你的帖子："+feedback);}
        }
        java.time.LocalDateTime mutedUntil=null;
        if("true".equals(body.get("mute"))){
            if(decision.equals("dismiss"))throw new BusinessException("举报未成立时不能禁言");
            mutedUntil=discipline.mute(report.get("post_owner").toString(),body.getOrDefault("muteMinutes","60"),feedback,((Number)report.get("post_id")).longValue(),report.get("post_title").toString());
            notice(report.get("post_owner").toString(),report.get("post_id"),"WARNING","举报处置附加社区禁言，截止 "+mutedUntil+"："+feedback);
        }
        db.update("UPDATE community_report SET muted_until=? WHERE id=?",mutedUntil,id);
        db.update("UPDATE community_report SET status=?,decision=?,feedback=?,reviewer=?,reviewed_at=NOW() WHERE id=?",decision.equals("dismiss")?"DISMISSED":"RESOLVED",decision,feedback,owner(),id);
        operations.record("举报处理："+(decision.equals("dismiss")?"未成立":decision.equals("delete")?"删除帖子":"隐藏帖子"),"REPORT",id,report.get("post_owner").toString(),((Number)report.get("post_id")).longValue(),report.get("post_title").toString(),feedback);
        notice(report.get("reporter").toString(),report.get("post_id"),"REPORT_RESULT","你的举报已处理："+(decision.equals("dismiss")?"举报未成立":decision.equals("delete")?"帖子已删除":"帖子已隐藏")+"。"+feedback);
    }
    public Map<String,Object> appealPage(boolean admin,int page,int size){
        if(admin)AuthContext.requireAdmin();size=PageSize.checked(size);
        String where=admin?"":" WHERE a.owner=?";
        List<Object> args=new ArrayList<>();if(!admin)args.add(owner());
        long total=db.queryForObject("SELECT COUNT(*) FROM community_appeal a"+where,Long.class,args.toArray());
        args.add(size);args.add(PageSize.offset(page,size));
        var rows=db.queryForList("SELECT a.*,COALESCE(r.post_title,o.post_title) post_title,COALESCE(r.feedback,o.details) report_feedback,COALESCE(r.post_id,o.post_id) post_id FROM community_appeal a LEFT JOIN community_report r ON r.id=a.report_id LEFT JOIN admin_operation_log o ON o.id=a.operation_id"+where+" ORDER BY a.id DESC LIMIT ? OFFSET ?",args.toArray());
        return Map.of("list",rows,"total",total,"pending",admin?db.queryForObject("SELECT COUNT(*) FROM community_appeal WHERE status='PENDING'",Long.class):0);
    }
    public List<Map<String,Object>> appealable(){
        var result=db.queryForList("SELECT r.* FROM community_report r WHERE r.post_owner=? AND r.status='RESOLVED' AND NOT EXISTS(SELECT 1 FROM community_appeal a WHERE a.report_id=r.id) ORDER BY r.id DESC",owner());
        result.addAll(db.queryForList("SELECT o.id operation_id,o.post_id,o.post_title,o.details feedback FROM admin_operation_log o JOIN community_post p ON p.id=o.post_id WHERE o.target_owner=? AND o.target_type='POST' AND o.action IN ('隐藏帖子','屏蔽下架帖子','删除帖子') AND p.status IN ('FORCED','DELETED') AND o.id=(SELECT MAX(latest.id) FROM admin_operation_log latest WHERE latest.post_id=o.post_id AND latest.target_type='POST' AND latest.action IN ('隐藏帖子','屏蔽下架帖子','删除帖子')) AND NOT EXISTS(SELECT 1 FROM community_appeal a WHERE a.operation_id=o.id) ORDER BY o.id DESC",owner()));return result;
    }
    @Transactional
    public void appealOperation(long operationId,String content){
        AuthContext.requireUserId();content=content==null?"":content.trim();
        if(content.codePointCount(0,content.length())<100||content.length()>3000)throw new BusinessException("申诉说明需100至3000字");
        var rows=db.queryForList("SELECT o.id FROM admin_operation_log o JOIN community_post p ON p.id=o.post_id WHERE o.id=? AND o.target_owner=? AND o.target_type='POST' AND o.action IN ('隐藏帖子','屏蔽下架帖子','删除帖子') AND p.status IN ('FORCED','DELETED') FOR UPDATE",operationId,owner());
        if(rows.isEmpty())throw new BusinessException(403,"只能申诉本人的帖子处置");
        if(db.queryForObject("SELECT COUNT(*) FROM community_appeal WHERE operation_id=?",Long.class,operationId)>0)throw new BusinessException(409,"该处置已提交申诉");
        db.update("INSERT INTO community_appeal(operation_id,owner,content) VALUES(?,?,?)",operationId,owner(),content);
    }
    @Transactional
    public void appeal(long reportId,String content){
        AuthContext.requireUserId();content=content==null?"":content.trim();
        if(content.codePointCount(0,content.length())<100||content.length()>3000)throw new BusinessException("申诉说明需100至3000字");
        var rows=db.queryForList("SELECT * FROM community_report WHERE id=? AND post_owner=? AND status='RESOLVED' FOR UPDATE",reportId,owner());
        if(rows.isEmpty())throw new BusinessException(403,"只能申诉本人已被处置的举报");
        if(db.queryForObject("SELECT COUNT(*) FROM community_appeal WHERE report_id=?",Long.class,reportId)>0)throw new BusinessException(409,"此处置已提交申诉，请在申诉记录中查看结果");
        db.update("INSERT INTO community_appeal(report_id,owner,content) VALUES(?,?,?)",reportId,owner(),content);
    }
    @Transactional
    public void resolveAppeal(long id,Map<String,String> body){
        AuthContext.requireAdmin();String decision=body.getOrDefault("decision",""),feedback=body.getOrDefault("feedback","").trim();
        if(!Set.of("APPROVED","REJECTED").contains(decision)||feedback.isBlank()||feedback.length()>500)throw new BusinessException("请选择结果并填写500字以内的处理说明");
        var rows=db.queryForList("SELECT * FROM community_appeal WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw new BusinessException(404,"申诉不存在");var appeal=rows.get(0);
        if(!"PENDING".equals(appeal.get("status")))throw new BusinessException(409,"申诉已处理");
        if(appeal.get("report_id")==null){resolveOperationAppeal(appeal,decision,feedback);return;}
        var report=db.queryForMap("SELECT * FROM community_report WHERE id=? FOR UPDATE",appeal.get("report_id"));
        if(decision.equals("APPROVED")){
            db.update("UPDATE community_report SET status='OVERTURNED' WHERE id=?",report.get("id"));
            // 删除帖子仍保留删除状态；隐藏帖子仅在没有其他有效举报处置时恢复。
            if(db.queryForObject("SELECT COUNT(*) FROM community_report WHERE post_id=? AND status='RESOLVED'",Long.class,report.get("post_id"))==0
                && db.queryForObject("SELECT COUNT(*) FROM admin_operation_log WHERE post_id=? AND target_type='POST' AND action IN ('隐藏帖子','屏蔽下架帖子','删除帖子') AND id>COALESCE((SELECT MAX(review.id) FROM admin_operation_log review WHERE review.target_type='REPORT' AND review.target_id=?),0)",Long.class,report.get("post_id"),report.get("id").toString())==0)
                db.update("UPDATE community_post SET status='PUBLIC' WHERE id=? AND status='FORCED'",report.get("post_id"));
            if(report.get("muted_until")!=null)db.update("UPDATE community_user SET muted_until=NULL WHERE owner=? AND muted_until=?",appeal.get("owner"),report.get("muted_until"));
        }
        db.update("UPDATE community_appeal SET status=?,feedback=?,reviewer=?,reviewed_at=NOW() WHERE id=?",decision,feedback,owner(),id);
        notice(appeal.get("owner").toString(),report.get("post_id"),"APPEAL_RESULT","申诉"+(decision.equals("APPROVED")?"通过":"未通过")+"："+feedback);
        operations.record("申诉"+(decision.equals("APPROVED")?"通过":"驳回"),"APPEAL",id,appeal.get("owner").toString(),((Number)report.get("post_id")).longValue(),report.get("post_title").toString(),feedback);
    }
    private void resolveOperationAppeal(Map<String,Object> appeal,String decision,String feedback){
        var operation=db.queryForMap("SELECT * FROM admin_operation_log WHERE id=?",appeal.get("operation_id"));
        var post=db.queryForMap("SELECT * FROM community_post WHERE id=? FOR UPDATE",operation.get("post_id"));
        long latest=db.queryForObject("SELECT MAX(id) FROM admin_operation_log WHERE post_id=? AND target_type='POST' AND action IN ('隐藏帖子','屏蔽下架帖子','删除帖子')",Long.class,post.get("id"));
        if(decision.equals("APPROVED")&&latest==((Number)operation.get("id")).longValue()&&db.queryForObject("SELECT COUNT(*) FROM community_report WHERE post_id=? AND status='RESOLVED'",Long.class,post.get("id"))==0)
            db.update("UPDATE community_post SET status='PUBLIC' WHERE id=? AND status='FORCED'",post.get("id"));
        db.update("UPDATE community_appeal SET status=?,feedback=?,reviewer=?,reviewed_at=NOW() WHERE id=?",decision,feedback,owner(),appeal.get("id"));
        notice(appeal.get("owner").toString(),post.get("id"),"APPEAL_RESULT","申诉"+(decision.equals("APPROVED")?"通过":"未通过")+"："+feedback);
        operations.record("申诉"+(decision.equals("APPROVED")?"通过":"驳回"),"APPEAL",appeal.get("id"),appeal.get("owner").toString(),((Number)post.get("id")).longValue(),post.get("title").toString(),feedback);
    }
    private void notice(String recipient,Object postId,String kind,String message){db.update("INSERT INTO community_notice(owner,post_id,kind,message) VALUES(?,?,?,?)",recipient,postId,kind,message.length()>500?message.substring(0,500):message);}
}
