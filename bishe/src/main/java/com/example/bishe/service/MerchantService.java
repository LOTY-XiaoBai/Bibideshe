package com.example.bishe.service;

import com.example.bishe.common.*;
import com.example.bishe.dto.LoginRequest;
import com.example.bishe.dto.LoginResponse;
import com.example.bishe.util.PasswordPolicy;
import com.example.bishe.util.TokenUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.sql.Statement;
import java.util.*;

@Service
public class MerchantService {
    private static final char[] ALPHABET="ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final Map<String,String> BAN_INTERVALS=Map.of("DAY_1","1 DAY","DAY_3","3 DAY","WEEK_2","2 WEEK","MONTH_1","1 MONTH","MONTH_3","3 MONTH","YEAR_10","10 YEAR");
    private static final Map<String,String> BAN_LABELS=Map.of("DAY_1","1天","DAY_3","3天","WEEK_2","2周","MONTH_1","1个月","MONTH_3","3个月","YEAR_10","10年","PERMANENT","永久");
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final CaptchaService captcha;
    private final LoginGuardService guard;
    public MerchantService(JdbcTemplate db,PasswordEncoder passwords,CaptchaService captcha,LoginGuardService guard){
        this.db=db;this.passwords=passwords;this.captcha=captcha;this.guard=guard;
    }
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void cleanExpired(){db.update("DELETE FROM merchant WHERE activated_at IS NULL AND expires_at<=NOW()");}
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void releaseExpiredBans(){db.update("UPDATE merchant SET is_banned=0,auth_version=auth_version+1 WHERE is_banned=1 AND ban_until<=NOW()");}
    public void releaseExpiredBan(long id){db.update("UPDATE merchant SET is_banned=0,auth_version=auth_version+1 WHERE merchant_id=? AND is_banned=1 AND ban_until<=NOW()",id);}

    public Map<String,Object> invite(){
        StringBuilder code=new StringBuilder("M");
        for(int i=0;i<15;i++)code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        var key=new GeneratedKeyHolder();
        db.update(c->{var s=c.prepareStatement("INSERT INTO merchant(account_code,password,created_by,expires_at) VALUES(?,?,?,DATE_ADD(NOW(),INTERVAL 12 HOUR))",Statement.RETURN_GENERATED_KEYS);s.setString(1,code.toString());s.setString(2,passwords.encode("123456"));s.setString(3,AuthContext.requireAdminId());return s;},key);
        return publicProfile(key.getKey().longValue());
    }
    public Map<String,Object> list(int page,int size){
        AuthContext.requireAdmin();
        releaseExpiredBans();
        cleanExpired();page=Math.max(1,page);size=Math.max(1,Math.min(size,50));
        return Map.of("total",db.queryForObject("SELECT COUNT(*) FROM merchant",Long.class),"list",db.queryForList("SELECT merchant_id AS merchantId,account_code AS accountCode,name,city,avatar,cover_url AS coverUrl,is_banned AS isBanned,ban_reason AS banReason,ban_until AS banUntil,ban_duration AS banDuration,created_at AS createdAt,expires_at AS expiresAt,activated_at AS activatedAt,must_change_password AS mustChangePassword FROM merchant ORDER BY merchant_id DESC LIMIT ? OFFSET ?",size,(page-1)*size));
    }
    @Transactional
    public LoginResponse login(LoginRequest request){
        captcha.verify(request.getCaptchaKey(),request.getCaptchaCode());
        String code=request.getId()==null?"":request.getId().trim().toUpperCase(Locale.ROOT);
        guard.assertNotLocked(code);
        db.update("UPDATE merchant SET is_banned=0,auth_version=auth_version+1 WHERE account_code=? AND is_banned=1 AND ban_until<=NOW()",code);
        var rows=db.queryForList("SELECT * FROM merchant WHERE account_code=? AND (activated_at IS NOT NULL OR expires_at>NOW()) FOR UPDATE",code);
        if(rows.isEmpty())throw new BusinessException("邀请码不存在或已过期，请联系管理员重新邀请");
        var row=rows.get(0);
        if(request.getPassword()==null || request.getPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72 || !passwords.matches(request.getPassword(),row.get("password").toString()))guard.onPasswordWrong(code);
        assertNotBanned(row);
        long id=((Number)row.get("merchant_id")).longValue();
        db.update("UPDATE merchant SET activated_at=COALESCE(activated_at,NOW()),expires_at=NULL WHERE merchant_id=?",id);
        guard.onLoginSuccess(code);
        return session(id);
    }
    public Map<String,Object> publicProfile(long id){
        var rows=db.queryForList("SELECT merchant_id AS merchantId,account_code AS accountCode,name,city,avatar,cover_url AS coverUrl,is_banned AS isBanned,ban_reason AS banReason,ban_until AS banUntil,ban_duration AS banDuration,must_change_password AS mustChangePassword,created_at AS createdAt,expires_at AS expiresAt,activated_at AS activatedAt FROM merchant WHERE merchant_id=?",id);
        if(rows.isEmpty())throw new BusinessException(401,"商家账号已失效");
        return rows.get(0);
    }
    public long requireMerchant(){
        var u=AuthContext.requireLogin();
        if(!UserRole.MERCHANT.equals(u.getRole())||u.getUserId()==null)throw new BusinessException(403,"需要商家身份");
        return u.getUserId();
    }
    public boolean assertSession(AuthUser user){
        releaseExpiredBan(user.getUserId());
        var rows=db.queryForList("SELECT account_code,auth_version,must_change_password,is_banned,ban_reason,ban_until,ban_duration FROM merchant WHERE merchant_id=? AND activated_at IS NOT NULL",user.getUserId());
        if(!rows.isEmpty())assertNotBanned(rows.get(0));
        if(rows.isEmpty() || !user.getSubject().equals(rows.get(0).get("account_code")+"."+rows.get(0).get("auth_version")))throw new BusinessException(401,"商家登录已失效，请重新登录");
        return ((Number)rows.get(0).get("must_change_password")).intValue()==1;
    }
    private void assertNotBanned(Map<String,Object> row){
        if(((Number)row.get("is_banned")).intValue()!=0){
            String duration=Objects.toString(row.get("ban_duration"),"PERMANENT");
            throw new BusinessException(403,"商家账号已被封禁",Map.of("type","MERCHANT_BANNED","reason",Objects.toString(row.get("ban_reason"),""),"durationLabel",BAN_LABELS.getOrDefault(duration,"永久"),"banUntil",Objects.toString(row.get("ban_until"),""),"permanent",row.get("ban_until")==null));
        }
    }
    @Transactional
    public void setBanned(long id,boolean banned,String reason){
        setBanned(id,banned,reason,"PERMANENT");
    }
    @Transactional
    public void setBanned(long id,boolean banned,String reason,String duration){
        AuthContext.requireAdmin();
        if(db.queryForList("SELECT merchant_id FROM merchant WHERE merchant_id=? FOR UPDATE",id).isEmpty())throw new BusinessException(404,"商家不存在");
        if(banned&&(reason==null||reason.isBlank()||reason.length()>500))throw new BusinessException("请填写封禁原因（最多500字）");
        if(banned&&(duration==null||!BAN_LABELS.containsKey(duration)))throw new BusinessException("请选择有效的封禁时长");
        String until=banned&&BAN_INTERVALS.containsKey(duration)?"DATE_ADD(NOW(),INTERVAL "+BAN_INTERVALS.get(duration)+")":"NULL";
        db.update("UPDATE merchant SET is_banned=?,ban_reason=?,ban_duration=?,ban_until="+until+",banned_at="+(banned?"NOW()":"NULL")+",auth_version=auth_version+1 WHERE merchant_id=?",banned?1:0,banned?reason.trim():"",banned?duration:"PERMANENT",id);
        if(banned){
            db.update("UPDATE advertisement SET is_deleted=1,review_status='WITHDRAWN',review_reason='商家已封禁，商品已下架',revision=revision+1 WHERE merchant_id=? AND (is_deleted=0 OR review_status<>'WITHDRAWN')",id);
            db.update("UPDATE merchant_campaign SET carousel_enabled=0,review_status='WITHDRAWN',review_reason='商家已封禁，广告已撤销',revision=revision+1 WHERE merchant_id=? AND review_status<>'WITHDRAWN'",id);
        }
    }
    @Transactional
    public Map<String,Object> saveAvatar(Map<String,Object> body){
        long id=requireMerchant();String avatar=text(body,"avatar",255,false);
        if(!avatar.isBlank()&&!avatar.matches("/uploads/image/[a-f0-9]{32}\\.(jpg|jpeg|png|gif|webp|bmp)"))throw new BusinessException("请上传有效的头像图片");
        db.update("UPDATE merchant SET avatar=? WHERE merchant_id=?",avatar,id);return publicProfile(id);
    }
    @Transactional
    public Map<String,Object> saveCover(Map<String,Object> body){
        long id=requireMerchant();String cover=text(body,"coverUrl",255,false);
        if(!cover.isBlank()&&!cover.matches("/uploads/image/[a-f0-9]{32}\\.(jpg|jpeg|png|gif|webp|bmp)"))throw new BusinessException("请上传有效的主页背景图片");
        db.update("UPDATE merchant SET cover_url=? WHERE merchant_id=?",cover,id);return publicProfile(id);
    }
    @Transactional
    public LoginResponse saveProfile(Map<String,Object> body){
        long id=requireMerchant();
        var row=db.queryForMap("SELECT * FROM merchant WHERE merchant_id=? FOR UPDATE",id);
        String name=text(body,"name",80,true),city=text(body,"city",80,true);
        if(city.matches(".*[<>\\r\\n].*")||city.chars().allMatch(c->Character.isDigit(c)||c=='.'||c==','||c==' '))throw new BusinessException("请填写有效的发货城市名称");
        String next=text(body,"newPassword",72,false);
        boolean first=((Number)row.get("must_change_password")).intValue()==1;
        if(first||!next.isEmpty()){
            String current=text(body,"currentPassword",72,true);
            guard.assertNotLocked("merchant-password:"+id);
            if(!passwords.matches(current,row.get("password").toString()))guard.onPasswordWrong("merchant-password:"+id);
            PasswordPolicy.validate(next);
            if(next.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new BusinessException("新密码的UTF-8长度不能超过72字节");
            if(passwords.matches(next,row.get("password").toString()))throw new BusinessException("新密码不能与原密码相同");
            db.update("UPDATE merchant SET password=?,auth_version=auth_version+1,must_change_password=0 WHERE merchant_id=?",passwords.encode(next),id);
            guard.onLoginSuccess("merchant-password:"+id);
        }
        db.update("UPDATE merchant SET name=?,city=? WHERE merchant_id=?",name,city,id);
        return session(id);
    }
    private LoginResponse session(long id){
        var row=db.queryForMap("SELECT account_code,auth_version FROM merchant WHERE merchant_id=?",id);
        return new LoginResponse(TokenUtil.createToken(new AuthUser(id,row.get("account_code")+"."+row.get("auth_version"),UserRole.MERCHANT)),UserRole.MERCHANT,publicProfile(id));
    }
    public static String text(Map<String,Object> body,String key,int max,boolean required){
        Object raw=body.get(key);String s=raw==null?"":key.endsWith("Password")?raw.toString():raw.toString().trim();
        if((required&&s.isBlank())||s.length()>max)throw new BusinessException("请填写有效的"+switch(key){case "name"->"店铺名称";case "city"->"发货城市";case "title"->"标题";case "content"->"介绍文案";case "currentPassword"->"当前密码";case "newPassword"->"新密码";default->key;}+"（最多"+max+"个字符）");
        return s;
    }
}
