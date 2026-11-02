package com.example.bishe.service;

import com.example.bishe.common.BusinessException;
import com.example.bishe.common.AuthUser;
import com.example.bishe.common.QuestionType;
import com.example.bishe.common.UserRole;
import com.example.bishe.dto.AnswerItem;
import com.example.bishe.dto.AnswerReviewItem;
import com.example.bishe.dto.ExamDetailResponse;
import com.example.bishe.dto.ExamPublishRequest;
import com.example.bishe.dto.ExamReviewResponse;
import com.example.bishe.dto.ExamStartResponse;
import com.example.bishe.dto.ExamSubmitRequest;
import com.example.bishe.dto.GradeAnswerRequest;
import com.example.bishe.dto.PendingGradeItemVO;
import com.example.bishe.dto.QuestionItemView;
import com.example.bishe.dto.QuestionScoreItem;
import com.example.bishe.dto.QuestionView;
import com.example.bishe.dto.ScoreRankItemVO;
import com.example.bishe.entity.Course;
import com.example.bishe.entity.Exam;
import com.example.bishe.entity.ExamQuestion;
import com.example.bishe.entity.ExamQuestionAnswer;
import com.example.bishe.entity.Student;
import com.example.bishe.entity.UserExamRecord;
import com.example.bishe.mapper.CourseMapper;
import com.example.bishe.mapper.CoursePersonnelMapper;
import com.example.bishe.mapper.ErrorBookMapper;
import com.example.bishe.mapper.ExamMapper;
import com.example.bishe.mapper.ExamQuestionAnswerMapper;
import com.example.bishe.mapper.ExamQuestionMapper;
import com.example.bishe.mapper.StudentMapper;
import com.example.bishe.mapper.UserExamRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 考试服务: 教师发布考试, 学生作答提交并自动批改, 答错自动加入错题本。
 */
@Service
public class ExamService {

    /** 题目被管理员删除时的补满分批注 */
    private static final String LOST_QUESTION_ANNOTATION = "该题目已被管理员删除, 系统自动给予满分";

    /** 解答题等待教师人工批改的标记 */
    public static final String PENDING_GRADE_ANNOTATION = "待教师批改";

    /** 解答题已批阅标记(返回给前端展示) */
    public static final String GRADED_ANNOTATION = "已批阅";

    private static final int RESULT_HIDDEN = 1;
    private static final int RESULT_PUBLISHED = 1;

    private final ExamMapper examMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final ExamQuestionAnswerMapper examQuestionAnswerMapper;
    private final UserExamRecordMapper userExamRecordMapper;
    private final CourseMapper courseMapper;
    private final CoursePersonnelMapper coursePersonnelMapper;
    private final ErrorBookMapper errorBookMapper;
    private final QuestionService questionService;
    private final CourseService courseService;
    private final StudentMapper studentMapper;

    public ExamService(ExamMapper examMapper,
                        ExamQuestionMapper examQuestionMapper,
                        ExamQuestionAnswerMapper examQuestionAnswerMapper,
                        UserExamRecordMapper userExamRecordMapper,
                        CourseMapper courseMapper,
                        CoursePersonnelMapper coursePersonnelMapper,
                        ErrorBookMapper errorBookMapper,
                        QuestionService questionService,
                        CourseService courseService,
                        StudentMapper studentMapper) {
        this.examMapper = examMapper;
        this.examQuestionMapper = examQuestionMapper;
        this.examQuestionAnswerMapper = examQuestionAnswerMapper;
        this.userExamRecordMapper = userExamRecordMapper;
        this.courseMapper = courseMapper;
        this.coursePersonnelMapper = coursePersonnelMapper;
        this.errorBookMapper = errorBookMapper;
        this.questionService = questionService;
        this.courseService = courseService;
        this.studentMapper = studentMapper;
    }

    // ==================== 教师端: 发布考试 ====================

    @Transactional
    public Exam publishExam(ExamPublishRequest request) {
        if (request.getTeacherId() == null || request.getCourseId() == null) {
            throw new BusinessException("教师ID与课程ID不能为空");
        }
        Course course = courseMapper.findById(request.getCourseId());
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        if (!course.getTeacherId().equals(request.getTeacherId())) {
            throw new BusinessException("无权在该课程下发布考试(非本人创建)");
        }
        if (request.getTimeLimit() == null || request.getTimeLimit() <= 0) {
            throw new BusinessException("考试限制时间必须为正整数(分钟)");
        }
        List<QuestionScoreItem> items = validateQuestionItems(request.getQuestions());

        Exam exam = new Exam();
        exam.setCourseId(request.getCourseId());
        exam.setPublisherId(request.getTeacherId());
        exam.setTimeLimit(request.getTimeLimit());
        exam.setHideResultUntilPublish(normalizeHideFlag(request.getHideResultUntilPublish()));
        exam.setIsResultPublished(0);
        examMapper.insert(exam);

        List<ExamQuestion> examQuestions = new ArrayList<>();
        for (QuestionScoreItem item : items) {
            ExamQuestion eq = new ExamQuestion();
            eq.setExamId(exam.getExamId());
            eq.setQuestionId(item.getQuestionId());
            eq.setQuestionType(item.getQuestionType());
            eq.setScore(item.getScore());
            examQuestions.add(eq);
        }
        examQuestionMapper.batchInsert(examQuestions);
        return exam;
    }

    // ==================== 查询 ====================

    public ExamDetailResponse getExamDetail(Long examId, AuthUser user) {
        Exam exam = requireExam(examId);
        boolean hideAnswer = authorizeCourseAccess(exam.getCourseId(), user);
        List<ExamQuestion> examQuestions = examQuestionMapper.findByExamId(examId);
        List<QuestionView> views = questionService.resolveQuestions(examQuestions.stream()
                .map(eq -> new QuestionService.QuestionRef(eq.getQuestionId(), eq.getQuestionType()))
                .collect(Collectors.toList()), hideAnswer);
        List<QuestionItemView> items = new ArrayList<>();
        for (int i = 0; i < examQuestions.size(); i++) {
            items.add(new QuestionItemView(examQuestions.get(i).getScore(), views.get(i)));
        }
        ExamDetailResponse response = new ExamDetailResponse();
        response.setExam(exam);
        response.setQuestions(items);
        return response;
    }

    public List<Exam> listByCourse(Long courseId, AuthUser user) {
        if (courseId == null) {
            throw new BusinessException("课程ID不能为空");
        }
        authorizeCourseAccess(courseId, user);
        return examMapper.findByCourseId(courseId);
    }

    /**
     * 校验课程资源访问权限。学生仅可访问已选课程且答案始终隐藏；
     * 教师仅可访问本人课程；系统管理员可访问并查看答案。
     *
     * @return 是否必须隐藏标准答案
     */
    private boolean authorizeCourseAccess(Long courseId, AuthUser user) {
        if (user == null) {
            throw new BusinessException(401, "未登录或登录已失效");
        }
        Course course = courseMapper.findById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        if (UserRole.ADMIN.equals(user.getRole())) {
            return false;
        }
        if (UserRole.TEACHER.equals(user.getRole())) {
            if (user.getUserId() == null || !user.getUserId().equals(course.getTeacherId())) {
                throw new BusinessException(403, "无权访问其他教师课程的考试");
            }
            return false;
        }
        if (UserRole.STUDENT.equals(user.getRole())) {
            if (user.getUserId() == null || coursePersonnelMapper.find(courseId, user.getUserId()) == null) {
                throw new BusinessException(403, "您未选修该课程，无法访问考试");
            }
            return true;
        }
        throw new BusinessException(403, "当前身份无权访问考试");
    }

    public List<Exam> listByTeacher(Long teacherId) {
        if (teacherId == null) {
            throw new BusinessException("教师ID不能为空");
        }
        return examMapper.findByPublisherId(teacherId);
    }

    public List<UserExamRecord> listRecordsByStudent(Long studentId) {
        if (studentId == null) {
            throw new BusinessException("学生ID不能为空");
        }
        return userExamRecordMapper.findByStudentId(studentId);
    }

    /** 学生查看自己某次考试的作答复盘(提交后可反复查看) */
    public ExamReviewResponse reviewExam(Long examId, Long studentId) {
        Exam exam = requireExam(examId);
        assertStudentCanViewResult(exam, studentId);
        List<ExamQuestionAnswer> answers = examQuestionAnswerMapper.findByExamAndStudent(examId, studentId);
        if (answers.isEmpty()) {
            throw new BusinessException("您尚未提交该考试");
        }
        UserExamRecord record = userExamRecordMapper.find(studentId, examId);
        return buildReviewResponse(examId, studentId, answers, record, null);
    }

    /** 教师公布考试成绩与答案 */
    @Transactional
    public void publishResult(Long teacherId, Long examId) {
        requireOwnedExam(teacherId, examId);
        examMapper.updateResultPublished(examId, RESULT_PUBLISHED);
    }

    /**
     * 教师查看某次考试全部学生得分排名(公布成绩后或任意时刻均可查看)。
     */
    public List<ScoreRankItemVO> listScoreRanking(Long teacherId, Long examId) {
        requireOwnedExam(teacherId, examId);
        List<UserExamRecord> records = userExamRecordMapper.findByExamId(examId);
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<Long> studentIds = records.stream().map(UserExamRecord::getStudentId).distinct().collect(Collectors.toList());
        Map<Long, Student> studentMap = new HashMap<>();
        if (!studentIds.isEmpty()) {
            for (Student s : studentMapper.findByIds(studentIds)) {
                studentMap.put(s.getStudentId(), s);
            }
        }
        List<ScoreRankItemVO> items = new ArrayList<>();
        for (UserExamRecord record : records) {
            ScoreRankItemVO item = new ScoreRankItemVO();
            item.setStudentId(record.getStudentId());
            Student student = studentMap.get(record.getStudentId());
            if (student != null) {
                item.setNickname(student.getNickname());
                item.setAvatar(student.getAvatar());
            }
            item.setAutoScore(record.getExamScore());
            item.setTeacherScore(record.getTeacherScore());
            BigDecimal finalScore = record.getTeacherScore() != null
                    && record.getTeacherScore().compareTo(BigDecimal.ZERO) > 0
                    ? record.getTeacherScore()
                    : (record.getExamScore() == null ? BigDecimal.ZERO : record.getExamScore());
            item.setFinalScore(finalScore);
            items.add(item);
        }
        items.sort(Comparator.comparing(ScoreRankItemVO::getFinalScore,
                Comparator.nullsLast(Comparator.reverseOrder())));
        int rank = 1;
        for (ScoreRankItemVO item : items) {
            item.setRank(rank++);
        }
        return items;
    }

    /** 教师查看指定学生的考试作答(不受成绩公布限制) */
    public ExamReviewResponse reviewExamForTeacher(Long teacherId, Long examId, Long studentId) {
        requireOwnedExam(teacherId, examId);
        if (studentId == null) {
            throw new BusinessException("学生ID不能为空");
        }
        List<ExamQuestionAnswer> answers = examQuestionAnswerMapper.findByExamAndStudent(examId, studentId);
        if (answers.isEmpty()) {
            throw new BusinessException("该学生尚未提交考试");
        }
        UserExamRecord record = userExamRecordMapper.find(studentId, examId);
        return buildReviewResponse(examId, studentId, answers, record, null);
    }

    // ==================== 学生端: 开始考试 / 作答并提交 ====================

    /**
     * 学生开始考试: 记录 start_time, 此后提交须在 timeLimit 分钟内完成。
     * 重复调用幂等, 返回首次开始时间。
     */
    @Transactional
    public ExamStartResponse startExam(Long studentId, Long examId) {
        if (studentId == null || examId == null) {
            throw new BusinessException("学生ID与考试ID不能为空");
        }
        Exam exam = requireExam(examId);
        if (coursePersonnelMapper.find(exam.getCourseId(), studentId) == null) {
            throw new BusinessException("您未选修该课程, 无法参加此考试");
        }
        UserExamRecord existing = userExamRecordMapper.find(studentId, examId);
        if (existing != null && existing.getStartTime() != null) {
            return toStartResponse(exam, existing);
        }
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            UserExamRecord record = new UserExamRecord();
            record.setStudentId(studentId);
            record.setExamId(examId);
            record.setExamScore(BigDecimal.ZERO);
            record.setTeacherScore(BigDecimal.ZERO);
            record.setStartTime(now);
            userExamRecordMapper.insert(record);
            return toStartResponse(exam, record);
        }
        // 兼容旧数据: 有总分记录但无 start_time 时补写开始时间
        userExamRecordMapper.updateStartTime(studentId, examId, now);
        existing.setStartTime(now);
        return toStartResponse(exam, existing);
    }

    @Transactional
    public ExamReviewResponse submitExam(ExamSubmitRequest request) {
        if (request.getStudentId() == null || request.getExamId() == null) {
            throw new BusinessException("学生ID与考试ID不能为空");
        }
        Exam exam = requireExam(request.getExamId());
        if (coursePersonnelMapper.find(exam.getCourseId(), request.getStudentId()) == null) {
            throw new BusinessException("您未选修该课程, 无法参加此考试");
        }

        UserExamRecord session = userExamRecordMapper.find(request.getStudentId(), exam.getExamId());
        if (session == null || session.getStartTime() == null) {
            throw new BusinessException("请先开始考试后再提交");
        }
        LocalDateTime deadline = session.getStartTime().plusMinutes(exam.getTimeLimit());
        if (LocalDateTime.now().isAfter(deadline)) {
            throw new BusinessException("已超过考试限制时间(" + exam.getTimeLimit() + "分钟), 无法提交");
        }

        // 考试提交必须幂等：防止重复请求覆盖已经入库的答卷。
        if (examQuestionAnswerMapper.countByExamAndStudent(exam.getExamId(), request.getStudentId()) > 0) {
            throw new BusinessException(409, "该考试已提交，请勿重复提交");
        }

        List<ExamQuestion> examQuestions = examQuestionMapper.findByExamId(exam.getExamId());
        if (examQuestions.isEmpty()) {
            throw new BusinessException("该考试尚未配置任何题目");
        }
        Map<String, String> answerMap = toAnswerMap(request.getAnswers());

        BigDecimal total = BigDecimal.ZERO;
        List<ExamQuestionAnswer> savedAnswers = new ArrayList<>();
        for (ExamQuestion eq : examQuestions) {
            String key = key(eq.getQuestionId(), eq.getQuestionType());
            String studentAnswer = answerMap.get(key);

            ExamQuestionAnswer answer = new ExamQuestionAnswer();
            answer.setExamId(exam.getExamId());
            answer.setQuestionId(eq.getQuestionId());
            answer.setQuestionType(eq.getQuestionType());
            answer.setStudentId(request.getStudentId());
            answer.setCourseId(exam.getCourseId());
            answer.setStudentAnswer(studentAnswer);

            if (!questionService.existsQuestion(eq.getQuestionId(), eq.getQuestionType())) {
                answer.setScore(eq.getScore());
                answer.setAnnotation(LOST_QUESTION_ANNOTATION);
            } else if (QuestionType.SOLVE == eq.getQuestionType()) {
                // 解答题不自动批改, 等待教师人工批改
                answer.setScore(BigDecimal.ZERO);
                answer.setAnnotation(PENDING_GRADE_ANNOTATION);
            } else if (questionService.isAnswerCorrect(eq.getQuestionId(), eq.getQuestionType(), studentAnswer)) {
                answer.setScore(eq.getScore());
            } else {
                answer.setScore(BigDecimal.ZERO);
                addToErrorBook(request.getStudentId(), eq.getQuestionId(), eq.getQuestionType());
            }

            examQuestionAnswerMapper.insert(answer);
            savedAnswers.add(answer);
            total = total.add(answer.getScore() == null ? BigDecimal.ZERO : answer.getScore());
        }

        userExamRecordMapper.updateScores(request.getStudentId(), exam.getExamId(), total, total);
        session.setExamScore(total);
        session.setTeacherScore(total);

        // 提交成功后重算该学生在本课程的分数及综合评分
        courseService.recalculateAndUpdateStudentScore(exam.getCourseId(), request.getStudentId());
        courseService.updateComprehensiveScoreForStudent(request.getStudentId());

        ExamReviewResponse response = buildReviewResponse(exam.getExamId(), request.getStudentId(), savedAnswers, session, examQuestions);
        if (!canStudentViewResult(exam)) {
            maskReviewForHiddenResult(response);
        }
        return response;
    }

    // ==================== 教师端: 解答题人工批改 ====================

    /**
     * 查询某次考试中待教师批改的解答题。
     */
    public List<PendingGradeItemVO> listPendingGrade(Long teacherId, Long examId) {
        Exam exam = requireOwnedExam(teacherId, examId);
        List<ExamQuestionAnswer> answers = examQuestionAnswerMapper.findPendingSolveByExam(examId);
        if (answers.isEmpty()) {
            return List.of();
        }
        Map<String, ExamQuestion> eqMap = toExamQuestionMap(examQuestionMapper.findByExamId(examId));
        List<QuestionView> views = questionService.resolveQuestions(answers.stream()
                .map(a -> new QuestionService.QuestionRef(a.getQuestionId(), a.getQuestionType()))
                .collect(Collectors.toList()), false);
        List<PendingGradeItemVO> result = new ArrayList<>();
        for (int i = 0; i < answers.size(); i++) {
            ExamQuestionAnswer answer = answers.get(i);
            ExamQuestion eq = eqMap.get(key(answer.getQuestionId(), answer.getQuestionType()));
            PendingGradeItemVO vo = new PendingGradeItemVO();
            vo.setExamId(examId);
            vo.setQuestionId(answer.getQuestionId());
            vo.setQuestionType(answer.getQuestionType());
            vo.setStudentId(answer.getStudentId());
            vo.setCourseId(exam.getCourseId());
            vo.setStudentAnswer(answer.getStudentAnswer());
            vo.setFullScore(eq != null ? eq.getScore() : null);
            vo.setAnnotation(answer.getAnnotation());
            vo.setQuestion(views.get(i));
            result.add(vo);
        }
        return result;
    }

    /**
     * 教师批改解答题: 写入分数与批注, 重算该生考试总分与课程成绩。
     */
    @Transactional
    public void gradeSolveAnswer(Long teacherId, GradeAnswerRequest request) {
        if (request.getExamId() == null || request.getQuestionId() == null
                || request.getQuestionType() == null || request.getStudentId() == null) {
            throw new BusinessException("考试ID/题目ID/题目类型/学生ID不能为空");
        }
        if (request.getQuestionType() != QuestionType.SOLVE) {
            throw new BusinessException("仅解答题支持人工批改");
        }
        if (request.getScore() == null || request.getScore().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("分数不能为空且不能为负数");
        }
        Exam exam = requireOwnedExam(teacherId, request.getExamId());
        ExamQuestion eq = examQuestionMapper.find(request.getExamId(), request.getQuestionId(), request.getQuestionType());
        if (eq == null) {
            throw new BusinessException("该考试不包含此题目");
        }
        if (request.getScore().compareTo(eq.getScore()) > 0) {
            throw new BusinessException("分数不能超过该题满分(" + eq.getScore() + ")");
        }
        ExamQuestionAnswer answer = examQuestionAnswerMapper.find(
                request.getExamId(), request.getQuestionId(), request.getQuestionType(), request.getStudentId());
        if (answer == null) {
            throw new BusinessException("未找到该学生的作答记录");
        }

        answer.setScore(request.getScore());
        answer.setAnnotation(resolveGradedAnnotation(request.getAnnotation()));
        examQuestionAnswerMapper.updateScore(answer);

        if (request.getScore().compareTo(eq.getScore()) < 0) {
            addToErrorBook(request.getStudentId(), request.getQuestionId(), request.getQuestionType());
        }

        BigDecimal total = examQuestionAnswerMapper.sumScoreByExamAndStudent(request.getExamId(), request.getStudentId());
        if (total == null) {
            total = BigDecimal.ZERO;
        }
        userExamRecordMapper.updateExamScore(request.getStudentId(), request.getExamId(), total);
        userExamRecordMapper.updateTeacherScore(request.getStudentId(), request.getExamId(), total);

        courseService.recalculateAndUpdateStudentScore(exam.getCourseId(), request.getStudentId());
        courseService.updateComprehensiveScoreForStudent(request.getStudentId());
    }

    // ==================== 供管理员复用: 教师注销级联清理 ====================

    /**
     * 删除某教师发布的全部考试及其题目配置/答题记录/总记录(管理员注销教师账号时调用)。
     * 正常情况下教师的考试都挂在其名下课程下, 会随课程级联删除一并清理;
     * 这里再按发布者ID做一次兜底清理, 避免出现遗留数据。
     */
    @Transactional
    public void deleteExamsByPublisher(Long teacherId) {
        examQuestionAnswerMapper.deleteByPublisherId(teacherId);
        userExamRecordMapper.deleteByPublisherId(teacherId);
        examQuestionMapper.deleteByPublisherId(teacherId);
        examMapper.deleteByPublisherId(teacherId);
    }

    // ==================== 内部工具方法 ====================

    private ExamReviewResponse buildReviewResponse(Long examId, Long studentId,
                                                     List<ExamQuestionAnswer> answers, UserExamRecord record,
                                                     List<ExamQuestion> examQuestions) {
        Map<String, ExamQuestion> eqMap = toExamQuestionMap(
                examQuestions != null ? examQuestions : examQuestionMapper.findByExamId(examId));
        List<QuestionView> views = questionService.resolveQuestions(answers.stream()
                .map(a -> new QuestionService.QuestionRef(a.getQuestionId(), a.getQuestionType()))
                .collect(Collectors.toList()), false);
        List<AnswerReviewItem> items = new ArrayList<>();
        for (int i = 0; i < answers.size(); i++) {
            ExamQuestionAnswer answer = answers.get(i);
            ExamQuestion eq = eqMap.get(key(answer.getQuestionId(), answer.getQuestionType()));
            AnswerReviewItem item = new AnswerReviewItem();
            item.setFullScore(eq != null ? eq.getScore() : answer.getScore());
            item.setScore(answer.getScore());
            item.setStudentAnswer(answer.getStudentAnswer());
            item.setAnnotation(formatAnnotationForReview(answer.getAnnotation(), answer.getQuestionType()));
            item.setQuestion(views.get(i));
            items.add(item);
        }
        ExamReviewResponse response = new ExamReviewResponse();
        response.setExamId(examId);
        response.setStudentId(studentId);
        response.setExamScore(record != null ? record.getExamScore() : null);
        response.setTeacherScore(record != null ? record.getTeacherScore() : null);
        response.setAnswers(items);
        return response;
    }

    private ExamStartResponse toStartResponse(Exam exam, UserExamRecord record) {
        ExamStartResponse response = new ExamStartResponse();
        response.setExamId(exam.getExamId());
        response.setStudentId(record.getStudentId());
        response.setStartTime(record.getStartTime());
        response.setTimeLimit(exam.getTimeLimit());
        response.setDeadline(record.getStartTime().plusMinutes(exam.getTimeLimit()));
        return response;
    }

    private Map<String, ExamQuestion> toExamQuestionMap(List<ExamQuestion> examQuestions) {
        Map<String, ExamQuestion> map = new HashMap<>();
        if (examQuestions != null) {
            for (ExamQuestion eq : examQuestions) {
                map.put(key(eq.getQuestionId(), eq.getQuestionType()), eq);
            }
        }
        return map;
    }

    private void addToErrorBook(Long studentId, Long questionId, Integer questionType) {
        if (errorBookMapper.find(studentId, questionId, questionType) == null) {
            com.example.bishe.entity.ErrorBook errorBook = new com.example.bishe.entity.ErrorBook();
            errorBook.setStudentId(studentId);
            errorBook.setQuestionId(questionId);
            errorBook.setQuestionType(questionType);
            errorBookMapper.insert(errorBook);
        }
    }

    private Map<String, String> toAnswerMap(List<AnswerItem> answers) {
        Map<String, String> map = new HashMap<>();
        if (answers != null) {
            for (AnswerItem item : answers) {
                if (item.getQuestionId() != null && item.getQuestionType() != null) {
                    map.put(key(item.getQuestionId(), item.getQuestionType()), item.getStudentAnswer());
                }
            }
        }
        return map;
    }

    private String key(Long questionId, Integer questionType) {
        return questionId + "_" + questionType;
    }

    private List<QuestionScoreItem> validateQuestionItems(List<QuestionScoreItem> questions) {
        if (questions == null || questions.isEmpty()) {
            throw new BusinessException("考试题目列表不能为空");
        }
        Set<String> seen = new HashSet<>();
        for (QuestionScoreItem item : questions) {
            if (!QuestionType.isValid(item.getQuestionType())) {
                throw new BusinessException("题目类型不合法: " + item.getQuestionType());
            }
            if (item.getQuestionId() == null) {
                throw new BusinessException("题目ID不能为空");
            }
            if (item.getScore() == null || item.getScore().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("题目分值必须大于0(题目ID=" + item.getQuestionId() + ")");
            }
            if (!questionService.existsQuestion(item.getQuestionId(), item.getQuestionType())) {
                throw new BusinessException("题目不存在(题目ID=" + item.getQuestionId() + ")");
            }
            String key = key(item.getQuestionId(), item.getQuestionType());
            if (!seen.add(key)) {
                throw new BusinessException("题目重复(题目ID=" + item.getQuestionId() + ")");
            }
        }
        return questions;
    }

    private Exam requireExam(Long examId) {
        if (examId == null) {
            throw new BusinessException("考试ID不能为空");
        }
        Exam exam = examMapper.findById(examId);
        if (exam == null) {
            throw new BusinessException("因为一些原因，该考试丢失了");
        }
        return exam;
    }

    private Exam requireOwnedExam(Long teacherId, Long examId) {
        Exam exam = requireExam(examId);
        Course course = courseMapper.findById(exam.getCourseId());
        if (course == null) {
            throw new BusinessException("考试所属课程不存在");
        }
        if (!course.getTeacherId().equals(teacherId) && !teacherId.equals(exam.getPublisherId())) {
            throw new BusinessException("无权批改该考试(非课程所有者)");
        }
        return exam;
    }

    private int normalizeHideFlag(Integer hideResultUntilPublish) {
        return hideResultUntilPublish != null && hideResultUntilPublish == RESULT_HIDDEN ? RESULT_HIDDEN : 0;
    }

    private boolean canStudentViewResult(Exam exam) {
        if (exam.getHideResultUntilPublish() == null || exam.getHideResultUntilPublish() != RESULT_HIDDEN) {
            return true;
        }
        return exam.getIsResultPublished() != null && exam.getIsResultPublished() == RESULT_PUBLISHED;
    }

    private void assertStudentCanViewResult(Exam exam, Long studentId) {
        if (studentId == null) {
            return;
        }
        if (!canStudentViewResult(exam)) {
            throw new BusinessException("教师尚未公布本次考试成绩与答案, 暂不可查看复盘");
        }
    }

    private void maskReviewForHiddenResult(ExamReviewResponse response) {
        response.setExamScore(null);
        response.setTeacherScore(null);
        if (response.getAnswers() != null) {
            for (AnswerReviewItem item : response.getAnswers()) {
                item.setScore(null);
                item.setAnnotation("成绩待教师公布后可见");
                if (item.getQuestion() != null) {
                    item.getQuestion().setAnswer(null);
                }
            }
        }
    }

    private String resolveGradedAnnotation(String annotation) {
        if (annotation == null || annotation.trim().isEmpty() || PENDING_GRADE_ANNOTATION.equals(annotation.trim())) {
            return "";
        }
        return annotation.trim();
    }

    private String formatAnnotationForReview(String annotation, Integer questionType) {
        if (QuestionType.SOLVE == questionType) {
            if (annotation == null || annotation.trim().isEmpty()) {
                return GRADED_ANNOTATION;
            }
            if (PENDING_GRADE_ANNOTATION.equals(annotation.trim())) {
                return "待批阅";
            }
        }
        return annotation;
    }
}
