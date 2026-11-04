package com.example.bishe.service;

import com.example.bishe.common.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.sql.Statement;
import java.util.*;

@Service
public class CourseFilesService {
    private final JdbcTemplate db;
    private final ClassroomService classroom;
    private final FileUploadService files;
    public CourseFilesService(JdbcTemplate db,ClassroomService classroom,FileUploadService files){this.db=db;this.classroom=classroom;this.files=files;}
    private void access(AuthUser user,Long courseId){if(!UserRole.ADMIN.equals(user.getRole()))classroom.accessible(user,courseId);}
    public List<Map<String,Object>> list(AuthUser user,Long courseId){
        access(user,courseId);
        return db.queryForList("SELECT id,original_name AS originalName,file_size AS fileSize,content_type AS contentType,uploaded_at AS uploadedAt FROM course_material WHERE course_id=? ORDER BY uploaded_at DESC,id DESC",courseId);
    }
    @Transactional
    public Map<String,Object> upload(Long teacherId,Long courseId,MultipartFile file){
        db.queryForList("SELECT course_id FROM course WHERE course_id=? FOR UPDATE",courseId);classroom.owned(teacherId,courseId);
        if(file==null || file.isEmpty())throw new BusinessException("请选择课程资料");
        String original=Objects.toString(file.getOriginalFilename(),"资料").replace('\\','/');original=original.substring(original.lastIndexOf('/')+1);
        if(original.isBlank() || original.length()>255)throw new BusinessException("资料文件名应为1至255字");
        String stored=files.uploadCourseMaterial(file);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)files.deleteCourseMaterial(stored);}});
        String name=original;String type=Objects.toString(file.getContentType(),"application/octet-stream");if(type.length()>120)type="application/octet-stream";String mime=type;
        var key=new GeneratedKeyHolder();
        db.update(c->{var s=c.prepareStatement("INSERT INTO course_material(course_id,teacher_id,original_name,storage_name,file_size,content_type) VALUES(?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);s.setLong(1,courseId);s.setLong(2,teacherId);s.setString(3,name);s.setString(4,stored);s.setLong(5,file.getSize());s.setString(6,mime);return s;},key);
        return Map.of("id",Objects.requireNonNull(key.getKey()).longValue(),"originalName",name,"fileSize",file.getSize());
    }
    public record Download(org.springframework.core.io.Resource resource,String filename,long size){}
    public Download download(AuthUser user,Long courseId,Long id){
        access(user,courseId);var row=material(courseId,id);
        var resource=new org.springframework.core.io.FileSystemResource(files.courseMaterialPath(row.get("storage_name").toString()));
        if(!resource.exists())throw new BusinessException(404,"资料文件不存在，请联系教师重新上传");
        return new Download(resource,row.get("original_name").toString(),((Number)row.get("file_size")).longValue());
    }
    private Map<String,Object> material(Long courseId,Long id){var rows=db.queryForList("SELECT * FROM course_material WHERE id=? AND course_id=?",id,courseId);if(rows.isEmpty())throw new BusinessException(404,"课程资料不存在");return rows.get(0);}
    @Transactional
    public void remove(Long teacherId,Long courseId,Long id){
        classroom.owned(teacherId,courseId);var row=material(courseId,id);db.update("DELETE FROM course_material WHERE id=? AND course_id=?",id,courseId);deleteAfterCommit(List.of(row.get("storage_name").toString()));
    }
    public void deleteForCourse(Long courseId){
        var names=db.queryForList("SELECT storage_name FROM course_material WHERE course_id=?",String.class,courseId);
        db.update("DELETE FROM course_material WHERE course_id=?",courseId);deleteAfterCommit(names);
    }
    private void deleteAfterCommit(List<String> names){
        if(names.isEmpty())return;
        if(!TransactionSynchronizationManager.isSynchronizationActive()){names.forEach(files::deleteCourseMaterial);return;}
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){names.forEach(files::deleteCourseMaterial);}});
    }
}
