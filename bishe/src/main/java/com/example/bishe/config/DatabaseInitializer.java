package com.example.bishe.config;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
/** 首次启动建表，旧库仅执行未应用的增量；不导入本地账号、聊天或帖子。 */
@Component
public class DatabaseInitializer implements InitializingBean {
    private final DataSource source;
    private final boolean enabled;
    public DatabaseInitializer(DataSource source,@Value("${app.database.initialize:true}")boolean enabled){this.source=source;this.enabled=enabled;}
    public void afterPropertiesSet() throws Exception {
        if(!enabled)return;
        try(var connection=source.getConnection()){
            String lock="bishe-schema:"+connection.getCatalog();
            try(var statement=connection.prepareStatement("SELECT GET_LOCK(?,60)")){
                statement.setString(1,lock);try(var result=statement.executeQuery()){if(!result.next()||result.getInt(1)!=1)throw new IllegalStateException("无法获取数据库迁移锁");}
            }
            try {
                try(var statement=connection.createStatement()){statement.execute("CREATE TABLE IF NOT EXISTS app_schema_migration(version VARCHAR(80) PRIMARY KEY,applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");}
                for(String path:List.of("base/01_schema.sql","migrations/07_community_shop_profile_manual.sql","migrations/09_community_reply_likes.sql","migrations/10_community_safety.sql","migrations/11_community_management.sql","migrations/12_navigation_appeals.sql","migrations/13_direct_moderation_appeals.sql","migrations/14_course_classroom.sql","migrations/15_course_query_indexes.sql","migrations/16_classroom_quizzes.sql","migrations/17_course_workspace.sql","migrations/18_merchant_commerce.sql","migrations/19_merchant_controls.sql","migrations/20_merchant_storefront.sql","migrations/21_merchant_ban_duration.sql","migrations/22_home_carousel.sql")){
                    String version="auto:"+path.substring(path.lastIndexOf('/')+1);
                    try(var check=connection.prepareStatement("SELECT COUNT(*) FROM app_schema_migration WHERE version=?")){
                        check.setString(1,version);try(var result=check.executeQuery()){result.next();if(result.getInt(1)>0)continue;}
                    }
                    ScriptUtils.executeSqlScript(connection,new EncodedResource(new ClassPathResource("database/"+path),StandardCharsets.UTF_8));
                    try(var mark=connection.prepareStatement("INSERT IGNORE INTO app_schema_migration(version) VALUES(?)")){mark.setString(1,version);mark.executeUpdate();}
                }
            } finally {try(var release=connection.prepareStatement("SELECT RELEASE_LOCK(?)")){release.setString(1,lock);release.execute();}}
        }
    }
}
