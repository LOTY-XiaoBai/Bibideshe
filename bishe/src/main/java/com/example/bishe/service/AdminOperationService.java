package com.example.bishe.service;

import com.example.bishe.common.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** 管理员操作只提供新增和查询，业务修改与留痕共用当前事务。 */
@Service
public class AdminOperationService {
    private final JdbcTemplate db;
    public AdminOperationService(JdbcTemplate db){this.db=db;}
    public void record(String action,String type,Object id,String target,Long postId,String title,String details){
        var u=AuthContext.get();
        if(u!=null&&UserRole.ADMIN.equals(u.getRole()))recordAs("ADMIN:"+u.getSubject(),action,type,id,target,postId,title,details);
    }
    public void recordAs(String operator,String action,String type,Object id,String target,Long postId,String title,String details){
        var attributes=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if(attributes!=null)attributes.setAttribute("adminOperationRecorded",true,0);
        db.update("INSERT INTO admin_operation_log(operator,action,target_type,target_id,target_owner,post_id,post_title,details) VALUES(?,?,?,?,?,?,?,?)",
            operator,action,type,Objects.toString(id,""),Objects.toString(target,""),postId,cut(title,120),cut(details,2000));
    }
    private String cut(String s,int max){return s==null?"":s.substring(0,Math.min(s.length(),max));}
    public Map<String,Object> list(String q,int page){return list(q,page,20);}
    public Map<String,Object> list(String q,int page,int size){
        size=PageSize.checked(size);
        AuthContext.requireAdmin();if(q.length()>120)throw new BusinessException("搜索词过长");
        String where=" WHERE CONCAT_WS(' ',operator,action,target_type,target_id,target_owner,post_title,details) LIKE ?";
        String search="%"+q+"%";int offset=PageSize.offset(page,size);
        return Map.of("list",db.queryForList("SELECT * FROM admin_operation_log"+where+" ORDER BY id DESC LIMIT ? OFFSET ?",search,size,offset),
            "total",db.queryForObject("SELECT COUNT(*) FROM admin_operation_log"+where,Long.class,search));
    }
}
