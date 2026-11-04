package com.example.bishe.service;
import com.example.bishe.common.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;
@Service
public class ProfileShopService {
 private final JdbcTemplate db; private final CommunityService community;
 public ProfileShopService(JdbcTemplate db,CommunityService community){this.db=db;this.community=community;}
 public Map<String,Object> profile(){db.update("INSERT IGNORE INTO user_extra(owner) VALUES(?)",CommunityService.owner());return db.queryForMap("SELECT * FROM user_extra WHERE owner=?",CommunityService.owner());}
 public void saveProfile(Map<String,Object>b){
  profile();String gender=community.text(b,"gender",12),birth=community.text(b,"birth_date",10);
  if(!Set.of("男","女","保密").contains(gender))throw new BusinessException("性别请选择男、女或保密");
  if(!birth.isBlank()){try{if(LocalDate.parse(birth).isAfter(LocalDate.now()))throw new IllegalArgumentException();}catch(Exception e){throw new BusinessException("出生日期无效");}}
  db.update("UPDATE user_extra SET gender=?,birth_date=?,school=?,education=?,recipient=?,phone=?,address=? WHERE owner=?",gender,birth.isBlank()?null:birth,community.text(b,"school",120),community.text(b,"education",40),community.text(b,"recipient",60),community.text(b,"phone",30),community.text(b,"address",500),CommunityService.owner());
 }
 public String shipping(Long buyer){
  var rows=db.queryForList("SELECT * FROM user_extra WHERE owner=?",CommunityService.owner());
  if(rows.isEmpty() || rows.get(0).get("recipient").toString().isBlank() || rows.get(0).get("phone").toString().isBlank() || rows.get(0).get("address").toString().isBlank())throw new BusinessException(409,"请先在个人设置填写收货人、联系电话和详细地址",Map.of("type","ADDRESS_REQUIRED"));
  var r=rows.get(0);return r.get("recipient")+" | "+r.get("phone")+" | "+r.get("address");
 }
 public Map<String,Object> product(long id){
  var rows=db.queryForList("SELECT p.*,m.name AS merchant_name,m.city AS merchant_city,m.avatar AS merchant_avatar FROM advertisement p JOIN merchant m ON m.merchant_id=p.merchant_id WHERE p.ad_id=? AND p.is_deleted=0 AND p.review_status='APPROVED' AND m.is_banned=0",id);if(rows.isEmpty())throw new BusinessException(404,"商品未上架或不存在");var r=rows.get(0);
  var detail=db.queryForList("SELECT * FROM product_detail WHERE ad_id=?",id);r.put("detail",detail.isEmpty()?Map.of("origin","待商家补充","blocks","[]"):detail.get(0));
  r.put("sales",db.queryForObject("SELECT COUNT(*) FROM order_record WHERE ad_id=?",Long.class,id));
  r.put("reviews",db.queryForList("SELECT e.rating,e.feedback_content,e.publish_time FROM order_evaluation e JOIN order_record o ON o.order_id=e.order_id WHERE o.ad_id=? ORDER BY e.publish_time DESC LIMIT 200",id));return r;
 }
 public void productSave(long id,Map<String,Object>b){AuthContext.requireAdmin();throw new BusinessException(410,"商品详情由所属商家提交，管理员负责审核");}
 @Scheduled(fixedDelay=60000,initialDelay=60000)
 @Transactional
 public void autoReview(){
  // 与手工评价共用订单行锁，避免重复插入。历史无收货时间的订单不推算。
  var orders=db.queryForList("SELECT order_id,ad_id,buyer_id FROM order_record WHERE status=2 AND received_time<=DATE_SUB(NOW(),INTERVAL 7 DAY) AND NOT EXISTS(SELECT 1 FROM order_evaluation e WHERE e.order_id=order_record.order_id) LIMIT 100 FOR UPDATE");
  for(var o:orders){db.update("INSERT INTO order_evaluation(order_id,buyer_id,rating,feedback_content) SELECT ?,?,5,'系统默认好评（确认收货7天未评价）' WHERE NOT EXISTS(SELECT 1 FROM order_evaluation WHERE order_id=?)",o.get("order_id"),o.get("buyer_id"),o.get("order_id"));db.update("UPDATE advertisement SET score=(SELECT COALESCE(AVG(e.rating),0) FROM order_evaluation e JOIN order_record r ON r.order_id=e.order_id WHERE r.ad_id=?) WHERE ad_id=?",o.get("ad_id"),o.get("ad_id"));}
 }
 public void lockOrder(long id){db.queryForList("SELECT order_id FROM order_record WHERE order_id=? FOR UPDATE",id);}
 public void snapshot(long id,String address){db.update("UPDATE order_record SET shipping_snapshot=? WHERE order_id=?",address,id);}
}
