package com.example.bishe;

import com.example.bishe.common.*;
import com.example.bishe.dto.*;
import com.example.bishe.service.*;
import com.example.bishe.util.TokenUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"app.token.secret=test-token-secret-with-at-least-32-characters","app.captcha.expose-code=true"})
@EnabledIfEnvironmentVariable(named="RUN_DB_INTEGRATION",matches="true")
@Transactional
class MerchantCommerceIntegrationTests {
    @Autowired JdbcTemplate db;
    @Autowired MerchantService merchants;
    @Autowired MerchantCommerceService commerce;
    @Autowired CarouselService carousel;
    @Autowired CaptchaService captcha;
    @Autowired ShopService shop;
    @Autowired ProfileShopService extra;
    long owner,other;
    String code,otherCode;
    final String image="/uploads/image/0123456789abcdef0123456789abcdef.png";
    void admin(){AuthContext.set(new AuthUser(null,"admin-merchant-test",UserRole.ADMIN));}
    void merchant(long id,String account){AuthContext.set(new AuthUser(id,account+".0",UserRole.MERCHANT));}
    long id(Map<String,Object> r){return ((Number)r.get("id")).longValue();}
    @BeforeEach void setup(){admin();var a=merchants.invite();var b=merchants.invite();owner=((Number)a.get("merchantId")).longValue();other=((Number)b.get("merchantId")).longValue();code=a.get("accountCode").toString();otherCode=b.get("accountCode").toString();db.update("UPDATE merchant SET name='测试商家',city='杭州市',activated_at=NOW(),expires_at=NULL,must_change_password=0 WHERE merchant_id IN (?,?)",owner,other);merchant(owner,code);}
    @AfterEach void clear(){AuthContext.clear();}
    LoginResponse login(String account,String password){var c=captcha.create();var r=new LoginRequest();r.setId(account);r.setPassword(password);r.setCaptchaKey(c.get("captchaKey"));r.setCaptchaCode(c.get("captchaCode"));return merchants.login(r);}
    Map<String,Object> product(){return new HashMap<>(Map.of("title","校园帆布包","content","耐用的日常好物","price","29.90","imageUrl",image,"blocks","[{\"type\":\"text\",\"value\":\"纯棉帆布，手工缝制\"}]"));}
    Map<String,Object> campaign(long product){return new HashMap<>(Map.of("title","装下你的校园日常","content","一只包，一段新的旅程。","imageUrl",image,"productId",product));}
    void approve(String kind,Map<String,Object> item){admin();commerce.review(kind,id(item),Map.of("decision","APPROVED","revision",item.get("revision")));if("campaigns".equals(kind))carousel.selectCampaign(id(item),Map.of("enabled",true,"revision",((Number)item.get("revision")).intValue()+1));merchant(owner,code);}

    @Test void invitationExpiresOnlyBeforeFirstLoginAndPasswordChangeInvalidatesOldToken(){
        admin();var invite=merchants.invite();String account=invite.get("accountCode").toString();long id=((Number)invite.get("merchantId")).longValue();
        assertThat(account).matches("M[A-HJ-NP-Z2-9]{15}");
        assertThat(invite).doesNotContainKeys("password","authVersion");
        db.update("UPDATE merchant SET expires_at=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE merchant_id=?",id);
        assertThatThrownBy(()->login(account,"123456")).hasMessageContaining("过期");merchants.cleanExpired();assertThat(db.queryForObject("SELECT COUNT(*) FROM merchant WHERE merchant_id=?",Long.class,id)).isZero();
        var fresh=merchants.invite();String freshCode=fresh.get("accountCode").toString();long freshId=((Number)fresh.get("merchantId")).longValue();
        var session=login(freshCode.toLowerCase(Locale.ROOT),"123456");var oldUser=TokenUtil.parseToken(session.getToken());AuthContext.set(oldUser);assertThat(merchants.assertSession(oldUser)).isTrue();
        assertThat(db.queryForObject("SELECT expires_at FROM merchant WHERE merchant_id=?",java.sql.Timestamp.class,freshId)).isNull();
        var changed=merchants.saveProfile(Map.of("name","新商家","city","杭州市","currentPassword","123456","newPassword","SafePass123"));
        assertThat(merchants.assertSession(TokenUtil.parseToken(changed.getToken()))).isFalse();assertThatThrownBy(()->merchants.assertSession(oldUser)).hasMessageContaining("登录已失效");
        db.update("UPDATE merchant SET created_at=DATE_SUB(NOW(),INTERVAL 2 DAY) WHERE merchant_id=?",freshId);merchants.cleanExpired();assertThat(db.queryForObject("SELECT COUNT(*) FROM merchant WHERE merchant_id=?",Long.class,freshId)).isEqualTo(1);
    }
    @Test void productAndCampaignMustBeApprovedSeparatelyAndProductEditSuspendsAdvertising(){
        var p=commerce.saveProduct(null,product());long productId=id(p);assertThat(extraProductUnavailable(productId)).isTrue();
        assertThatThrownBy(()->commerce.saveCampaign(null,campaign(productId))).hasMessageContaining("已审核上架");
        approve("products",p);assertThat(extra.product(productId).get("merchant_name")).isEqualTo("测试商家");
        var c=commerce.saveCampaign(null,campaign(productId));long campaignId=id(c);assertThat(commerce.publicCampaigns()).noneMatch(r->id(r)==campaignId);approve("campaigns",c);assertThat(commerce.publicCampaigns()).anyMatch(r->id(r)==campaignId);
        var update=product();update.put("revision",2);update.put("title","新版本商品");commerce.saveProduct(productId,update);
        assertThat(commerce.publicCampaigns()).noneMatch(r->id(r)==campaignId);assertThat(db.queryForObject("SELECT review_status FROM merchant_campaign WHERE id=?",String.class,campaignId)).isEqualTo("PENDING");
        admin();assertThatThrownBy(()->commerce.review("products",productId,Map.of("decision","APPROVED","revision",1))).hasMessageContaining("刷新");
    }
    boolean extraProductUnavailable(long id){try{extra.product(id);return false;}catch(BusinessException e){return true;}}
    @Test void merchantCannotReadOrModifyAnotherMerchantsProductsCampaignsOrLogistics(){
        var p=commerce.saveProduct(null,product());approve("products",p);var c=commerce.saveCampaign(null,campaign(id(p)));
        merchant(other,otherCode);assertThat(commerce.list("products",false,"",1,12).get("total")).isEqualTo(0L);
        assertThatThrownBy(()->commerce.saveProduct(id(p),product())).hasMessageContaining("不属于");assertThatThrownBy(()->commerce.saveCampaign(null,campaign(id(p)))).hasMessageContaining("不属于");assertThatThrownBy(()->commerce.withdraw("campaigns",id(c))).hasMessageContaining("不属于");
        assertThatThrownBy(()->commerce.ship(-123L,Map.of("logisticsInfo","伪造发货"))).hasMessageContaining("不属于");assertThatThrownBy(AuthContext::requireUserId).hasMessageContaining("不支持");assertThatThrownBy(()->commerce.reviewCounts()).hasMessageContaining("管理员");
    }
    @Test void rejectedContentNeedsReasonAndUnsafeImageOrEmptyDetailsAreRejected(){
        var unsafe=product();unsafe.put("imageUrl","https://example.invalid/picture.png");assertThatThrownBy(()->commerce.saveProduct(null,unsafe)).hasMessageContaining("有效");
        var empty=product();empty.put("blocks","[]");assertThatThrownBy(()->commerce.saveProduct(null,empty)).hasMessageContaining("图文");
        var p=commerce.saveProduct(null,product());admin();assertThatThrownBy(()->commerce.review("products",id(p),Map.of("decision","REJECTED","revision",1))).hasMessageContaining("原因");
        commerce.review("products",id(p),Map.of("decision","REJECTED","revision",1,"reason","请补充清晰的封面图"));
        assertThat(db.queryForObject("SELECT review_reason FROM advertisement WHERE ad_id=?",String.class,id(p))).contains("封面");
    }
    @Test void orderKeepsMerchantPriceAndOriginSnapshotsAndSurvivesWithdrawal(){
        var p=commerce.saveProduct(null,product());approve("products",p);long productId=id(p);
        long buyer=8_000_000_000_000L+new Random().nextInt(1000000000);db.update("INSERT INTO student(student_id,nickname,password) VALUES(?,'商家订单测试','disabled-test')",buyer);
        AuthContext.set(new AuthUser(buyer,""+buyer,UserRole.STUDENT));db.update("INSERT INTO user_extra(owner,recipient,phone,address) VALUES(?,'测试收件人','13000000000','测试收件地址')",CommunityService.owner());
        var req=new OrderCreateRequest();req.setAdId(productId);var result=shop.createOrder(buyer,req);long orderId=result.getOrder().getOrderId();
        assertThatThrownBy(()->shop.deleteMyOrder(buyer,orderId)).hasMessageContaining("仅可删除");
        assertThat(result.getOrder().getMerchantId()).isEqualTo(owner);assertThat(result.getOrder().getUnitPrice()).isEqualByComparingTo("29.90");assertThat(result.getOrder().getShipOrigin()).isEqualTo("杭州市");
        merchant(other,otherCode);assertThatThrownBy(()->commerce.ship(orderId,Map.of("logisticsInfo","错误商家"))).hasMessageContaining("不属于");assertThat(commerce.orders(1,12).get("total")).isEqualTo(0L);
        merchant(owner,code);commerce.ship(orderId,Map.of("logisticsInfo","顺丰 SF123"));commerce.withdraw("products",productId);assertThat(shop.getMyOrder(buyer,orderId).getOrder().getStatus()).isEqualTo(1);
        AuthContext.set(new AuthUser(buyer,""+buyer,UserRole.STUDENT));
        assertThatThrownBy(()->shop.createOrder(buyer,req)).hasMessageContaining("下架");
        shop.confirmReceived(buyer,orderId);var f=new OrderFeedbackRequest();f.setOrderId(orderId);f.setRating(5);f.setFeedbackContent("很好");shop.submitFeedback(buyer,f);merchant(owner,code);assertThat(commerce.evaluations(1,12).get("total")).isEqualTo(1L);
        commerce.deleteOrder(orderId);assertThat(commerce.orders(1,12).get("total")).isEqualTo(0L);assertThat(shop.getMyOrder(buyer,orderId)).isNotNull();
        shop.deleteMyOrder(buyer,orderId);assertThat(shop.listMyOrders(buyer)).isEmpty();assertThatThrownBy(()->shop.getMyOrder(buyer,orderId)).hasMessageContaining("已删除");
        assertThat(commerce.evaluations(1,12).get("total")).isEqualTo(1L);assertThat(db.queryForObject("SELECT COUNT(*) FROM order_record WHERE order_id=?",Long.class,orderId)).isEqualTo(1L);
    }

    @Test void banningMerchantBlocksLoginHidesStoreProductsAndCampaignsAndUnbanRequiresNewSession(){
        var p=commerce.saveProduct(null,product());approve("products",p);var c=commerce.saveCampaign(null,campaign(id(p)));approve("campaigns",c);
        var oldUser=TokenUtil.parseToken(login(code,"123456").getToken());
        admin();merchants.setBanned(owner,true,"测试违规经营");
        assertThatThrownBy(()->merchants.assertSession(oldUser)).hasMessageContaining("封禁");assertThatThrownBy(()->login(code,"123456")).hasMessageContaining("封禁");
        assertThat(shop.listActiveAds()).noneMatch(x->x.getAdId()==id(p));assertThat(commerce.publicCampaigns()).noneMatch(x->id(x)==id(c));assertThatThrownBy(()->commerce.storefront(owner)).hasMessageContaining("已被封禁");
        merchants.setBanned(owner,false,"");assertThatThrownBy(()->merchants.assertSession(oldUser)).hasMessageContaining("登录已失效");assertThat(merchants.assertSession(TokenUtil.parseToken(login(code,"123456").getToken()))).isFalse();assertThat(commerce.storefront(owner)).containsKey("products");
        assertThat(db.queryForObject("SELECT review_status FROM advertisement WHERE ad_id=?",String.class,id(p))).isEqualTo("WITHDRAWN");
        assertThat(db.queryForObject("SELECT review_status FROM merchant_campaign WHERE id=?",String.class,id(c))).isEqualTo("WITHDRAWN");
        assertThat(shop.listActiveAds()).noneMatch(x->x.getAdId()==id(p));assertThat(commerce.publicCampaigns()).noneMatch(x->id(x)==id(c));
    }
    @Test void storefrontAdRequiresOwnApprovedProductsAndStopsShowingWhenLastProductWithdrawn(){
        var ad=campaign(0);ad.put("targetType","MERCHANT");ad.remove("productId");
        assertThatThrownBy(()->commerce.saveCampaign(null,ad)).hasMessageContaining("至少上架");
        var p=commerce.saveProduct(null,product());approve("products",p);var c=commerce.saveCampaign(null,ad);assertThat(c.get("targetType")).isEqualTo("MERCHANT");assertThat(c.get("productId")).isNull();approve("campaigns",c);
        assertThat(commerce.publicCampaigns()).anyMatch(x->id(x)==id(c));assertThat((List<?>)commerce.storefront(owner).get("products")).hasSize(1);
        commerce.withdraw("products",id(p));assertThat(commerce.publicCampaigns()).noneMatch(x->id(x)==id(c));assertThat((List<?>)commerce.storefront(owner).get("products")).isEmpty();
    }
    @Test void merchantCanCustomizeAvatarAndCoverAndSearchFindsOnlyApprovedProducts(){
        assertThat(merchants.saveAvatar(Map.of("avatar",image)).get("avatar")).isEqualTo(image);assertThat(merchants.saveCover(Map.of("coverUrl",image)).get("coverUrl")).isEqualTo(image);
        assertThatThrownBy(()->merchants.saveAvatar(Map.of("avatar","https://example.invalid/avatar.png"))).hasMessageContaining("有效");
        var p=commerce.saveProduct(null,product());assertThat(shop.listActiveAds("校园帆布包")).noneMatch(x->x.getAdId()==id(p));approve("products",p);
        assertThat(shop.listActiveAds("帆布包")).anyMatch(x->x.getAdId()==id(p));assertThat(shop.listActiveAds("杭州市")).anyMatch(x->x.getAdId()==id(p));assertThat(shop.listActiveAds("%_'")).isEmpty();
        var store=(Map<?,?>)commerce.storefront(owner).get("merchant");assertThat(store.get("avatar")).isEqualTo(image);assertThat(store.get("coverUrl")).isEqualTo(image);assertThat(store.get("city")).isEqualTo("杭州市");
    }
    @SuppressWarnings("unchecked") @Test void allReviewListIncludesBothKindsAndKeepsStatusFilters(){
        var p=commerce.saveProduct(null,product());approve("products",p);var c=commerce.saveCampaign(null,campaign(id(p)));admin();
        var pending=(List<Map<String,Object>>)commerce.list("all",true,"PENDING",1,50).get("list");assertThat(pending).anyMatch(x->id(x)==id(c)&&"campaigns".equals(x.get("itemType")));assertThat(pending).allMatch(x->"PENDING".equals(x.get("reviewStatus")));
        var all=(List<Map<String,Object>>)commerce.list("all",true,"",1,50).get("list");assertThat(all).anyMatch(x->id(x)==id(p)&&"products".equals(x.get("itemType")));assertThat(all).anyMatch(x->id(x)==id(c)&&"campaigns".equals(x.get("itemType")));
    }

    @Test void administratorControlsOfficialCarouselAndIntervalWithConflictAndPermissionChecks(){
        admin();var initial=carousel.settings();int revision=((Number)initial.get("revision")).intValue();
        var hidden=carousel.saveSettings(Map.of("officialEnabled",false,"intervalSeconds",12,"revision",revision));
        assertThat(((Number)hidden.get("intervalSeconds")).intValue()).isEqualTo(12);
        assertThatThrownBy(()->carousel.saveSettings(Map.of("officialEnabled",true,"intervalSeconds",5,"revision",revision))).hasMessageContaining("已被修改");
        for(Object seconds:List.of(0,2,61,4.5,"invalid"))assertThatThrownBy(()->carousel.saveSettings(Map.of("officialEnabled",true,"intervalSeconds",seconds,"revision",revision+1))).isInstanceOf(BusinessException.class);
        AuthContext.set(new AuthUser(123L,"123",UserRole.STUDENT));
        var feed=carousel.publicCarousel();assertThat(feed.get("officialEnabled").toString()).isIn("0","false");assertThat(feed).doesNotContainKey("revision");
        assertThatThrownBy(()->carousel.saveSettings(Map.of("officialEnabled",true,"intervalSeconds",5,"revision",revision+1))).hasMessageContaining("管理员");
        assertThatThrownBy(()->carousel.settings()).hasMessageContaining("管理员");
        merchant(owner,code);assertThatThrownBy(()->carousel.settings()).hasMessageContaining("管理员");
        admin();var restored=carousel.saveSettings(Map.of("officialEnabled",true,"intervalSeconds",3,"revision",revision+1));
        assertThat(restored.get("officialEnabled").toString()).isIn("1","true");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM home_carousel_config WHERE id=1",Integer.class)).isEqualTo(1);
    }
    @Test void campaignSelectionRequiresApprovalAndCanBeRemovedWithoutDeletingContent(){
        var p=commerce.saveProduct(null,product());approve("products",p);var c=commerce.saveCampaign(null,campaign(id(p)));long cid=id(c);
        admin();assertThatThrownBy(()->carousel.selectCampaign(cid,Map.of("enabled",true,"revision",1))).hasMessageContaining("已通过审核");
        commerce.review("campaigns",cid,Map.of("decision","APPROVED","revision",1));
        assertThat(commerce.publicCampaigns()).noneMatch(r->id(r)==cid);
        carousel.selectCampaign(cid,Map.of("enabled",true,"revision",2));assertThat(commerce.publicCampaigns()).anyMatch(r->id(r)==cid);
        assertThatThrownBy(()->carousel.selectCampaign(cid,Map.of("enabled",false,"revision",2))).hasMessageContaining("刷新");
        carousel.selectCampaign(cid,Map.of("enabled",false,"revision",3));assertThat(commerce.publicCampaigns()).noneMatch(r->id(r)==cid);
        assertThat(db.queryForObject("SELECT review_status FROM merchant_campaign WHERE id=?",String.class,cid)).isEqualTo("APPROVED");
        merchant(owner,code);assertThatThrownBy(()->carousel.selectCampaign(cid,Map.of("enabled",true,"revision",4))).hasMessageContaining("管理员");
        commerce.withdraw("products",id(p));admin();assertThatThrownBy(()->carousel.selectCampaign(cid,Map.of("enabled",true,"revision",4))).hasMessageContaining("下架");
    }
    @Test void supportsAllBanDurationsAndExpiresWithoutRepublishingGoodsOrRemovingOrders(){
        var p=commerce.saveProduct(null,product());approve("products",p);var c=commerce.saveCampaign(null,campaign(id(p)));approve("campaigns",c);
        long orderId=-9_000_000_000L-owner;db.update("INSERT INTO order_record(order_id,ad_id,buyer_id,merchant_id,status) VALUES(?,?,1,?,0)",orderId,id(p),owner);
        admin();
        for(String duration:List.of("DAY_1","DAY_3","WEEK_2","MONTH_1","MONTH_3","YEAR_10","PERMANENT")){
            merchants.setBanned(owner,true,"限时封禁测试",duration);
            var row=db.queryForMap("SELECT banned_at,ban_until,ban_duration FROM merchant WHERE merchant_id=?",owner);
            assertThat(row.get("ban_duration")).isEqualTo(duration);
            var start=db.queryForObject("SELECT banned_at FROM merchant WHERE merchant_id=?",java.time.LocalDateTime.class,owner);
            if("PERMANENT".equals(duration))assertThat(row.get("ban_until")).isNull();
            else {
                var expected=switch(duration){case "DAY_1"->start.plusDays(1);case "DAY_3"->start.plusDays(3);case "WEEK_2"->start.plusWeeks(2);case "MONTH_1"->start.plusMonths(1);case "MONTH_3"->start.plusMonths(3);default->start.plusYears(10);};
                assertThat(db.queryForObject("SELECT ban_until FROM merchant WHERE merchant_id=?",java.time.LocalDateTime.class,owner)).isEqualTo(expected);
            }
        }
        assertThatThrownBy(()->merchants.setBanned(owner,true,"无效","DAY_999")).hasMessageContaining("有效的封禁时长");
        merchants.setBanned(owner,true,"到期测试","DAY_1");db.update("UPDATE merchant SET ban_until=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE merchant_id=?",owner);merchants.releaseExpiredBans();
        assertThat(db.queryForObject("SELECT is_banned FROM merchant WHERE merchant_id=?",Integer.class,owner)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM order_record WHERE order_id=?",Long.class,orderId)).isEqualTo(1L);
        assertThat(db.queryForObject("SELECT review_status FROM advertisement WHERE ad_id=?",String.class,id(p))).isEqualTo("WITHDRAWN");
        assertThat(db.queryForObject("SELECT review_status FROM merchant_campaign WHERE id=?",String.class,id(c))).isEqualTo("WITHDRAWN");
        assertThat(merchants.assertSession(TokenUtil.parseToken(login(code,"123456").getToken()))).isFalse();
    }
}
