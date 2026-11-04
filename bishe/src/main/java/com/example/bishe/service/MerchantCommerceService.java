package com.example.bishe.service;

import com.example.bishe.common.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.sql.Statement;
import java.util.*;

/** 商品保留旧表主键以兼容历史订单；广告独立存储并单独审核。 */
@Service
public class MerchantCommerceService {
    private final JdbcTemplate db;
    private final MerchantService merchants;
    private final ShopService shop;
    private final JsonMapper json;
    private static final String PRODUCT="SELECT p.ad_id AS id,p.merchant_id AS merchantId,p.title,p.content,p.image_url AS imageUrl,p.price,p.score,p.review_status AS reviewStatus,p.review_reason AS reviewReason,p.revision,p.is_deleted AS withdrawn,p.create_time AS createdAt,m.name AS merchantName,m.city,d.blocks FROM advertisement p JOIN merchant m ON m.merchant_id=p.merchant_id LEFT JOIN product_detail d ON d.ad_id=p.ad_id";
    private static final String CAMPAIGN="SELECT c.id,c.merchant_id AS merchantId,c.product_id AS productId,c.title,c.content,c.image_url AS imageUrl,c.review_status AS reviewStatus,c.review_reason AS reviewReason,c.revision,c.carousel_enabled AS carouselEnabled,c.created_at AS createdAt,m.name AS merchantName,m.city,c.target_type AS targetType,p.title AS productTitle,p.price,p.review_status AS productStatus,p.is_deleted AS productWithdrawn FROM merchant_campaign c JOIN merchant m ON m.merchant_id=c.merchant_id LEFT JOIN advertisement p ON p.ad_id=c.product_id";
    public MerchantCommerceService(JdbcTemplate db,MerchantService merchants,ShopService shop,JsonMapper json){this.db=db;this.merchants=merchants;this.shop=shop;this.json=json;}
    public Map<String,Object> stats(){
        long id=merchants.requireMerchant();
        return Map.of("products",db.queryForObject("SELECT COUNT(*) FROM advertisement WHERE merchant_id=? AND is_deleted=0",Long.class,id),"campaigns",db.queryForObject("SELECT COUNT(*) FROM merchant_campaign WHERE merchant_id=? AND review_status='APPROVED'",Long.class,id),"pendingOrders",db.queryForObject("SELECT COUNT(*) FROM order_record WHERE merchant_id=? AND status=0",Long.class,id),"reviews",db.queryForObject("SELECT COUNT(*) FROM order_evaluation e JOIN order_record o ON o.order_id=e.order_id WHERE o.merchant_id=?",Long.class,id));
    }
    public List<Map<String,Object>> productOptions(String keyword){
        return db.queryForList("SELECT ad_id AS id,title,price FROM advertisement WHERE merchant_id=? AND review_status='APPROVED' AND is_deleted=0 AND title LIKE ? ORDER BY ad_id DESC LIMIT 50",merchants.requireMerchant(),"%"+keyword.substring(0,Math.min(100,keyword.length()))+"%");
    }
    public Map<String,Object> list(String kind,boolean admin,String status,int page,int size){
        if(admin)AuthContext.requireAdmin();
        if("all".equals(kind)){AuthContext.requireAdmin();return allReviews(status,page,size);}
        boolean product="products".equals(kind);
        String alias=product?"p":"c",where=admin?" WHERE 1=1":" WHERE "+alias+".merchant_id=?";
        var args=new ArrayList<Object>();if(!admin)args.add(merchants.requireMerchant());
        if(status!=null&&!status.isBlank()){
            if(!Set.of("PENDING","APPROVED","REJECTED","WITHDRAWN").contains(status))throw new BusinessException("审核状态无效");
            where+=" AND "+alias+".review_status=?";args.add(status);
        }
        String base=product?PRODUCT:CAMPAIGN;
        long total=db.queryForObject("SELECT COUNT(*) FROM ("+base+where+") merchant_rows",Long.class,args.toArray());
        size=Math.max(1,Math.min(size,50));page=Math.max(1,page);args.add(size);args.add((page-1)*size);
        return Map.of("total",total,"list",db.queryForList(base+where+" ORDER BY "+alias+"."+(product?"ad_id":"id")+" DESC LIMIT ? OFFSET ?",args.toArray()));
    }
    @Transactional
    public Map<String,Object> saveProduct(Long id,Map<String,Object> b){
        long owner=merchants.requireMerchant();
        var profile=merchants.publicProfile(owner);
        String title=MerchantService.text(b,"title",100,true),content=MerchantService.text(b,"content",1000,true),image=image(b),blocks=blocks(b);
        BigDecimal price;
        try{price=new BigDecimal(String.valueOf(b.get("price")));if(price.signum()<=0||price.scale()>2||price.compareTo(new BigDecimal("99999999.99"))>0)throw new IllegalArgumentException();}catch(Exception e){throw new BusinessException("价格须大于0，最多两位小数");}
        if(id==null){
            var key=new GeneratedKeyHolder();
            db.update(c->{var s=c.prepareStatement("INSERT INTO advertisement(title,content,image_url,price,publisher_id,merchant_id,review_status) VALUES(?,?,?,?,?,?,'PENDING')",Statement.RETURN_GENERATED_KEYS);s.setString(1,title);s.setString(2,content);s.setString(3,image);s.setBigDecimal(4,price);s.setString(5,profile.get("accountCode").toString());s.setLong(6,owner);return s;},key);id=key.getKey().longValue();
        }else{
            ownedProduct(id,owner);checkRevision(b,db.queryForObject("SELECT revision FROM advertisement WHERE ad_id=?",Integer.class,id));
            db.update("UPDATE advertisement SET title=?,content=?,image_url=?,price=?,review_status='PENDING',review_reason='',reviewed_by=NULL,reviewed_at=NULL,is_deleted=0,revision=revision+1 WHERE ad_id=?",title,content,image,price,id);
            db.update("UPDATE merchant_campaign SET review_status='PENDING',carousel_enabled=0,review_reason='关联商品已修改，请重新审核',revision=revision+1 WHERE product_id=? AND review_status<>'WITHDRAWN'",id);
        }
        db.update("INSERT INTO product_detail(ad_id,origin,blocks) VALUES(?,?,?) ON DUPLICATE KEY UPDATE origin=VALUES(origin),blocks=VALUES(blocks)",id,profile.get("city"),blocks);
        return db.queryForMap(PRODUCT+" WHERE p.ad_id=?",id);
    }
    @Transactional
    public Map<String,Object> saveCampaign(Long id,Map<String,Object> b){
        long owner=merchants.requireMerchant();
        String targetType=b.get("targetType")==null?"PRODUCT":String.valueOf(b.get("targetType"));
        if(!Set.of("PRODUCT","MERCHANT").contains(targetType))throw new BusinessException("请选择有效的广告关联方式");
        Long productId="PRODUCT".equals(targetType)?number(b,"productId"):null;
        // 同一商家先锁商品，再锁广告，和商品编辑保持一致的锁顺序。
        if(productId!=null){var p=ownedProduct(productId,owner);if(!"APPROVED".equals(p.get("review_status"))||isTrue(p.get("is_deleted")))throw new BusinessException("请选择自己已审核上架的商品");}
        else if(db.queryForList("SELECT ad_id FROM advertisement WHERE merchant_id=? AND review_status='APPROVED' AND is_deleted=0 ORDER BY ad_id LIMIT 1 FOR SHARE",owner).isEmpty())throw new BusinessException("关联商家主页前，请至少上架一件自己的商品");
        String title=MerchantService.text(b,"title",100,true),content=MerchantService.text(b,"content",1000,true),image=image(b);
        if(id==null){
            var key=new GeneratedKeyHolder();
            db.update(c->{var s=c.prepareStatement("INSERT INTO merchant_campaign(merchant_id,product_id,title,content,image_url,target_type) VALUES(?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);s.setLong(1,owner);s.setObject(2,productId);s.setString(3,title);s.setString(4,content);s.setString(5,image);s.setString(6,targetType);return s;},key);id=key.getKey().longValue();
        }else{
            var c=ownedCampaign(id,owner);checkRevision(b,((Number)c.get("revision")).intValue());
            db.update("UPDATE merchant_campaign SET product_id=?,target_type=?,title=?,content=?,image_url=?,carousel_enabled=0,review_status='PENDING',review_reason='',reviewed_by=NULL,reviewed_at=NULL,revision=revision+1 WHERE id=?",productId,targetType,title,content,image,id);
        }
        return db.queryForMap(CAMPAIGN+" WHERE c.id=?",id);
    }
    @Transactional
    public void withdraw(String kind,long id){
        long owner=merchants.requireMerchant();
        if("products".equals(kind)){ownedProduct(id,owner);db.update("UPDATE advertisement SET is_deleted=1,review_status='WITHDRAWN',revision=revision+1 WHERE ad_id=?",id);}
        else{ownedCampaign(id,owner);db.update("UPDATE merchant_campaign SET carousel_enabled=0,review_status='WITHDRAWN',revision=revision+1 WHERE id=?",id);}
    }
    @Transactional
    public void review(String kind,long id,Map<String,Object> b){
        String admin=AuthContext.requireAdminId(),decision=MerchantService.text(b,"decision",16,true),reason=MerchantService.text(b,"reason",500,false);
        if(!Set.of("APPROVED","REJECTED").contains(decision))throw new BusinessException("审核决定无效");
        if("REJECTED".equals(decision)&&reason.isBlank())throw new BusinessException("请填写驳回或下架原因");
        boolean product="products".equals(kind);
        String table=product?"advertisement":"merchant_campaign",key=product?"ad_id":"id";
        if(!product){
            var campaign=db.queryForList("SELECT product_id,merchant_id,target_type FROM merchant_campaign WHERE id=?",id);
            if(campaign.isEmpty())throw new BusinessException(404,"广告不存在");
            var c=campaign.get(0);
            if("PRODUCT".equals(c.get("target_type"))){
                var p=db.queryForMap("SELECT * FROM advertisement WHERE ad_id=? FOR UPDATE",c.get("product_id"));
                if("APPROVED".equals(decision)&&(!"APPROVED".equals(p.get("review_status"))||isTrue(p.get("is_deleted"))))throw new BusinessException("关联商品尚未上架，无法批准广告");
            }else if("APPROVED".equals(decision)&&db.queryForList("SELECT ad_id FROM advertisement WHERE merchant_id=? AND review_status='APPROVED' AND is_deleted=0 LIMIT 1 FOR SHARE",c.get("merchant_id")).isEmpty())throw new BusinessException("商家目前没有在售商品，无法批准主页广告");
        }
        var rows=db.queryForList("SELECT * FROM "+table+" WHERE "+key+"=? FOR UPDATE",id);
        if(rows.isEmpty()||rows.get(0).get("merchant_id")==null)throw new BusinessException(404,"申请不存在");
        var row=rows.get(0);checkRevision(b,((Number)row.get("revision")).intValue());
        if(!"PENDING".equals(row.get("review_status"))&&!("APPROVED".equals(row.get("review_status"))&&"REJECTED".equals(decision)))throw new BusinessException("状态已改变，请刷新审核列表");
        db.update("UPDATE "+table+" SET "+(!product?"carousel_enabled=0,":"")+"review_status=?,review_reason=?,reviewed_by=?,reviewed_at=NOW(),revision=revision+1 WHERE "+key+"=?",decision,reason,admin,id);
        db.update("INSERT INTO merchant_review_log(resource_type,resource_id,revision,decision,reason,admin_id) VALUES(?,?,?,?,?,?)",kind,id,row.get("revision"),decision,reason,admin);
    }
    public Map<String,Object> reviewCounts(){AuthContext.requireAdmin();return Map.of("products",db.queryForObject("SELECT COUNT(*) FROM advertisement WHERE merchant_id IS NOT NULL AND review_status='PENDING'",Long.class),"campaigns",db.queryForObject("SELECT COUNT(*) FROM merchant_campaign WHERE review_status='PENDING'",Long.class));}
    public List<Map<String,Object>> publicCampaigns(){
        return db.queryForList(CAMPAIGN+" WHERE c.carousel_enabled=1 AND c.review_status='APPROVED' AND m.is_banned=0 AND ((c.target_type='PRODUCT' AND p.review_status='APPROVED' AND p.is_deleted=0) OR (c.target_type='MERCHANT' AND EXISTS(SELECT 1 FROM advertisement goods WHERE goods.merchant_id=c.merchant_id AND goods.review_status='APPROVED' AND goods.is_deleted=0))) ORDER BY c.reviewed_at DESC,c.id DESC");
    }
    public Map<String,Object> orders(int page,int size){
        long owner=merchants.requireMerchant();size=Math.max(1,Math.min(50,size));page=Math.max(1,page);
        return Map.of("total",db.queryForObject("SELECT COUNT(*) FROM order_record WHERE merchant_id=? AND merchant_deleted=0",Long.class,owner),"list",db.queryForList("SELECT order_id AS orderId,product_title AS productTitle,unit_price AS unitPrice,ship_origin AS shipOrigin,shipping_snapshot AS shippingAddress,status,logistics_info AS logisticsInfo,create_time AS createdAt,after_sale_status AS afterSaleStatus,after_sale_content AS afterSaleContent,after_sale_reply AS afterSaleReply FROM order_record WHERE merchant_id=? AND merchant_deleted=0 ORDER BY create_time DESC,order_id DESC LIMIT ? OFFSET ?",owner,size,(page-1)*size));
    }
    @Transactional
    public void deleteOrder(long id){
        ownedOrder(id);var r=db.queryForMap("SELECT status,after_sale_status FROM order_record WHERE order_id=?",id);
        if(((Number)r.get("status")).intValue()!=2||((Number)r.get("after_sale_status")).intValue()==1)throw new BusinessException("仅可删除已收货且无进行中售后的历史订单");
        db.update("UPDATE order_record SET merchant_deleted=1 WHERE order_id=?",id);
    }
    private Map<String,Object> allReviews(String status,int page,int size){
        if(status!=null&&!status.isBlank()&&!Set.of("PENDING","APPROVED","REJECTED","WITHDRAWN").contains(status))throw new BusinessException("审核状态无效");
        String common="id,merchantId,title,content,imageUrl,price,reviewStatus,reviewReason,revision,createdAt,merchantName,city";
        String union="SELECT "+common+",blocks,NULL AS productTitle,'products' AS itemType,'PRODUCT' AS targetType FROM ("+PRODUCT+") products UNION ALL SELECT "+common+",NULL AS blocks,productTitle,'campaigns' AS itemType,targetType FROM ("+CAMPAIGN+") campaigns";
        String from=" FROM ("+union+") combined";var args=new ArrayList<Object>();
        if(status!=null&&!status.isBlank()){from+=" WHERE reviewStatus=?";args.add(status);}
        long total=db.queryForObject("SELECT COUNT(*)"+from,Long.class,args.toArray());
        size=Math.max(1,Math.min(50,size));page=Math.max(1,page);args.add(size);args.add((long)(page-1)*size);
        return Map.of("total",total,"list",db.queryForList("SELECT *"+from+" ORDER BY createdAt DESC,itemType,id DESC LIMIT ? OFFSET ?",args.toArray()));
    }
    public Map<String,Object> storefront(long id){
        merchants.releaseExpiredBan(id);
        if(db.queryForObject("SELECT COUNT(*) FROM merchant WHERE merchant_id=? AND is_banned=1",Long.class,id)>0)throw new BusinessException(403,"该商家已被封禁，所有商品已下架，广告已撤销",Map.of("type","MERCHANT_STOREFRONT_BANNED"));
        var rows=db.queryForList("SELECT merchant_id AS merchantId,name,city,avatar,cover_url AS coverUrl FROM merchant WHERE merchant_id=? AND activated_at IS NOT NULL AND is_banned=0 AND must_change_password=0",id);
        if(rows.isEmpty())throw new BusinessException(404,"商家不存在或已暂停营业");var merchant=rows.get(0);
        merchant.put("rating",db.queryForObject("SELECT COALESCE(ROUND(AVG(e.rating),1),0) FROM order_evaluation e JOIN order_record o ON o.order_id=e.order_id WHERE o.merchant_id=?",java.math.BigDecimal.class,id));
        merchant.put("reviewCount",db.queryForObject("SELECT COUNT(*) FROM order_evaluation e JOIN order_record o ON o.order_id=e.order_id WHERE o.merchant_id=?",Long.class,id));
        merchant.put("sales",db.queryForObject("SELECT COUNT(*) FROM order_record WHERE merchant_id=?",Long.class,id));
        var products=db.queryForList("SELECT ad_id AS adId,title,content,image_url AS imageUrl,price,score,merchant_id AS merchantId FROM advertisement WHERE merchant_id=? AND review_status='APPROVED' AND is_deleted=0 ORDER BY ad_id DESC",id);
        return Map.of("merchant",merchant,"products",products);
    }
    @Transactional
    public void ship(long id,Map<String,Object> b){
        ownedOrder(id);String logistics=MerchantService.text(b,"logisticsInfo",255,true);
        var r=new com.example.bishe.dto.LogisticsUpdateRequest();r.setOrderId(id);r.setLogisticsInfo(logistics);shop.publishLogistics(r);
    }
    @Transactional
    public void afterSale(long id,Map<String,Object> b){
        ownedOrder(id);var r=new com.example.bishe.dto.AfterSaleReplyRequest();r.setOrderId(id);r.setReply(MerchantService.text(b,"reply",500,true));shop.replyAfterSale(r);
    }
    public Map<String,Object> evaluations(int page,int size){
        long owner=merchants.requireMerchant();size=Math.max(1,Math.min(50,size));page=Math.max(1,page);
        return Map.of("total",db.queryForObject("SELECT COUNT(*) FROM order_evaluation e JOIN order_record o ON o.order_id=e.order_id WHERE o.merchant_id=?",Long.class,owner),"list",db.queryForList("SELECT e.evaluation_id AS id,e.order_id AS orderId,e.rating,e.feedback_content AS content,e.publish_time AS createdAt,o.product_title AS productTitle FROM order_evaluation e JOIN order_record o ON o.order_id=e.order_id WHERE o.merchant_id=? ORDER BY e.publish_time DESC,e.evaluation_id DESC LIMIT ? OFFSET ?",owner,size,(page-1)*size));
    }
    private Map<String,Object> ownedProduct(long id,long owner){var rows=db.queryForList("SELECT * FROM advertisement WHERE ad_id=? AND merchant_id=? FOR UPDATE",id,owner);if(rows.isEmpty())throw new BusinessException(404,"商品不存在或不属于当前商家");return rows.get(0);}
    private boolean isTrue(Object value){return Boolean.TRUE.equals(value)||(value instanceof Number n&&n.intValue()!=0);}
    private Map<String,Object> ownedCampaign(long id,long owner){var rows=db.queryForList("SELECT * FROM merchant_campaign WHERE id=? AND merchant_id=? FOR UPDATE",id,owner);if(rows.isEmpty())throw new BusinessException(404,"广告不存在或不属于当前商家");return rows.get(0);}
    private void ownedOrder(long id){if(db.queryForList("SELECT order_id FROM order_record WHERE order_id=? AND merchant_id=? AND merchant_deleted=0 FOR UPDATE",id,merchants.requireMerchant()).isEmpty())throw new BusinessException(404,"订单不存在或不属于当前商家");}
    private void checkRevision(Map<String,Object> b,int revision){if(number(b,"revision")!=revision)throw new BusinessException(409,"内容已更新，请刷新后再操作");}
    private long number(Map<String,Object> b,String key){try{return Long.parseLong(String.valueOf(b.get(key)));}catch(Exception e){throw new BusinessException("请提供有效的"+key);}}
    private String image(Map<String,Object> b){String s=MerchantService.text(b,"imageUrl",255,true);if(!s.matches("/uploads/image/[a-f0-9]{32}\\.(jpg|jpeg|png|gif|webp|bmp)"))throw new BusinessException("请上传有效的商品或广告图片");return s;}
    private String blocks(Map<String,Object> b){
        String s=MerchantService.text(b,"blocks",200000,true);
        try{var nodes=json.readTree(s);if(!nodes.isArray()||nodes.size()==0||nodes.size()>50)throw new IllegalArgumentException();
            boolean hasContent=false;
            for(var node:nodes){String type=node.path("type").asText(),value=node.path("value").asText();if("text".equals(type)){if(value.length()>10000)throw new IllegalArgumentException();if(!value.isBlank())hasContent=true;}else if("image".equals(type)&&value.matches("/uploads/image/[a-f0-9]{32}\\.(jpg|jpeg|png|gif|webp|bmp)")){hasContent=true;}else throw new IllegalArgumentException();}
            if(!hasContent)throw new IllegalArgumentException();return s;
        }catch(Exception e){throw new BusinessException("请添加有效的商品图文介绍（最多50段）");}
    }
}
