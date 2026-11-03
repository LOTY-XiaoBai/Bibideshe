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
   assertThat(db.queryForObject("SELECT COUNT(*) FROM app_schema_migration WHERE version LIKE 'auto:%'",Long.class)).isEqualTo(7);
  } finally {
   if(!schema.matches("codex_schema_test_[a-f0-9]{32}"))throw new IllegalStateException("临时库名校验失败");
   original.execute("DROP DATABASE `"+schema+"`");
  }
 }
}
