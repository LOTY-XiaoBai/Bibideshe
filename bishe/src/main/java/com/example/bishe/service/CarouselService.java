package com.example.bishe.service;

import com.example.bishe.common.AuthContext;
import com.example.bishe.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Service
public class CarouselService {
    private final JdbcTemplate db;
    private final MerchantCommerceService commerce;
    public CarouselService(JdbcTemplate db, MerchantCommerceService commerce) { this.db=db; this.commerce=commerce; }

    public Map<String,Object> settings() {
        AuthContext.requireAdmin();
        return config();
    }
    private Map<String,Object> config() {
        return db.queryForMap("SELECT official_enabled AS officialEnabled,interval_seconds AS intervalSeconds,revision FROM home_carousel_config WHERE id=1");
    }
    @Transactional
    public Map<String,Object> saveSettings(Map<String,Object> body) {
        AuthContext.requireAdmin();
        if (!(body.get("officialEnabled") instanceof Boolean)) throw new BusinessException("请选择官方广告是否加入轮播");
        int seconds=integer(body,"intervalSeconds"),revision=integer(body,"revision");
        if(seconds<3||seconds>60) throw new BusinessException("轮播间隔须为3至60秒的整数");
        if(db.update("UPDATE home_carousel_config SET official_enabled=?,interval_seconds=?,revision=revision+1 WHERE id=1 AND revision=?",body.get("officialEnabled"),seconds,revision)!=1)
            throw new BusinessException(409,"轮播设置已被修改，请刷新后重试");
        return config();
    }
    @Transactional
    public void selectCampaign(long id,Map<String,Object> body) {
        AuthContext.requireAdmin();
        if(!(body.get("enabled") instanceof Boolean)) throw new BusinessException("请选择是否加入轮播");
        int revision=integer(body,"revision");
        var rows=db.queryForList("SELECT c.review_status,c.revision,m.is_banned FROM merchant_campaign c JOIN merchant m ON m.merchant_id=c.merchant_id WHERE c.id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw new BusinessException(404,"广告不存在");
        var row=rows.get(0);
        if(((Number)row.get("revision")).intValue()!=revision) throw new BusinessException(409,"广告已更新，请刷新后再操作");
        if(Boolean.TRUE.equals(body.get("enabled"))) {
            boolean banned=Boolean.TRUE.equals(row.get("is_banned"))||(row.get("is_banned") instanceof Number n&&n.intValue()!=0);
            if(!"APPROVED".equals(row.get("review_status"))||banned) throw new BusinessException("只有正常营业商家已通过审核的广告才能加入轮播");
            if(db.queryForObject("SELECT COUNT(*) FROM merchant_campaign c WHERE c.id=? AND ((c.target_type='PRODUCT' AND EXISTS(SELECT 1 FROM advertisement p WHERE p.ad_id=c.product_id AND p.merchant_id=c.merchant_id AND p.review_status='APPROVED' AND p.is_deleted=0)) OR (c.target_type='MERCHANT' AND EXISTS(SELECT 1 FROM advertisement p WHERE p.merchant_id=c.merchant_id AND p.review_status='APPROVED' AND p.is_deleted=0)))",Integer.class,id)==0)
                throw new BusinessException("关联商品已下架或商家没有在售商品，无法加入轮播");
        }
        db.update("UPDATE merchant_campaign SET carousel_enabled=?,revision=revision+1 WHERE id=?",body.get("enabled"),id);
    }
    public Map<String,Object> publicCarousel() {
        AuthContext.requireUserId();
        var result=config();
        result.remove("revision");
        result.put("campaigns",commerce.publicCampaigns());
        return result;
    }
    private int integer(Map<String,Object> body,String key) {
        try { return new java.math.BigDecimal(String.valueOf(body.get(key))).intValueExact(); }
        catch(Exception e) { throw new BusinessException("请提供有效的"+key); }
    }
}
