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
class CommunityEnhancementIntegrationTests {
 @Autowired CommunityService community;
 @Autowired AdminService adminService;
 @Autowired CommunitySafetyService safety;
 @Autowired CommunityDisciplineService discipline;
 @Autowired UnreadService unread;
 @Autowired AdminOperationService operations;
 @Autowired JdbcTemplate db;
 final String marker="增强测试"+UUID.randomUUID();
 long a,b;
 long user(){long id=5_000_000_000_000L+java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000_000L);db.update("INSERT INTO student(student_id,nickname,password) VALUES(?,?,'disabled-test-account')",id,marker);return id;}
 void login(long id){AuthContext.set(new AuthUser(id,Long.toString(id),UserRole.STUDENT));}
 void admin(){AuthContext.set(new AuthUser(null,"enhancement-reviewer",UserRole.ADMIN));}
 String target(){return "STUDENT:"+a;}
 @BeforeEach void setup(){a=user();b=user();login(a);}
 @AfterEach void clear(){AuthContext.clear();}
 long post(){return community.save(null,Map.of("title",marker,"blocks","[]"));}
 List<Map<String,Object>> rows(Map<String,Object> result){return (List<Map<String,Object>>)result.get("list");}
 long report(long post){login(b);safety.report(post,Map.of("reason","其他","details",marker));return db.queryForObject("SELECT id FROM community_report WHERE post_id=? AND reporter=?",Long.class,post,"STUDENT:"+b);}
 @Test void feedLoadsFiveThenTenWithoutOverlapForAllRoles(){
  for(int i=0;i<26;i++)post();
  for(String role:List.of(UserRole.STUDENT,UserRole.TEACHER,UserRole.ADMIN)){
   AuthContext.set(new AuthUser(a,Long.toString(a),role));String scope=role.equals(UserRole.ADMIN)?"admin":"community";
   Set<Object> ids=new HashSet<>();int offset=0;
   for(int count:List.of(5,10,10,1)){var batch=rows(community.feed(marker,"latest",scope,1,offset,offset==0?5:10));assertThat(batch).hasSize(count);for(var p:batch)assertThat(ids.add(p.get("id"))).isTrue();offset+=count;}
   assertThat(ids).hasSize(26);
  }
 }
 @Test void unmuteAndClearAreIndependentAndAllResolvesRole(){
  admin();community.moderateUser(Map.of("owner","ALL:"+a,"action","WARN","reason",marker));community.moderateUser(Map.of("owner",target(),"action","MUTE","reason",marker));
  community.unmute(target());assertThat(db.queryForObject("SELECT warnings FROM community_user WHERE owner=?",Integer.class,target())).isEqualTo(1);
  community.moderateUser(Map.of("owner",target(),"action","MUTE","reason",marker));community.clearWarnings(target());
  assertThat(db.queryForObject("SELECT warnings FROM community_user WHERE owner=?",Integer.class,target())).isZero();assertThat(db.queryForObject("SELECT muted_until>NOW() FROM community_user WHERE owner=?",Boolean.class,target())).isTrue();
  assertThat(rows(community.mutedPage("ALL",1,5))).anyMatch(u->target().equals(u.get("owner")));
  assertThat(rows(community.mutedPage("TEACHER",1,20))).noneMatch(u->target().equals(u.get("owner")));
 }
 @Test void warningDecaySettlesFullDaysOnlyAndKeepsMute(){
  db.update("INSERT INTO community_user(owner,warnings,warning_decay_at,muted_until) VALUES(?,4,DATE_SUB(NOW(),INTERVAL 49 HOUR),DATE_ADD(NOW(),INTERVAL 1 DAY))",target());
  discipline.decay();discipline.decay();assertThat(db.queryForObject("SELECT warnings FROM community_user WHERE owner=?",Integer.class,target())).isEqualTo(2);
  assertThat(db.queryForObject("SELECT muted_until>NOW() FROM community_user WHERE owner=?",Boolean.class,target())).isTrue();
  db.update("UPDATE community_user SET warnings=0,muted_until=NULL,warning_decay_at=DATE_SUB(NOW(),INTERVAL 23 HOUR) WHERE owner=?",target());admin();community.moderateUser(Map.of("owner",target(),"action","WARN","reason",marker));
  assertThat(db.queryForObject("SELECT TIMESTAMPDIFF(MINUTE,warning_decay_at,NOW()) FROM community_user WHERE owner=?",Integer.class,target())).isZero();
 }
 @Test void durationsIncludeCustomAndPermanentAndRejectInvalid(){
  admin();for(String minutes:List.of("60","120","300","720","1440","4320","20160","37","PERMANENT")){
   var until=discipline.mute(target(),minutes,marker,null,"");assertThat(until).isAfter(java.time.LocalDateTime.now());
  }
  assertThatThrownBy(()->discipline.mute(target(),"0",marker,null,"")).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->discipline.mute(target(),"5256001",marker,null,"")).isInstanceOf(BusinessException.class);
 }
 @Test void reportMuteAppealRestoresHiddenPostAndNotifiesWithOwnershipChecks(){
  long post=post(),report=report(post);admin();safety.resolve(report,Map.of("decision","hide","feedback",marker,"mute","true","muteMinutes","120"));
  assertThat(db.queryForObject("SELECT status FROM community_post WHERE id=?",String.class,post)).isEqualTo("FORCED");
  login(a);assertThatThrownBy(()->safety.appeal(report,"短申诉")).isInstanceOf(BusinessException.class);safety.appeal(report,"申诉依据".repeat(30));
  long appeal=((Number)rows(safety.appealPage(false,1,20)).get(0).get("id")).longValue();
  login(b);assertThat(rows(safety.appealPage(false,1,20))).isEmpty();assertThatThrownBy(()->safety.resolveAppeal(appeal,Map.of("decision","APPROVED","feedback",marker))).isInstanceOf(BusinessException.class);
  admin();safety.resolveAppeal(appeal,Map.of("decision","APPROVED","feedback",marker));
  assertThat(db.queryForObject("SELECT status FROM community_post WHERE id=?",String.class,post)).isEqualTo("PUBLIC");assertThat(db.queryForObject("SELECT muted_until FROM community_user WHERE owner=?",java.sql.Timestamp.class,target())).isNull();
  login(a);assertThat(community.notices(1)).anyMatch(n->"APPEAL_RESULT".equals(n.get("kind")));
 }
 @Test void directlyHiddenPostCanAlsoBeAppealed(){
  long post=post();admin();community.action(post,"forceHide");login(a);
  var entry=safety.appealable().stream().filter(r->r.get("operation_id")!=null&&((Number)r.get("post_id")).longValue()==post).findFirst().orElseThrow();
  safety.appealOperation(((Number)entry.get("operation_id")).longValue(),"直接处置申诉".repeat(20));
  long appeal=((Number)rows(safety.appealPage(false,1,20)).get(0).get("id")).longValue();
  admin();safety.resolveAppeal(appeal,Map.of("decision","APPROVED","feedback",marker));assertThat(db.queryForObject("SELECT status FROM community_post WHERE id=?",String.class,post)).isEqualTo("PUBLIC");
 }
 @Test void approvedAppealDoesNotUndoLaterMuteOrRestoreDeletedPost(){
  long post=post(),report=report(post);admin();safety.resolve(report,Map.of("decision","delete","feedback",marker,"mute","true","muteMinutes","60"));
  discipline.mute(target(),"4320",marker,null,"");login(a);safety.appeal(report,"申诉依据".repeat(30));long appeal=((Number)rows(safety.appealPage(false,1,20)).get(0).get("id")).longValue();
  admin();safety.resolveAppeal(appeal,Map.of("decision","APPROVED","feedback",marker));assertThat(db.queryForObject("SELECT status FROM community_post WHERE id=?",String.class,post)).isEqualTo("DELETED");assertThat(db.queryForObject("SELECT muted_until>NOW() FROM community_user WHERE owner=?",Boolean.class,target())).isTrue();
 }
 long message(long sender,long receiver,int privateMessage,long group){db.update("INSERT INTO chat_record(sender_id,receiver_id,is_private,group_id,content,message_type) VALUES(?,?,?,?,?,0)",sender,receiver,privateMessage,group,marker);return db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);}
 long sum(Object rows){return ((List<Map<String,Object>>)rows).stream().mapToLong(r->((Number)r.get("unread")).longValue()).sum();}
 @Test void unreadCountsAddPrivateGroupsAndRequestsAndBoundReadCursor(){
  db.update("INSERT INTO friend(friend_a_id,friend_b_id) VALUES(?,?)",Math.min(a,b),Math.max(a,b));
  long first=message(b,a,1,0),second=message(b,a,1,0);message(b,a,1,0);
  db.update("INSERT INTO group_chat(group_id,group_name,total_people) VALUES(?,?,2)",a,marker);db.update("INSERT INTO group_user(group_id,user_id) VALUES(?,?)",a,a);
  message(b,0,0,a);message(b,0,0,a);message(a,0,0,a);
  for(int i=0;i<3;i++)db.update("INSERT INTO friend_request(requester_id,receiver_id,status) VALUES(?,?,0)",user(),a);
  var summary=unread.summary();assertThat(sum(summary.get("private"))+sum(summary.get("groups"))+((Number)summary.get("requests")).longValue()).isEqualTo(8);
  unread.read("private",b,second);assertThat(sum(unread.summary().get("private"))).isEqualTo(1);unread.read("private",b,first);assertThat(sum(unread.summary().get("private"))).isEqualTo(1);
  unread.read("private",b,Long.MAX_VALUE);message(b,a,1,0);assertThat(sum(unread.summary().get("private"))).isEqualTo(1);
  unread.read("group",a,Long.MAX_VALUE);assertThat(sum(unread.summary().get("groups"))).isZero();login(b);assertThatThrownBy(()->unread.read("group",a,Long.MAX_VALUE)).isInstanceOf(BusinessException.class);
 }
 @Test void courseDeletionLeavesAnAuditSnapshot(){
  db.update("INSERT INTO teacher(teacher_id,name,password,course_count) VALUES(?,?,'disabled',1)",a,marker);
  db.update("INSERT INTO course(teacher_id,course_name) VALUES(?,?)",a,marker);long id=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
  admin();adminService.deleteCourse(id);assertThat(db.queryForObject("SELECT COUNT(*) FROM course WHERE course_id=?",Long.class,id)).isZero();
  assertThat(rows(operations.list(marker,1))).anyMatch(r->"删除课程".equals(r.get("action"))&&Long.toString(id).equals(r.get("target_id")));
 }
 @Test void adminPagesHonorSizesAndRejectUnsupportedSize(){
  admin();for(int i=0;i<26;i++)operations.record("分页测试","USER",i,target(),null,"",marker);
  for(int size:List.of(5,10,20,50)){var page=operations.list(marker,1,size);assertThat(((Number)page.get("total")).longValue()).isEqualTo(26);assertThat(rows(page)).hasSize(Math.min(size,26));}
  assertThat(rows(operations.list(marker,2,20))).hasSize(6);assertThatThrownBy(()->operations.list(marker,1,100)).isInstanceOf(BusinessException.class);
 }
}
