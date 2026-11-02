package com.example.bishe.service;

import com.example.bishe.common.AuthUser;
import com.example.bishe.common.BusinessException;
import com.example.bishe.common.UserRole;
import com.example.bishe.dto.LoginRequest;
import com.example.bishe.dto.LoginResponse;
import com.example.bishe.dto.MyProfileVO;
import com.example.bishe.dto.ProfileUpdateRequest;
import com.example.bishe.dto.StudentRegisterRequest;
import com.example.bishe.entity.Student;
import com.example.bishe.mapper.StudentMapper;
import com.example.bishe.util.IdGenerator;
import com.example.bishe.util.TokenUtil;
import com.example.bishe.util.PasswordPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 学生账号服务: 处理注册、登录与个人资料。
 */
@Service
public class StudentService {

    /** ID 冲突时的最大重试次数 */
    private static final int MAX_ID_RETRY = 10;

    private final StudentMapper studentMapper;
    private final PasswordEncoder passwordEncoder;
    private final ModerationService moderationService;
    private final CaptchaService captchaService;
    private final LoginGuardService loginGuardService;

    public StudentService(StudentMapper studentMapper,
                          PasswordEncoder passwordEncoder,
                          ModerationService moderationService,
                          CaptchaService captchaService,
                          LoginGuardService loginGuardService) {
        this.studentMapper = studentMapper;
        this.passwordEncoder = passwordEncoder;
        this.moderationService = moderationService;
        this.captchaService = captchaService;
        this.loginGuardService = loginGuardService;
    }

    /**
     * 学生注册。ID 由后端生成(1开头), 返回创建成功的学生信息(不含密码)。
     */
    public Student register(StudentRegisterRequest request) {
        captchaService.verify(request.getCaptchaKey(), request.getCaptchaCode());
        if (!StringUtils.hasText(request.getNickname())) {
            throw new BusinessException("昵称不能为空");
        }
        PasswordPolicy.validate(request.getPassword());

        Student student = new Student();
        student.setStudentId(generateUniqueId());
        student.setNickname(request.getNickname());
        student.setPassword(passwordEncoder.encode(request.getPassword()));
        student.setSignature(request.getSignature());
        student.setAvatar(request.getAvatar());
        student.setComprehensiveScore(BigDecimal.ZERO);

        studentMapper.insert(student);

        student.setPassword(null);
        return student;
    }

    /**
     * 学生登录: 校验验证码 + ID + 密码, 成功返回 Token 与学生信息(不含密码)。
     */
    public LoginResponse login(LoginRequest request) {
        String accountId = request.getId() == null ? "" : request.getId().trim();
        loginGuardService.assertNotLocked(accountId);
        captchaService.verify(request.getCaptchaKey(), request.getCaptchaCode());

        Long studentId = parseId(accountId);
        Student student = studentMapper.findById(studentId);
        if (student == null) {
            throw new BusinessException("账号不存在");
        }
        moderationService.assertStudentActive(student);
        if (!passwordEncoder.matches(request.getPassword(), student.getPassword())) {
            loginGuardService.onPasswordWrong(accountId);
        }
        loginGuardService.onLoginSuccess(accountId);
        student.setPassword(null);
        AuthUser authUser = new AuthUser(student.getStudentId(), String.valueOf(student.getStudentId()), UserRole.STUDENT);
        String token = TokenUtil.createToken(authUser);
        return new LoginResponse(token, UserRole.STUDENT, student);
    }

    /**
     * 查看当前学生个人资料。
     */
    public MyProfileVO getProfile(Long studentId) {
        Student student = requireStudent(studentId);
        MyProfileVO vo = new MyProfileVO();
        vo.setUserId(student.getStudentId());
        vo.setRole(UserRole.STUDENT);
        vo.setDisplayName(student.getNickname());
        vo.setSignature(student.getSignature());
        vo.setAvatar(student.getAvatar());
        vo.setComprehensiveScore(student.getComprehensiveScore());
        vo.setHasRating(null);
        vo.setScoreTip(null);
        return vo;
    }

    /**
     * 修改学生个人资料: 昵称/个性签名/头像。
     */
    public MyProfileVO updateProfile(Long studentId, ProfileUpdateRequest request) {
        if (request == null) {
            throw new BusinessException("请求不能为空");
        }
        Student student = requireStudent(studentId);
        if (request.getDisplayName() != null) {
            if (!StringUtils.hasText(request.getDisplayName())) {
                throw new BusinessException("昵称不能为空");
            }
            student.setNickname(request.getDisplayName().trim());
        }
        if (request.getSignature() != null) {
            student.setSignature(request.getSignature());
        }
        if (request.getAvatar() != null) {
            student.setAvatar(request.getAvatar());
        }
        studentMapper.updateProfile(student);
        return getProfile(studentId);
    }

    private Student requireStudent(Long studentId) {
        Student student = studentMapper.findById(studentId);
        moderationService.assertStudentActive(student);
        return student;
    }

    private Long generateUniqueId() {
        for (int i = 0; i < MAX_ID_RETRY; i++) {
            Long id = IdGenerator.generateStudentId();
            if (studentMapper.findById(id) == null) {
                return id;
            }
        }
        throw new BusinessException("生成学生ID失败, 请重试");
    }

    private Long parseId(String id) {
        if (!StringUtils.hasText(id)) {
            throw new BusinessException("ID不能为空");
        }
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("学生ID格式不正确");
        }
    }
}
