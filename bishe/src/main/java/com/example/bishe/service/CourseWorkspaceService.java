package com.example.bishe.service;

import com.example.bishe.common.*;
import com.example.bishe.dto.*;
import com.example.bishe.entity.Course;
import com.example.bishe.mapper.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
public class CourseWorkspaceService {
    private final JdbcTemplate db;
    private final CourseMapper courses;
    private final CoursePersonnelMapper personnel;
    private final ClassroomService classroom;
    public CourseWorkspaceService(JdbcTemplate db,CourseMapper courses,CoursePersonnelMapper personnel,ClassroomService classroom){this.db=db;this.courses=courses;this.personnel=personnel;this.classroom=classroom;}
    private Course access(AuthUser user,Long id){if(!UserRole.ADMIN.equals(user.getRole()))classroom.accessible(user,id);var c=courses.findById(id);if(c==null)throw new BusinessException(404,"课程不存在");return c;}
    public Map<String,Object> overview(AuthUser user,Long id){
        Course c=access(user,id);var data=new LinkedHashMap<String,Object>();
        c.setMemberCount(db.queryForObject("SELECT COUNT(*) FROM course_personnel WHERE course_id=?",Integer.class,id));
        c.setPendingApplications(UserRole.TEACHER.equals(user.getRole())?db.queryForObject("SELECT COUNT(*) FROM course_application WHERE course_id=? AND status='PENDING'",Integer.class,id):0);
        data.put("course",c);data.put("teacher",db.queryForMap("SELECT teacher_id AS teacherId,name,avatar,signature,comprehensive_score AS comprehensiveScore FROM teacher WHERE teacher_id=?",c.getTeacherId()));
        data.put("materialCount",db.queryForObject("SELECT COUNT(*) FROM course_material WHERE course_id=?",Integer.class,id));
        if(UserRole.STUDENT.equals(user.getRole())){
            data.put("myScore",personnel.find(id,user.getUserId()).getStudentScore());
            var rating=db.queryForList("SELECT score FROM teacher_evaluation WHERE teacher_id=? AND student_id=?",BigDecimal.class,c.getTeacherId(),user.getUserId());
            data.put("myTeacherScore",rating.isEmpty()?null:rating.get(0));
        }
        return data;
    }
    @Transactional
    public Course update(Long teacherId,Long id,CourseCreateRequest request){
        Course c=courses.findByIdForUpdate(id);classroom.owned(teacherId,id);
        if(request==null || request.getCourseName()==null || request.getCourseName().trim().isEmpty() || request.getCourseName().trim().length()>100)throw new BusinessException("课程名称应为1至100字");
        c.setCourseName(request.getCourseName().trim());c.setFixedSchedule(Boolean.TRUE.equals(request.getFixedSchedule()));
        if(Boolean.TRUE.equals(request.getFixedSchedule())){
            if(request.getClassWeekday()==null || request.getClassWeekday()<1 || request.getClassWeekday()>7 || request.getClassTime()==null || !request.getClassTime().matches("([01]\\d|2[0-3]):[0-5]\\d"))throw new BusinessException("请选择固定授课的星期和时间");
            c.setClassWeekday(request.getClassWeekday());c.setClassTime(request.getClassTime());
        }else{c.setClassWeekday(null);c.setClassTime(null);}
        Integer max=request.getMaxStudents();
        if(max!=null && (max<1 || max>10000))throw new BusinessException("课堂人数上限应为1至10000人");
        int count=db.queryForObject("SELECT COUNT(*) FROM course_personnel WHERE course_id=?",Integer.class,id);
        if(max!=null && max<count)throw new BusinessException("人数上限不能低于已加入的 "+count+" 人");
        String cover=request.getCoverUrl();
        if(cover!=null && !cover.isBlank() && !cover.matches("/uploads/image/[a-f0-9]{32}\\.(png|jpg|jpeg|gif|webp|bmp)"))throw new BusinessException("课程封面地址无效");
        c.setApprovalRequired(Boolean.TRUE.equals(request.getApprovalRequired()));c.setMaxStudents(max);c.setCoverUrl(cover==null?c.getCoverUrl():cover);
        if(request.getShowStudentRanking()!=null)c.setShowStudentRanking(request.getShowStudentRanking());
        courses.updateSettings(c);classroom.broadcast(id,"COURSE_SETTINGS_UPDATED",Map.of("courseId",id));return courses.findById(id);
    }
    @Transactional
    public void setRanking(Long teacherId,Long id,boolean visible){Course c=courses.findByIdForUpdate(id);classroom.owned(teacherId,id);c.setShowStudentRanking(visible);courses.updateSettings(c);classroom.broadcast(id,"COURSE_SETTINGS_UPDATED",Map.of("courseId",id));}
    public List<CourseMemberVO> members(AuthUser user,Long id){
        Course c=access(user,id);var rows=new ArrayList<CourseMemberVO>();
        // MyBatis may reuse objects within a transaction; never mask the cached rows in place.
        for (var original : personnel.findMembersByCourseId(id)) {
            var row = new CourseMemberVO();
            org.springframework.beans.BeanUtils.copyProperties(original, row);
            rows.add(row);
        }
        boolean visible=!Boolean.FALSE.equals(c.getShowStudentRanking());
        if(visible)rows.sort(Comparator.comparing((CourseMemberVO m)->m.getStudentScore()==null?BigDecimal.ZERO:m.getStudentScore()).reversed().thenComparing(CourseMemberVO::getJoinedAt,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(CourseMemberVO::getStudentId));
        for(int i=0;i<rows.size();i++){
            var row=rows.get(i);row.setRank(visible?i+1:null);
            if(!visible && UserRole.STUDENT.equals(user.getRole()) && !Objects.equals(user.getUserId(),row.getStudentId()))row.setStudentScore(null);
        }
        return rows;
    }
    public List<Map<String,Object>> applications(Long teacherId,Long id){
        classroom.owned(teacherId,id);
        return db.queryForList("SELECT a.student_id AS studentId,a.course_id AS courseId,a.status,a.applied_at AS appliedAt,s.nickname,s.avatar FROM course_application a JOIN student s ON s.student_id=a.student_id WHERE a.course_id=? AND a.status='PENDING' ORDER BY a.applied_at,a.student_id",id);
    }
    public Map<String,Object> summary(Long studentId,Long courseId){
        access(new AuthUser(studentId,studentId.toString(),UserRole.STUDENT),courseId);
        var data=new LinkedHashMap<String,Object>();data.put("score",personnel.find(courseId,studentId).getStudentScore());
        data.put("quizCount",db.queryForObject("SELECT COUNT(*) FROM classroom_quiz_student s JOIN classroom_quiz q ON q.id=s.quiz_id WHERE q.course_id=? AND s.student_id=? AND s.status='SUBMITTED'",Integer.class,courseId,studentId));
        data.put("quizTotal",db.queryForObject("SELECT COUNT(*) FROM classroom_quiz_student s JOIN classroom_quiz q ON q.id=s.quiz_id WHERE q.course_id=? AND s.student_id=?",Integer.class,courseId,studentId));
        data.put("checkInCount",db.queryForObject("SELECT COUNT(*) FROM check_in_user u JOIN check_in_record r ON r.check_in_id=u.check_in_id WHERE r.course_id=? AND u.student_id=? AND u.status IN (1,2)",Integer.class,courseId,studentId));
        data.put("checkInTotal",db.queryForObject("SELECT COUNT(*) FROM check_in_user u JOIN check_in_record r ON r.check_in_id=u.check_in_id WHERE r.course_id=? AND u.student_id=?",Integer.class,courseId,studentId));
        data.put("examCount",db.queryForObject("SELECT COUNT(DISTINCT a.exam_id) FROM exam_question_answer a JOIN exam e ON e.exam_id=a.exam_id WHERE e.course_id=? AND a.student_id=?",Integer.class,courseId,studentId));
        data.put("examTotal",db.queryForObject("SELECT COUNT(*) FROM exam WHERE course_id=?",Integer.class,courseId));
        data.put("homeworkCount",db.queryForObject("SELECT COUNT(*) FROM user_homework_record r JOIN homework h ON h.homework_id=r.homework_id WHERE h.course_id=? AND r.student_id=?",Integer.class,courseId,studentId));
        data.put("homeworkTotal",db.queryForObject("SELECT COUNT(*) FROM homework WHERE course_id=?",Integer.class,courseId));return data;
    }
    @Transactional public void dismissApplications(Long studentId){db.update("UPDATE course_application SET student_hidden=1 WHERE student_id=?",studentId);}
}
