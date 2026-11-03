package com.example.bishe;

import com.example.bishe.common.*;
import com.example.bishe.dto.*;
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
class CommunityManagementIntegrationTests {
 @Autowired CommunityService community;
 @Autowired CommunityShareService sharing;
 @Autowired ChatService chat;
 @Autowired GroupService groups;
 @Autowired ModerationService moderation;
 @Autowired AdminOperationService operations;
 @Autowired JdbcTemplate db;
 final String marker="管理测试"+UUID.randomUUID();
 long a,b,c;
 long user(){long id=6_000_000_000_000L+java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000_000L);db.update("INSERT INTO student(student_id,nickname,password) VALUES(?,?,'disabled-test-account')",id,marker);return id;}
 void login(long id){AuthContext.set(new AuthUser(id,Long.toString(id),UserRole.STUDENT));}
 void admin(String id){AuthContext.set(new AuthUser(null,id,UserRole.ADMIN));}
 void friend(long x,long y){db.update("INSERT INTO friend(friend_a_id,friend_b_id) VALUES(?,?)",Math.min(x,y),Math.max(x,y));}
 long post(){return community.save(null,Map.of("title",marker,"blocks","[]"));}
 ChatSendRequest message(long receiver,String text){var request=new ChatSendRequest();request.setReceiverId(receiver);request.setContent(text);request.setMessageType(0);return request;}
 @BeforeEach void start(){a=user();b=user();c=user();login(a);}
 @AfterEach void clear(){AuthContext.clear();}
 @Test void administratorsCannotCreateReplyOrLike(){long id=post();community.reply(id,Map.of("content","回复"));long reply=db.queryForObject("SELECT id FROM community_reply WHERE post_id=?",Long.class,id);admin("reviewer");assertThatThrownBy(()->community.save(null,Map.of("title","管理员发帖","blocks","[]"))).isInstanceOf(BusinessException.class);assertThatThrownBy(()->community.action(id,"like")).isInstanceOf(BusinessException.class);assertThatThrownBy(()->community.likeReply(reply)).isInstanceOf(BusinessException.class);assertThatThrownBy(()->community.reply(id,Map.of("content","管理员回复"))).isInstanceOf(BusinessException.class);}
 @Test void sharedWordsMaskCommunityButKeepChatOriginalAndRespectEnabled(){
  String word="共用词"+UUID.randomUUID();admin("word-editor");var request=new SpecialWordRequest();request.setWord(word);var saved=moderation.addWord(request,"word-editor");
  assertThat(community.wordList()).anyMatch(w->word.equals(w.get("word")));
  login(a);long id=community.save(null,Map.of("title",word,"blocks","[{\"type\":\"text\",\"value\":\""+word+"\"}]"));
  assertThat(community.detail(id).get("title")).isEqualTo("*".repeat(word.length()));assertThat(community.detail(id).get("search_text")).isEqualTo("*".repeat(word.length())+"\n");
  community.reply(id,Map.of("content",word));assertThat(db.queryForObject("SELECT content FROM community_reply WHERE post_id=?",String.class,id)).isEqualTo("*".repeat(word.length()));
  friend(a,b);var sent=chat.sendPrivate(a,message(b,word));assertThat(sent.getContent()).isEqualTo(word);assertThat(db.queryForObject("SELECT COUNT(*) FROM chat_violation WHERE message_id=?",Long.class,sent.getMessageId())).isEqualTo(1);
  admin("word-editor");moderation.setWordEnabled(saved.getWordId(),0);login(a);community.reply(id,Map.of("content",word));assertThat(db.queryForObject("SELECT content FROM community_reply WHERE post_id=? ORDER BY id DESC LIMIT 1",String.class,id)).isEqualTo(word);
  admin("word-editor");community.deleteWord(saved.getWordId());assertThat(moderation.listWords()).noneMatch(w->word.equals(w.getWord()));
 }
 @Test void recentChatsComeBeforeNewFriendsAndBatchForwardDeduplicates(){
  friend(a,b);friend(a,c);db.update("UPDATE friend SET created_at='2020-01-01' WHERE friend_a_id=? AND friend_b_id=?",Math.min(a,b),Math.max(a,b));
  assertThat(sharing.targets().get(0).get("key")).isEqualTo("private:"+c);
  chat.sendPrivate(a,message(b,"最近聊天"));assertThat(sharing.targets().get(0).get("key")).isEqualTo("private:"+b);
  long id=post();assertThat(sharing.share(id,Map.of("targets",List.of("private:"+b,"private:"+c,"private:"+b)))).isEqualTo(2);
  assertThat(db.queryForObject("SELECT share_count FROM community_post WHERE id=?",Long.class,id)).isEqualTo(2);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM chat_record WHERE sender_id=? AND content LIKE ?",Long.class,a,"%/community/post/"+id)).isEqualTo(2);
 }
 @Test void invalidTargetsRejectWholeBatchAndGroupMembershipIsChecked(){
  friend(a,b);long id=post();assertThatThrownBy(()->sharing.share(id,Map.of("targets",List.of("private:"+b,"private:"+c)))).isInstanceOf(BusinessException.class);
  assertThat(db.queryForObject("SELECT share_count FROM community_post WHERE id=?",Long.class,id)).isZero();
  var request=new GroupCreateRequest();request.setGroupName(marker);long group=groups.createGroup(a,request).getGroupId();
  assertThat(sharing.share(id,Map.of("targets",List.of("private:"+b,"group:"+group)))).isEqualTo(2);
  login(c);assertThat(sharing.targets()).noneMatch(t->("group:"+group).equals(t.get("key")));assertThatThrownBy(()->sharing.share(id,Map.of("targets",List.of("group:"+group)))).isInstanceOf(BusinessException.class);
 }
 @Test void administratorsShareAuditHistoryWithPostAndUserSnapshots(){
  long id=post();community.reply(id,Map.of("content","需要处理的回复"));long reply=db.queryForObject("SELECT id FROM community_reply WHERE post_id=?",Long.class,id);
  admin("reviewer-one");community.action(id,"feature");community.action(id,"forceHide");community.deleteReply(reply);community.moderateUser(Map.of("owner","STUDENT:"+a,"postId",id,"action","MUTE","reason","重复广告"));
  admin("reviewer-two");community.unmute("STUDENT:"+a);community.action(id,"delete");
  var rows=(List<Map<String,Object>>)operations.list(marker,1).get("list");assertThat(rows).hasSize(5);assertThat(rows).allMatch(r->((Number)r.get("post_id")).longValue()==id);
  assertThat(rows).anyMatch(r->"ADMIN:reviewer-one".equals(r.get("operator")));assertThat(rows).anyMatch(r->"ADMIN:reviewer-two".equals(r.get("operator")));
  assertThat(db.queryForObject("SELECT COUNT(*) FROM admin_operation_log WHERE action='解除社区禁言' AND target_owner=?",Long.class,"STUDENT:"+a)).isEqualTo(1);
  var ban=new AccountBanRequest();ban.setAccountType("student");ban.setAccountId(b);ban.setBanReason("违规封禁");moderation.banAccount(ban,"reviewer-two");assertThat(db.queryForObject("SELECT COUNT(*) FROM admin_operation_log WHERE action='封禁账号' AND target_owner=?",Long.class,"STUDENT:"+b)).isEqualTo(1);
  login(a);assertThatThrownBy(()->operations.list("",1)).isInstanceOf(BusinessException.class);assertThatThrownBy(()->community.moderateUser(Map.of("owner","STUDENT:"+b,"action","MUTE","reason","无权"))).isInstanceOf(BusinessException.class);
 }
}
