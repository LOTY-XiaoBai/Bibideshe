package com.example.bishe.service;

import com.example.bishe.common.AuthUser;
import com.example.bishe.common.BusinessException;
import com.example.bishe.common.UserRole;
import com.example.bishe.dto.AdminRegisterRequest;
import com.example.bishe.dto.LoginRequest;
import com.example.bishe.dto.LoginResponse;
import com.example.bishe.dto.QuestionAuditRequest;
import com.example.bishe.dto.QuestionDeleteRequest;
import com.example.bishe.dto.QuestionView;
import com.example.bishe.entity.Admin;
import com.example.bishe.dto.AdminDeregisterRequest;
import com.example.bishe.entity.Course;
import com.example.bishe.entity.Student;
import com.example.bishe.entity.Teacher;
import com.example.bishe.mapper.AdminMapper;
import com.example.bishe.mapper.CascadeMapper;
import com.example.bishe.mapper.CourseMapper;
import com.example.bishe.mapper.CoursePersonnelMapper;
import com.example.bishe.mapper.StudentMapper;
import com.example.bishe.mapper.TeacherMapper;
import com.example.bishe.util.IdGenerator;
import com.example.bishe.util.TokenUtil;
import com.example.bishe.util.PasswordPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员账号服务: 处理登录、首次初始化注册与(需已登录管理员)创建新管理员, 以及管理员的高危操作。
 * 首次部署时若管理员数量为 0, 可通过隐藏入口完成初始化注册; 之后仅已登录管理员可继续创建。
 * 管理员ID格式为 "admin" + 5位随机数字。
 */
@Service
public class AdminService {

    /** ID 冲突时的最大重试次数 */
    private static final int MAX_ID_RETRY = 20;

    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder;
    private final CourseService courseService;
    private final CourseMapper courseMapper;
    private final CoursePersonnelMapper coursePersonnelMapper;
    private final TeacherMapper teacherMapper;
    private final StudentMapper studentMapper;
    private final CascadeMapper cascadeMapper;
    private final QuestionService questionService;
    private final ExamService examService;
    private final HomeworkService homeworkService;
    private final CaptchaService captchaService;
    private final LoginGuardService loginGuardService;

    public AdminService(AdminMapper adminMapper,
                        PasswordEncoder passwordEncoder,
                        CourseService courseService,
                        CourseMapper courseMapper,
                        CoursePersonnelMapper coursePersonnelMapper,
                        TeacherMapper teacherMapper,
                        StudentMapper studentMapper,
                        CascadeMapper cascadeMapper,
                        QuestionService questionService,
                        ExamService examService,
                        HomeworkService homeworkService,
                        CaptchaService captchaService,
                        LoginGuardService loginGuardService) {
        this.adminMapper = adminMapper;
        this.passwordEncoder = passwordEncoder;
        this.courseService = courseService;
        this.courseMapper = courseMapper;
        this.coursePersonnelMapper = coursePersonnelMapper;
        this.teacherMapper = teacherMapper;
        this.studentMapper = studentMapper;
        this.cascadeMapper = cascadeMapper;
        this.questionService = questionService;
        this.examService = examService;
        this.homeworkService = homeworkService;
        this.captchaService = captchaService;
        this.loginGuardService = loginGuardService;
    }

    /**
     * 系统是否允许首次初始化注册管理员。
     * 仅当管理员账号数量为 0 时返回 true。
     */
    public boolean canBootstrapRegister() {
        return adminMapper.countAll() == 0;
    }

    /**
     * 首次部署时初始化注册管理员。
     * 仅当系统中尚无任何管理员账号时允许调用。
     */
    public Admin bootstrapRegister(AdminRegisterRequest request) {
        if (!canBootstrapRegister()) {
            throw new BusinessException("系统已存在管理员账号，无法再次注册");
        }
        return createAdmin(request);
    }

    /**
     * 由已登录管理员创建新管理员账号。
     * ID 由后端生成("admin" + 5位随机数字), 返回创建成功的管理员信息(不含密码)。
     */
    public Admin createAdmin(AdminRegisterRequest request) {
        PasswordPolicy.validate(request.getPassword());

        Admin admin = new Admin();
        admin.setAdminId(generateUniqueId());
        admin.setPassword(passwordEncoder.encode(request.getPassword()));

        adminMapper.insert(admin);

        admin.setPassword(null);
        return admin;
    }

    /**
     * @deprecated 请使用 {@link #bootstrapRegister(AdminRegisterRequest)} 或 {@link #createAdmin(AdminRegisterRequest)}。
     */
    @Deprecated
    public Admin register(AdminRegisterRequest request) {
        return createAdmin(request);
    }

    /**
     * 管理员登录: 校验验证码 + ID + 密码, 成功返回 Token 与管理员信息(不含密码)。
     */
    public LoginResponse login(LoginRequest request) {
        if (!StringUtils.hasText(request.getId())) {
            throw new BusinessException("ID不能为空");
        }
        String accountId = request.getId().trim();
        loginGuardService.assertNotLocked(accountId);
        captchaService.verify(request.getCaptchaKey(), request.getCaptchaCode());

        Admin admin = adminMapper.findById(accountId);
        if (admin == null) {
            throw new BusinessException("账号不存在");
        }
        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            loginGuardService.onPasswordWrong(accountId);
        }
        loginGuardService.onLoginSuccess(accountId);
        admin.setPassword(null);
        AuthUser authUser = new AuthUser(null, admin.getAdminId(), UserRole.ADMIN);
        String token = TokenUtil.createToken(authUser);
        return new LoginResponse(token, UserRole.ADMIN, admin);
    }

    // ==================== 管理员端: 题库审核与删除 ====================

    /** 查询全部待审核的公共题库题目 */
    public List<QuestionView> listPendingAuditQuestions() {
        return questionService.listPendingAudit();
    }

    /**
     * 管理员审核教师提交至公共题库的题目。
     */
    public void auditQuestion(QuestionAuditRequest request) {
        if (request.getQuestionId() == null || request.getQuestionType() == null) {
            throw new BusinessException("题目ID与题目类型不能为空");
        }
        if (request.getApprove() == null) {
            throw new BusinessException("请指明是否通过审核");
        }
        questionService.auditQuestion(request.getQuestionId(), request.getQuestionType(), request.getApprove());
    }

    /**
     * 管理员删除题目(无论是否已上传至公共题库均可删除)。
     * 已经引用该题目的考试/作业会将其替换为占位文案, 并给已作答学生直接补满该题满分。
     */
    public void deleteQuestion(QuestionDeleteRequest request) {
        if (request.getQuestionId() == null || request.getQuestionType() == null) {
            throw new BusinessException("题目ID与题目类型不能为空");
        }
        questionService.deleteQuestion(request.getQuestionId(), request.getQuestionType());
    }

    /**
     * 管理员分页查询学生账号(支持昵称模糊 / ID 精确；无关键字时列出全部)。
     */
    public Map<String, Object> listStudents(String keyword, int page, int size) {
        page = normalizePage(page);
        size = normalizeSize(size);
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Long id = parseLongId(kw);
        int offset = (page - 1) * size;
        List<Student> students = studentMapper.findForAdmin(kw, id, offset, size);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Student s : students) {
            Map<String, Object> item = new HashMap<>();
            item.put("studentId", s.getStudentId());
            item.put("nickname", s.getNickname());
            item.put("signature", s.getSignature());
            item.put("avatar", s.getAvatar());
            item.put("comprehensiveScore", s.getComprehensiveScore());
            item.put("isDeleted", s.getIsDeleted() == null ? 0 : s.getIsDeleted());
            item.put("isBanned", s.getIsBanned() == null ? 0 : s.getIsBanned());
            item.put("banReason", s.getBanReason());
            item.put("bannedBy", s.getBannedBy());
            item.put("bannedAt", s.getBannedAt());
            list.add(item);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", studentMapper.countForAdmin(kw, id));
        result.put("page", page);
        result.put("size", size);
        result.put("list", list);
        return result;
    }

    /**
     * 管理员分页查询教师账号(支持姓名模糊 / ID 精确；无关键字时列出全部)。
     */
    public Map<String, Object> listTeachers(String keyword, int page, int size) {
        page = normalizePage(page);
        size = normalizeSize(size);
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Long id = parseLongId(kw);
        int offset = (page - 1) * size;
        List<Teacher> teachers = teacherMapper.findForAdmin(kw, id, offset, size);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Teacher t : teachers) {
            Map<String, Object> item = new HashMap<>();
            item.put("teacherId", t.getTeacherId());
            item.put("name", t.getName());
            item.put("signature", t.getSignature());
            item.put("avatar", t.getAvatar());
            item.put("comprehensiveScore", t.getComprehensiveScore());
            item.put("courseCount", t.getCourseCount());
            item.put("isDeleted", t.getIsDeleted() == null ? 0 : t.getIsDeleted());
            item.put("isBanned", t.getIsBanned() == null ? 0 : t.getIsBanned());
            item.put("banReason", t.getBanReason());
            item.put("bannedBy", t.getBannedBy());
            item.put("bannedAt", t.getBannedAt());
            list.add(item);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", teacherMapper.countForAdmin(kw, id));
        result.put("page", page);
        result.put("size", size);
        result.put("list", list);
        return result;
    }

    /**
     * 管理员分页查询全部课程(可按课程号精确筛选；不强制课程名)。
     */
    public Map<String, Object> listCourses(String keyword, int page, int size) {
        page = normalizePage(page);
        size = normalizeSize(size);
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Long courseId = null;
        if (kw != null) {
            courseId = parseLongId(kw);
            // 有关键字但不是合法课程号时，直接返回空结果（不按课程名搜索）
            if (courseId == null) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("total", 0);
                empty.put("page", page);
                empty.put("size", size);
                empty.put("list", new ArrayList<>());
                return empty;
            }
        }
        int offset = (page - 1) * size;
        Map<String, Object> result = new HashMap<>();
        result.put("total", courseMapper.countForAdmin(courseId));
        result.put("page", page);
        result.put("size", size);
        result.put("list", courseMapper.findForAdmin(courseId, offset, size));
        return result;
    }

    private Long parseLongId(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        try {
            return Long.parseLong(keyword.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int normalizePage(int page) {
        return page < 1 ? 1 : page;
    }

    private int normalizeSize(int size) {
        if (size < 1 || size > 100) {
            return 10;
        }
        return size;
    }

    // ==================== 管理员高危操作 ====================

    /**
     * 管理员删除任意课程(级联删除课程下所有记录), 并使对应教师的 course_count -1。
     */
    @Transactional
    public void deleteCourse(Long courseId) {
        if (courseId == null) {
            throw new BusinessException("课程ID不能为空");
        }
        Course course = courseMapper.findById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        courseService.deleteCourseCascade(courseId);
        teacherMapper.decrementCourseCount(course.getTeacherId());
    }

    /**
     * 管理员注销账号并解绑其全部关联数据。
     * <ul>
     *   <li>教师: 删除其创建的所有课程及课程下全部记录, 删除其考试/作业与题库
     *       (若其公共题被其他教师卷子引用, 已作答学生该题补满分并显示丢失占位), 再删除教师账号。</li>
     *   <li>学生: 删除其选课、考试/作业/签到/错题/订单等记录, 最后删除学生账号。</li>
     * </ul>
     */
    @Transactional
    public void deregisterAccount(AdminDeregisterRequest request) {
        if (request.getAccountId() == null) {
            throw new BusinessException("账号ID不能为空");
        }
        String type = request.getAccountType();
        if (!StringUtils.hasText(type)) {
            throw new BusinessException("账号类型不能为空");
        }

        if ("teacher".equalsIgnoreCase(type)) {
            deregisterTeacher(request.getAccountId());
        } else if ("student".equalsIgnoreCase(type)) {
            deregisterStudent(request.getAccountId());
        } else {
            throw new BusinessException("不支持的账号类型: " + type);
        }
    }

    /**
     * 注销教师账号: 级联删除其名下课程与题库、评分记录, 再删除教师本身。
     */
    private void deregisterTeacher(Long teacherId) {
        if (teacherMapper.findById(teacherId) == null) {
            throw new BusinessException("教师不存在");
        }
        List<Course> courses = courseMapper.findByTeacherId(teacherId);
        for (Course course : courses) {
            courseService.deleteCourseCascade(course.getCourseId());
        }
        // 兜底清理教师名下的考试/作业(正常情况下已随课程级联删除, 这里避免遗留脏数据)
        examService.deleteExamsByPublisher(teacherId);
        homeworkService.deleteHomeworksByPublisher(teacherId);
        // 清理教师题库: 走 deleteQuestion 逻辑, 给引用了这些公共题的其他教师卷子补满分后再删题
        questionService.deleteAllByPublisher(teacherId);
        cascadeMapper.deleteTeacherEvaluationsByTeacherId(teacherId);

        teacherMapper.deleteById(teacherId);
    }

    /**
     * 注销学生账号: 删除其选课与各类学习/订单记录, 再删除学生本身。
     */
    private void deregisterStudent(Long studentId) {
        if (studentMapper.findById(studentId) == null) {
            throw new BusinessException("学生不存在");
        }
        coursePersonnelMapper.deleteByStudentId(studentId);
        cascadeMapper.deleteExamAnswersByStudentId(studentId);
        cascadeMapper.deleteHomeworkAnswersByStudentId(studentId);
        cascadeMapper.deleteUserExamRecordsByStudentId(studentId);
        cascadeMapper.deleteUserHomeworkRecordsByStudentId(studentId);
        cascadeMapper.deleteCheckInUsersByStudentId(studentId);
        cascadeMapper.deleteErrorBookByStudentId(studentId);
        cascadeMapper.deleteTeacherEvaluationsByStudentId(studentId);

        studentMapper.deleteById(studentId);
    }

    /**
     * 生成一个数据库中不存在的管理员ID。
     */
    private String generateUniqueId() {
        for (int i = 0; i < MAX_ID_RETRY; i++) {
            String id = IdGenerator.generateAdminId();
            if (adminMapper.findById(id) == null) {
                return id;
            }
        }
        throw new BusinessException("管理员ID生成失败, 请重试");
    }
}
