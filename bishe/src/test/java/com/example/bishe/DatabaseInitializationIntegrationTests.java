package com.example.bishe;
import com.example.bishe.config.DatabaseInitializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties="app.token.secret=test-token-secret-with-at-least-32-characters")
@EnabledIfEnvironmentVariable(named="RUN_SCHEMA_INTEGRATION",matches="true")
class DatabaseInitializationIntegrationTests {
 @Autowired DataSource source;
 @Autowired Environment env;
 @Test void upgradesLegacyCoursesWithoutChangingMembersOrScores() throws Exception {
  String schema="codex_schema_test_"+UUID.randomUUID().toString().replace("-","");
  JdbcTemplate original=new JdbcTemplate(source);
  original.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");
  try {
   String url;try(var c=source.getConnection()){url=c.getMetaData().getURL().replaceFirst("^(jdbc:mysql://[^/]+/)[^?]*","$1"+schema);}
   if(!url.contains("/"+schema+"?"))throw new IllegalStateException("临时库URL校验失败");
   var isolated=new DriverManagerDataSource(url,env.getProperty("spring.datasource.username"),env.getProperty("spring.datasource.password"));
   try(var c=isolated.getConnection()){
    org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c,new org.springframework.core.io.support.EncodedResource(new org.springframework.core.io.ClassPathResource("database/base/01_schema.sql"),java.nio.charset.StandardCharsets.UTF_8));
   }
   var db=new JdbcTemplate(isolated);
   db.update("INSERT INTO teacher(teacher_id,name,password) VALUES(2,'旧教师','disabled')");
   db.update("INSERT INTO student(student_id,nickname,password) VALUES(1,'旧学生','disabled')");
   db.update("INSERT INTO course(course_id,teacher_id,course_name,is_finished) VALUES(1,2,'旧课程',0)");
   db.update("INSERT INTO course_personnel(course_id,student_id,student_score) VALUES(1,1,88.5)");
   var initializer=new DatabaseInitializer(isolated,true);initializer.afterPropertiesSet();
   assertThat(db.queryForObject("SELECT course_name FROM course WHERE course_id=1",String.class)).isEqualTo("旧课程");
   assertThat(db.queryForObject("SELECT student_score FROM course_personnel WHERE course_id=1 AND student_id=1",java.math.BigDecimal.class)).isEqualByComparingTo("88.5");
   assertThat(db.queryForObject("SELECT fixed_schedule+approval_required FROM course WHERE course_id=1",Integer.class)).isZero();
   assertThat(db.queryForObject("SELECT max_students FROM course WHERE course_id=1",Integer.class)).isNull();
   assertThat(db.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=? AND index_name='idx_checkin_student_status'",Integer.class,schema)).isEqualTo(3);
   // 人工重复执行新迁移也不能覆盖旧课程配置或报重复列/索引。
   db.update("UPDATE course SET approval_required=1,max_students=30 WHERE course_id=1");
   try(var c=isolated.getConnection()){
    for(String file:new String[]{"14_course_classroom.sql","15_course_query_indexes.sql"})org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c,new org.springframework.core.io.support.EncodedResource(new org.springframework.core.io.ClassPathResource("database/migrations/"+file),java.nio.charset.StandardCharsets.UTF_8));
   }
   assertThat(db.queryForObject("SELECT max_students FROM course WHERE course_id=1",Integer.class)).isEqualTo(30);
   initializer.afterPropertiesSet();
  } finally {
   if(!schema.matches("codex_schema_test_[a-f0-9]{32}"))throw new IllegalStateException("临时库名校验失败");
   original.execute("DROP DATABASE `"+schema+"`");
  }
 }
 @Test void initializesEmptySchemaWithoutBusinessDataAndRerunsSafely() throws Exception {
  String schema="codex_schema_test_"+UUID.randomUUID().toString().replace("-","");
  JdbcTemplate original=new JdbcTemplate(source);
  original.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");
  try {
   String url;try(var c=source.getConnection()){url=c.getMetaData().getURL().replaceFirst("^(jdbc:mysql://[^/]+/)[^?]*","$1"+schema);}
   if(!url.contains("/"+schema+"?"))throw new IllegalStateException("临时库URL校验失败");
   var isolated=new DriverManagerDataSource(url,env.getProperty("spring.datasource.username"),env.getProperty("spring.datasource.password"));
   var initializer=new DatabaseInitializer(isolated,true);initializer.afterPropertiesSet();
   var db=new JdbcTemplate(isolated);
   assertThat(db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=?",Integer.class,schema)).isGreaterThanOrEqualTo(40);
   for(String table:new String[]{"student","teacher","admin","community_post","chat_record","order_record","community_appeal"})assertThat(db.queryForObject("SELECT COUNT(*) FROM "+table,Long.class)).isZero();
   db.update("INSERT INTO student(student_id,nickname,password) VALUES(1,'仅用于测试','disabled')");
   db.update("UPDATE special_word SET enabled=0");
   initializer.afterPropertiesSet();assertThat(db.queryForObject("SELECT COUNT(*) FROM student",Long.class)).isEqualTo(1);
   assertThat(db.queryForObject("SELECT COUNT(*) FROM special_word WHERE enabled=1",Long.class)).isZero();
   assertThat(db.queryForObject("SELECT COUNT(*) FROM app_schema_migration WHERE version LIKE 'auto:%'",Long.class)).isEqualTo(9);
  } finally {
   if(!schema.matches("codex_schema_test_[a-f0-9]{32}"))throw new IllegalStateException("临时库名校验失败");
   original.execute("DROP DATABASE `"+schema+"`");
  }
 }
}
