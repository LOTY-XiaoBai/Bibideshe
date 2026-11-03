package com.example.bishe.service;
import com.example.bishe.common.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.LocalDateTime;
@Service
public class CommunityDisciplineService {
    private final JdbcTemplate db;
    private final AdminOperationService operations;
    public CommunityDisciplineService(JdbcTemplate db,AdminOperationService operations){this.db=db;this.operations=operations;}
    // 同一UPDATE中先扣减再推进锚点，并发请求不会重复扣减；离线多天一次结算。
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void decay(){db.update("UPDATE community_user SET warnings=GREATEST(0,warnings-TIMESTAMPDIFF(HOUR,warning_decay_at,NOW()) DIV 24),warning_decay_at=TIMESTAMPADD(HOUR,(TIMESTAMPDIFF(HOUR,warning_decay_at,NOW()) DIV 24)*24,warning_decay_at) WHERE warning_decay_at<=DATE_SUB(NOW(),INTERVAL 24 HOUR)");}
    public LocalDateTime mute(String target,String minutes,String reason,Long postId,String title){
        AuthContext.requireAdmin();
        LocalDateTime until;
        if("PERMANENT".equals(minutes))until=LocalDateTime.of(9999,12,31,23,59,59);
        else {long value;try{value=Long.parseLong(minutes);}catch(Exception e){throw new BusinessException("禁言时长无效");}if(value<1||value>5256000)throw new BusinessException("自定义禁言须为1分钟至10年");until=LocalDateTime.now().withNano(0).plusMinutes(value);}
        db.update("INSERT IGNORE INTO community_user(owner) VALUES(?)",target);
        db.update("UPDATE community_user SET muted_until=? WHERE owner=?",until,target);
        operations.record("社区禁言","USER",target,target,postId,title,reason+"；截止："+until);
        return until;
    }
}
