package com.example.bishe;
import com.example.bishe.common.*;
import com.example.bishe.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** 显式启用后验证真实 MySQL；每个测试事务回滚，不保留测试帖/订单/资料。 */
@SpringBootTest(properties="app.token.secret=test-token-secret-with-at-least-32-characters")
@EnabledIfEnvironmentVariable(named="RUN_DB_INTEGRATION",matches="true")
@Transactional
class CommunityIntegrationTests {
 @Autowired CommunityService service;
 @Autowired ProfileShopService extra;
 @Autowired JdbcTemplate db;
 @Autowired org.springframework.web.context.WebApplicationContext web;
 private final long userId=7_000_000_000_000L+java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000_000L);
 private final String subject=Long.toString(userId);
 @BeforeEach void login(){db.update("INSERT INTO student(student_id,nickname,password) VALUES(?,?,'disabled-test-account')",userId,subject);AuthContext.set(new AuthUser(userId,subject,UserRole.STUDENT));}
 private void asAdmin(Runnable action){var current=AuthContext.get();AuthContext.set(new AuthUser(null,"test-reviewer",UserRole.ADMIN));try{action.run();}finally{AuthContext.set(current);}}
 @AfterEach void clear(){AuthContext.clear();}
 private Map<String,Object> body(String title){return Map.of("title",title,"blocks","[{\"type\":\"text\",\"value\":\"正文\"}]");}
 @Test void viewsAreUniquePerAccountPerDayAndEditPreservesPublishTime(){
  long id=service.save(null,body("每日浏览"));var before=service.detail(id);service.detail(id);
  assertThat(service.detail(id).get("views")).isEqualTo(1L);
  service.save(id,body("修改标题"));assertThat(service.detail(id).get("created_at")).isEqualTo(before.get("created_at"));
 }
 @Test void rejectsBadPostsAndUnsafeImages(){
  asAdmin(()->service.addWord(Map.of("word","测试违禁词")));
  long masked=service.save(null,body("测试违禁词"));assertThat(service.detail(masked).get("title")).isEqualTo("*****");
  assertThatThrownBy(()->service.save(null,Map.of("title","图片","blocks","[{\"type\":\"image\",\"value\":\"javascript:alert(1)\"}]"))).isInstanceOf(BusinessException.class);
 }
 @Test void titleIsRequiredButBodyIsOptional(){
  long id=service.save(null,Map.of("title","只有标题也可以发布","blocks","[]"));
  assertThat(service.detail(id).get("blocks")).isEqualTo("[]");
  assertThat(service.detail(id).get("author_id")).isEqualTo(subject);
  assertThatThrownBy(()->service.save(null,Map.of("title"," ","blocks","[]"))).isInstanceOf(BusinessException.class).hasMessageContaining("标题");
 }
 @Test void replyLikesToggleAndDeletedParentDoesNotExposeOriginalContent(){
  long id=service.save(null,body("回复点赞与楼中楼"));service.reply(id,Map.of("content","原回复正文"));
  var first=(Map<String,Object>)((List<?>)service.detail(id).get("replies")).get(0);long rid=((Number)first.get("id")).longValue();
  service.reply(id,Map.of("content","楼中楼内容","parentId",rid));
  service.likeReply(rid);service.likeReply(rid);assertThat(db.queryForObject("SELECT COUNT(*) FROM community_reply_like WHERE reply_id=?",Long.class,rid)).isZero();
  service.likeReply(rid);var liked=(Map<String,Object>)((List<?>)service.detail(id).get("replies")).get(0);assertThat(((Number)liked.get("likes")).longValue()).isEqualTo(1L);
  service.deleteReply(rid);var deleted=(Map<String,Object>)((List<?>)service.detail(id).get("replies")).get(0);assertThat(deleted.get("content")).isEqualTo("该回复已删除");
  assertThatThrownBy(()->service.likeReply(rid)).isInstanceOf(BusinessException.class);
 }
 @Test void notificationHasMoreIsExact(){
  assertThat(service.noticePage(1).get("hasMore")).isEqualTo(false);
  for(int i=0;i<30;i++)db.update("INSERT INTO community_notice(owner,kind,message) VALUES(?,'LIKE','测试通知')",CommunityService.owner());
  assertThat(service.noticePage(1).get("hasMore")).isEqualTo(false);
  db.update("INSERT INTO community_notice(owner,kind,message) VALUES(?,'LIKE','第31条通知')",CommunityService.owner());
  assertThat(service.noticePage(1).get("hasMore")).isEqualTo(true);
  assertThat((List<?>)service.noticePage(1).get("list")).hasSize(30);
  assertThat((List<?>)service.noticePage(2).get("list")).hasSize(1);
  assertThat(service.noticePage(2).get("hasMore")).isEqualTo(false);
 }
 @Test void feedSupportsSearchFeaturedAndMine(){
  String title="检索"+UUID.randomUUID();long id=service.save(null,body(title));
  asAdmin(()->{service.action(id,"feature");service.action(id,"pin");});
  var feed=service.feed(title,"featured","community",1);
  assertThat(((Number)feed.get("total")).longValue()).isEqualTo(1L);
  assertThat((List<?>)feed.get("list")).hasSize(1);
  assertThat(((Number)service.feed(title,"hot","mine",1).get("total")).longValue()).isEqualTo(1L);
 }
 @Test void httpResponsesSerializePostTimestampsAndProfileDates() throws Exception {
  long id=service.save(null,body("接口序列化"));
  extra.saveProfile(Map.of("gender","保密","birth_date","2000-01-01"));
  String token=com.example.bishe.util.TokenUtil.createToken(AuthContext.get());
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).build();
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/community/posts/"+id).header("Authorization","Bearer "+token))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.created_at").exists());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/extra/profile").header("Authorization","Bearer "+token))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.birth_date").exists());
 }
 @Test void fourthMaskedReplyMutesOnlyCommunityAndAdminCanUnmute(){
  long id=service.save(null,body("回复检测"));asAdmin(()->service.addWord(Map.of("word","测试违禁词")));
  for(int i=0;i<4;i++)service.reply(id,Map.of("content","测试违禁词"));
  var replies=(List<Map<String,Object>>)service.detail(id).get("replies");assertThat(replies).hasSize(4);assertThat(replies.get(0).get("content")).isEqualTo("*****");
  assertThatThrownBy(()->service.reply(id,Map.of("content","普通回复"))).isInstanceOf(BusinessException.class).hasMessageContaining("禁言");
  assertThat(service.notices(1)).anyMatch(n->"WARNING".equals(n.get("kind")));
  String owner=CommunityService.owner();asAdmin(()->service.unmute(owner));service.reply(id,Map.of("content","解禁成功"));
 }
 @Test void forcedHiddenAndDeletedPostsCannotBeRestored(){
  long id=service.save(null,body("下架测试"));asAdmin(()->service.action(id,"forceHide"));
  assertThatThrownBy(()->service.action(id,"restore")).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->service.save(id,body("再次发布"))).isInstanceOf(BusinessException.class);
  long deleted=service.save(null,body("删除测试"));service.action(deleted,"delete");
  assertThatThrownBy(()->service.action(deleted,"restore")).isInstanceOf(BusinessException.class);
 }
 @Test void rejectsUnauthorizedEditAndDeletingOthersReplies(){
  long id=service.save(null,body("权限测试"));service.reply(id,Map.of("content","作者回复"));
  long reply=((Number)((List<Map<String,Object>>)service.detail(id).get("replies")).get(0).get("id")).longValue();
  AuthContext.set(new AuthUser(123L,"123",UserRole.STUDENT));
  assertThatThrownBy(()->service.save(id,body("冒充编辑"))).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->service.deleteReply(reply)).isInstanceOf(BusinessException.class);
 }
 @Test void profileValidatesBirthdayAndRequiresCompleteAddress(){
  assertThatThrownBy(()->extra.shipping(1L)).isInstanceOf(BusinessException.class).hasMessageContaining("收货人");
  assertThatThrownBy(()->extra.saveProfile(Map.of("gender","男","birth_date","2999-01-01"))).isInstanceOf(BusinessException.class);
  extra.saveProfile(Map.of("gender","女","birth_date","2000-01-01","school","测试学校","education","本科","recipient","测试收件人","phone","13800000000","address","测试地址"));
  assertThat(extra.shipping(1L)).contains("测试地址");assertThat(extra.profile().get("school")).isEqualTo("测试学校");
 }
 @Test void autoReviewRunsOnlyAfterSevenDaysAndIsIdempotent(){
  long ad=-910000001L,order=-910000002L;
  db.update("INSERT INTO advertisement(ad_id,title,content,price,publisher_id) VALUES(?,'测试商品','测试',1,'admin-test')",ad);
  db.update("INSERT INTO order_record(order_id,ad_id,buyer_id,status,is_received,received_time) VALUES(?,?,1,2,1,DATE_SUB(NOW(),INTERVAL 8 DAY))",order,ad);
  extra.autoReview();extra.autoReview();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM order_evaluation WHERE order_id=?",Long.class,order)).isEqualTo(1L);
  assertThat(db.queryForObject("SELECT rating FROM order_evaluation WHERE order_id=?",Integer.class,order)).isEqualTo(5);
  long fresh=-910000003L;db.update("INSERT INTO order_record(order_id,ad_id,buyer_id,status,is_received,received_time) VALUES(?,?,1,2,1,NOW())",fresh,ad);extra.autoReview();
  assertThat(db.queryForObject("SELECT COUNT(*) FROM order_evaluation WHERE order_id=?",Long.class,fresh)).isZero();
 }
}
