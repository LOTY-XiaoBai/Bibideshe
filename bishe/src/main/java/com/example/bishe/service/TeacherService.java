package com.example.bishe.service;

import com.example.bishe.common.AuthUser;
import com.example.bishe.common.BusinessException;
import com.example.bishe.common.UserRole;
import com.example.bishe.dto.LoginRequest;
import com.example.bishe.dto.LoginResponse;
import com.example.bishe.dto.MyProfileVO;
import com.example.bishe.dto.ProfileUpdateRequest;
import com.example.bishe.dto.TeacherRegisterRequest;
import com.example.bishe.entity.Teacher;
import com.example.bishe.mapper.TeacherEvaluationMapper;
import com.example.bishe.mapper.TeacherMapper;
import com.example.bishe.util.IdGenerator;
import com.example.bishe.util.TokenUtil;
import com.example.bishe.util.PasswordPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 教师账号服务: 处理注册、登录与个人资料。
 */
@Service
public class TeacherService {

    /** ID 冲突时的最大重试次数 */
    private static final int MAX_ID_RETRY = 10;

    private final TeacherMapper teacherMapper;
    private final TeacherEvaluationMapper teacherEvaluationMapper;
    private final PasswordEncoder passwordEncoder;
    private final ModerationService moderationService;
    private final CaptchaService captchaService;
    private final LoginGuardService loginGuardService;

    public TeacherService(TeacherMapper teacherMapper,
                          TeacherEvaluationMapper teacherEvaluationMapper,
                          PasswordEncoder passwordEncoder,
                          ModerationService moderationService,
                          CaptchaService captchaService,
                          LoginGuardService loginGuardService) {
        this.teacherMapper = teacherMapper;
        this.teacherEvaluationMapper = teacherEvaluationMapper;
        this.passwordEncoder = passwordEncoder;
        this.moderationService = moderationService;
        this.captchaService = captchaService;
        this.loginGuardService = loginGuardService;
    }

    /**
     * 教师注册。ID 由后端生成(2开头), 返回创建成功的教师信息(不含密码)。
     */
    public Teacher register(TeacherRegisterRequest request) {
        captchaService.verify(request.getCaptchaKey(), request.getCaptchaCode());
        if (!StringUtils.hasText(request.getName())) {
            throw new BusinessException("姓名不能为空");
        }
        PasswordPolicy.validate(request.getPassword());

        Teacher teacher = new Teacher();
        teacher.setTeacherId(generateUniqueId());
        teacher.setName(request.getName());
        teacher.setPassword(passwordEncoder.encode(request.getPassword()));
        teacher.setSignature(request.getSignature());
        teacher.setAvatar(request.getAvatar());
        teacher.setComprehensiveScore(BigDecimal.ZERO);
        teacher.setCourseCount(0);

        teacherMapper.insert(teacher);

        teacher.setPassword(null);
        return teacher;
    }

    /**
     * 教师登录: 校验验证码 + ID + 密码, 成功返回 Token 与教师信息(不含密码)。
     */
    public LoginResponse login(LoginRequest request) {
        String accountId = request.getId() == null ? "" : request.getId().trim();
        loginGuardService.assertNotLocked(accountId);
        captchaService.verify(request.getCaptchaKey(), request.getCaptchaCode());

        Long teacherId = parseId(accountId);
        Teacher teacher = teacherMapper.findById(teacherId);
        if (teacher == null) {
            throw new BusinessException("账号不存在");
        }
        moderationService.assertTeacherActive(teacher);
        if (!passwordEncoder.matches(request.getPassword(), teacher.getPassword())) {
            loginGuardService.onPasswordWrong(accountId);
        }
        loginGuardService.onLoginSuccess(accountId);
        teacher.setPassword(null);
        AuthUser authUser = new AuthUser(teacher.getTeacherId(), String.valueOf(teacher.getTeacherId()), UserRole.TEACHER);
        String token = TokenUtil.createToken(authUser);
        return new LoginResponse(token, UserRole.TEACHER, teacher);
    }

    /**
     * 查看当前教师个人资料。无人打分时 scoreTip 为「暂未有人打分」。
     */
    public MyProfileVO getProfile(Long teacherId) {
        Teacher teacher = requireTeacher(teacherId);
        int ratingCount = teacherEvaluationMapper.countByTeacherId(teacherId);
        MyProfileVO vo = new MyProfileVO();
        vo.setUserId(teacher.getTeacherId());
        vo.setRole(UserRole.TEACHER);
        vo.setDisplayName(teacher.getName());
        vo.setSignature(teacher.getSignature());
        vo.setAvatar(teacher.getAvatar());
        vo.setCourseCount(teacher.getCourseCount());
        if (ratingCount <= 0) {
            vo.setComprehensiveScore(null);
            vo.setHasRating(false);
            vo.setScoreTip("暂未有人打分");
        } else {
            vo.setComprehensiveScore(teacher.getComprehensiveScore());
            vo.setHasRating(true);
            vo.setScoreTip(null);
        }
        return vo;
    }

    /**
     * 修改教师个人资料: 姓名/个性签名/头像。
     */
    public MyProfileVO updateProfile(Long teacherId, ProfileUpdateRequest request) {
        if (request == null) {
            throw new BusinessException("请求不能为空");
        }
        Teacher teacher = requireTeacher(teacherId);
        if (request.getDisplayName() != null) {
            if (!StringUtils.hasText(request.getDisplayName())) {
                throw new BusinessException("姓名不能为空");
            }
            teacher.setName(request.getDisplayName().trim());
        }
        if (request.getSignature() != null) {
            teacher.setSignature(request.getSignature());
        }
        if (request.getAvatar() != null) {
            teacher.setAvatar(request.getAvatar());
        }
        teacherMapper.updateProfile(teacher);
        return getProfile(teacherId);
    }

    private Teacher requireTeacher(Long teacherId) {
        if (teacherId == null) {
            throw new BusinessException("教师ID不能为空");
        }
        Teacher teacher = teacherMapper.findById(teacherId);
        if (teacher == null) {
            throw new BusinessException("教师不存在");
        }
        return teacher;
    }

    /**
     * 生成一个数据库中不存在的教师ID。
     */
    private Long generateUniqueId() {
        for (int i = 0; i < MAX_ID_RETRY; i++) {
            Long id = IdGenerator.generateTeacherId();
            if (teacherMapper.findById(id) == null) {
                return id;
            }
        }
        throw new BusinessException("教师ID生成失败, 请重试");
    }

    private Long parseId(String id) {
        if (!StringUtils.hasText(id)) {
            throw new BusinessException("ID不能为空");
        }
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("ID格式不正确");
        }
    }
}
