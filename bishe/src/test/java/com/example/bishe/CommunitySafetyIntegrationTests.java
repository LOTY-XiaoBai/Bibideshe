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
@SpringBootTest(properties="app.token.secret=test-token-secret-with-at-least-32-characters")
@EnabledIfEnvironmentVariable(named="RUN_DB_INTEGRATION",matches="true")
@Transactional
class CommunitySafetyIntegrationTests {
 @Autowired CommunityService community;
 @Autowired CommunitySafetyService safety;
 @Autowired FriendService friends;
 @Autowired JdbcTemplate db;
 final String author="safety-author-"+UUID.randomUUID(),reader="safety-reader-"+UUID.randomUUID(),marker="测试"+UUID.randomUUID();
 void login(String s){AuthContext.set(new AuthUser(null,s,UserRole.ADMIN));}
 long post(){if(!UserRole.ADMIN.equals(AuthContext.get().getRole()))return community.save(null,Map.of("title",marker,"blocks","[]"));db.update("INSERT INTO community_post(owner,author,title,blocks,search_text) VALUES(?,?,?,'[]','')",CommunityService.owner(),author,marker);return db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);}
 @BeforeEach void start(){login(author);}
 @AfterEach void clear(){AuthContext.clear();}
 long total(String scope){return ((Number)community.feed(marker,"latest",scope,1).get("total")).longValue();}
 @Test void cannotReportOrBlockSelf(){long id=post();assertThatThrownBy(()->safety.report(id,Map.of("reason","其他"))).isInstanceOf(BusinessException.class);assertThatThrownBy(()->safety.block(id,"POST")).isInstanceOf(BusinessException.class);assertThatThrownBy(()->safety.block(id,"AUTHOR")).isInstanceOf(BusinessException.class);assertThat(community.detail(id).get("friend_status")).isEqualTo("SELF");}
 @Test void singlePostAndAuthorBlocksFilterAndCanBeUndone(){long a=post(),b=post();login(reader);safety.block(a,"POST");assertThat(total("community")).isEqualTo(1);long block=((Number)safety.blocks().get(0).get("id")).longValue();safety.unblock(block);assertThat(total("community")).isEqualTo(2);safety.block(a,"AUTHOR");assertThat(total("community")).isZero();login(author);long c=post();login(reader);assertThat(total("community")).isZero();safety.unblock(((Number)safety.blocks().get(0).get("id")).longValue());assertThat(total("community")).isEqualTo(3);}
 long socialUser(String role){
  long id=8_000_000_000_000L+java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000_000L);
  if(role.equals(UserRole.STUDENT))db.update("INSERT INTO student(student_id,nickname,password) VALUES(?,?,'disabled-test-account')",id,marker);
  else db.update("INSERT INTO teacher(teacher_id,name,password) VALUES(?,?,'disabled-test-account')",id,marker);
  return id;
 }
 void socialLogin(long id,String role){AuthContext.set(new AuthUser(id,Long.toString(id),role));}
 @Test void existingFriendRequestNeedsAcceptanceBeforeFriendFeedAndUsesBothPairOrders(){
  long a=socialUser(UserRole.STUDENT),b=socialUser(UserRole.STUDENT);
  socialLogin(b,UserRole.STUDENT);long id=post();
  socialLogin(a,UserRole.STUDENT);long otherPost=post();
  assertThat(community.detail(id).get("friend_status")).isEqualTo("NONE");
  var request=friends.sendRequest(a,b);
  assertThat(community.detail(id).get("friend_status")).isEqualTo("REQUEST_SENT");
  assertThat(total("friends")).isZero();
  assertThatThrownBy(()->friends.sendRequest(a,b)).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->friends.sendRequest(a,a)).isInstanceOf(BusinessException.class);
  socialLogin(b,UserRole.STUDENT);
  assertThat(community.detail(otherPost).get("friend_status")).isEqualTo("REQUEST_RECEIVED");
  assertThat(friends.listReceivedPending(b)).anyMatch(r->r.getRequestId().equals(request.getRequestId()));
  friends.acceptRequest(b,request.getRequestId());
  assertThat(community.detail(otherPost).get("friend_status")).isEqualTo("FRIEND");
  assertThat(total("friends")).isEqualTo(1);
  socialLogin(a,UserRole.STUDENT);
  assertThat(community.detail(id).get("friend_status")).isEqualTo("FRIEND");
  assertThat(total("friends")).isEqualTo(1);
  safety.block(id,"AUTHOR");assertThat(total("friends")).isZero();
  safety.unblock(((Number)safety.blocks().get(0).get("id")).longValue());
  friends.deleteFriend(a,b);assertThat(total("friends")).isZero();
  assertThat(community.detail(id).get("friend_status")).isEqualTo("NONE");
 }
 @Test void teacherStudentRequestCanBeRejectedAndAdministratorCannotBeFollowed(){
  long student=socialUser(UserRole.STUDENT),teacher=socialUser(UserRole.TEACHER);
  socialLogin(teacher,UserRole.TEACHER);long id=post();
  socialLogin(student,UserRole.STUDENT);var request=friends.sendRequest(student,teacher);
  socialLogin(teacher,UserRole.TEACHER);friends.rejectRequest(teacher,request.getRequestId());
  socialLogin(student,UserRole.STUDENT);
  assertThat(community.detail(id).get("friend_status")).isEqualTo("NONE");
  assertThat(total("friends")).isZero();
  assertThat(safety.friendStatus("ADMIN:"+author)).isEqualTo("UNAVAILABLE");
  login(reader);assertThat(community.detail(id).get("friend_status")).isEqualTo("UNAVAILABLE");
 }
 @Test void reportsReachAdminCannotDuplicateAndGenerateFeedback(){long id=post();login(reader);safety.report(id,Map.of("reason","广告骚扰","details","测试举报"));assertThatThrownBy(()->safety.report(id,Map.of("reason","其他"))).isInstanceOf(BusinessException.class);long report=db.queryForObject("SELECT id FROM community_report WHERE reporter=? AND post_id=?",Long.class,CommunityService.owner(),id);login("reviewer");assertThat((List<?>)safety.reports("PENDING",1).get("list")).anyMatch(r->((Number)((Map<?,?>)r).get("id")).longValue()==report);safety.resolve(report,Map.of("decision","hide","feedback","审核确认内容违规"));assertThat(db.queryForObject("SELECT status FROM community_post WHERE id=?",String.class,id)).isEqualTo("FORCED");login(reader);assertThat(community.notices(1)).anyMatch(n->"REPORT_RESULT".equals(n.get("kind")));assertThatThrownBy(()->safety.resolve(report,Map.of("decision","dismiss","feedback","重复处理"))).isInstanceOf(BusinessException.class);}
 @Test void nonAdminCannotReviewAndCannotUnblockOthers(){long id=post();login(reader);safety.block(id,"POST");long block=((Number)safety.blocks().get(0).get("id")).longValue();AuthContext.set(new AuthUser(123L,"123",UserRole.STUDENT));assertThatThrownBy(()->safety.reports("ALL",1)).isInstanceOf(BusinessException.class);assertThatThrownBy(()->safety.resolve(1,Map.of("decision","dismiss","feedback","无权限"))).isInstanceOf(BusinessException.class);assertThatThrownBy(()->safety.unblock(block)).isInstanceOf(BusinessException.class);}
 @Test void mineIgnoresFeaturedFilter(){post();post();assertThat(((Number)community.feed(marker,"featured","mine",1).get("total")).longValue()).isEqualTo(2);}
}
