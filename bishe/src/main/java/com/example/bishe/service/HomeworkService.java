package com.example.bishe.service;

import com.example.bishe.common.BusinessException;
import com.example.bishe.common.AuthUser;
import com.example.bishe.common.QuestionType;
import com.example.bishe.common.UserRole;
import com.example.bishe.dto.AnswerItem;
import com.example.bishe.dto.AnswerReviewItem;
import com.example.bishe.dto.GradeAnswerRequest;
import com.example.bishe.dto.HomeworkDetailResponse;
import com.example.bishe.dto.HomeworkPublishRequest;
import com.example.bishe.dto.HomeworkReviewResponse;
import com.example.bishe.dto.HomeworkSubmitRequest;
import com.example.bishe.dto.PendingGradeItemVO;
import com.example.bishe.dto.QuestionItemView;
import com.example.bishe.dto.QuestionScoreItem;
import com.example.bishe.dto.QuestionView;
import com.example.bishe.dto.ScoreRankItemVO;
import com.example.bishe.entity.Course;
import com.example.bishe.entity.ErrorBook;
import com.example.bishe.entity.Homework;
import com.example.bishe.entity.HomeworkQuestion;
import com.example.bishe.entity.HomeworkQuestionAnswer;
import com.example.bishe.entity.Student;
import com.example.bishe.entity.UserHomeworkRecord;
import com.example.bishe.mapper.CourseMapper;
import com.example.bishe.mapper.CoursePersonnelMapper;
import com.example.bishe.mapper.ErrorBookMapper;
import com.example.bishe.mapper.HomeworkMapper;
import com.example.bishe.mapper.HomeworkQuestionAnswerMapper;
import com.example.bishe.mapper.HomeworkQuestionMapper;
import com.example.bishe.mapper.StudentMapper;
import com.example.bishe.mapper.UserHomeworkRecordMapper;
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
 * 作业服务: 教师发布作业, 学生作答提交并自动批改, 答错自动加入错题本。
 */
@Service
public class HomeworkService {

    private static final String LOST_QUESTION_ANNOTATION = "该题目已被管理员删除, 系统自动给予满分";

    /** 解答题等待教师人工批改的标记 */
    public static final String PENDING_GRADE_ANNOTATION = "待教师批改";

    /** 解答题已批阅标记(返回给前端展示) */
    public static final String GRADED_ANNOTATION = "已批阅";

    private static final int RESULT_HIDDEN = 1;
    private static final int RESULT_PUBLISHED = 1;

    private final HomeworkMapper homeworkMapper;
    private final HomeworkQuestionMapper homeworkQuestionMapper;
    private final HomeworkQuestionAnswerMapper homeworkQuestionAnswerMapper;
    private final UserHomeworkRecordMapper userHomeworkRecordMapper;
    private final CourseMapper courseMapper;
    private final CoursePersonnelMapper coursePersonnelMapper;
    private final ErrorBookMapper errorBookMapper;
    private final QuestionService questionService;
    private final CourseService courseService;
    private final StudentMapper studentMapper;

    public HomeworkService(HomeworkMapper homeworkMapper,
                            HomeworkQuestionMapper homeworkQuestionMapper,
                            HomeworkQuestionAnswerMapper homeworkQuestionAnswerMapper,
                            UserHomeworkRecordMapper userHomeworkRecordMapper,
                            CourseMapper courseMapper,
                            CoursePersonnelMapper coursePersonnelMapper,
                            ErrorBookMapper errorBookMapper,
                            QuestionService questionService,
                            CourseService courseService,
                            StudentMapper studentMapper) {
        this.homeworkMapper = homeworkMapper;
        this.homeworkQuestionMapper = homeworkQuestionMapper;
        this.homeworkQuestionAnswerMapper = homeworkQuestionAnswerMapper;
        this.userHomeworkRecordMapper = userHomeworkRecordMapper;
        this.courseMapper = courseMapper;
        this.coursePersonnelMapper = coursePersonnelMapper;
        this.errorBookMapper = errorBookMapper;
        this.questionService = questionService;
        this.courseService = courseService;
        this.studentMapper = studentMapper;
    }

    // ==================== 教师端: 发布作业 ====================

    @Transactional
    public Homework publishHomework(HomeworkPublishRequest request) {
        if (request.getTeacherId() == null || request.getCourseId() == null) {
            throw new BusinessException("教师ID与课程ID不能为空");
        }
        Course course = courseMapper.findById(request.getCourseId());
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        if (!course.getTeacherId().equals(request.getTeacherId())) {
            throw new BusinessException("无权在该课程下发布作业(非本人创建)");
        }
        if (request.getDeadline() == null) {
            throw new BusinessException("截止时间不能为空");
        }
        if (request.getDeadline().isBefore(LocalDateTime.now())) {
            throw new BusinessException("截止时间不能早于当前时间");
        }
        List<QuestionScoreItem> items = validateQuestionItems(request.getQuestions());

        Homework homework = new Homework();
        homework.setCourseId(request.getCourseId());
        homework.setPublisherId(request.getTeacherId());
        homework.setDeadline(request.getDeadline());
        homework.setHideResultUntilPublish(normalizeHideFlag(request.getHideResultUntilPublish()));
        homework.setIsResultPublished(0);
        homeworkMapper.insert(homework);

        List<HomeworkQuestion> homeworkQuestions = new ArrayList<>();
        for (QuestionScoreItem item : items) {
            HomeworkQuestion hq = new HomeworkQuestion();
            hq.setHomeworkId(homework.getHomeworkId());
            hq.setQuestionId(item.getQuestionId());
            hq.setQuestionType(item.getQuestionType());
            hq.setScore(item.getScore());
            homeworkQuestions.add(hq);
        }
        homeworkQuestionMapper.batchInsert(homeworkQuestions);
        return homework;
    }

    // ==================== 查询 ====================

    public HomeworkDetailResponse getHomeworkDetail(Long homeworkId, AuthUser user) {
        Homework homework = requireHomework(homeworkId);
        boolean hideAnswer = authorizeCourseAccess(homework.getCourseId(), user);
        List<HomeworkQuestion> homeworkQuestions = homeworkQuestionMapper.findByHomeworkId(homeworkId);
        List<QuestionView> views = questionService.resolveQuestions(homeworkQuestions.stream()
                .map(hq -> new QuestionService.QuestionRef(hq.getQuestionId(), hq.getQuestionType()))
                .collect(Collectors.toList()), hideAnswer);
        List<QuestionItemView> items = new ArrayList<>();
        for (int i = 0; i < homeworkQuestions.size(); i++) {
            items.add(new QuestionItemView(homeworkQuestions.get(i).getScore(), views.get(i)));
        }
        HomeworkDetailResponse response = new HomeworkDetailResponse();
        response.setHomework(homework);
        response.setQuestions(items);
        return response;
    }

    public List<Homework> listByCourse(Long courseId, AuthUser user) {
        if (courseId == null) {
            throw new BusinessException("课程ID不能为空");
        }
        authorizeCourseAccess(courseId, user);
        return homeworkMapper.findByCourseId(courseId);
    }

    /**
     * 校验课程资源访问权限。学生仅可访问已选课程且答案始终隐藏；
     * 教师仅可访问本人课程；系统管理员可访问并查看答案。
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
                throw new BusinessException(403, "无权访问其他教师课程的作业");
            }
            return false;
        }
        if (UserRole.STUDENT.equals(user.getRole())) {
            if (user.getUserId() == null || coursePersonnelMapper.find(courseId, user.getUserId()) == null) {
                throw new BusinessException(403, "您未选修该课程，无法访问作业");
            }
            return true;
        }
        throw new BusinessException(403, "当前身份无权访问作业");
    }

    public List<Homework> listByTeacher(Long teacherId) {
        if (teacherId == null) {
            throw new BusinessException("教师ID不能为空");
        }
        return homeworkMapper.findByPublisherId(teacherId);
    }

    public List<UserHomeworkRecord> listRecordsByStudent(Long studentId) {
        if (studentId == null) {
            throw new BusinessException("学生ID不能为空");
        }
        return userHomeworkRecordMapper.findByStudentId(studentId);
    }

    /** 学生查看自己某次作业的作答复盘(提交后可反复查看) */
    public HomeworkReviewResponse reviewHomework(Long homeworkId, Long studentId) {
        Homework homework = requireHomework(homeworkId);
        assertStudentCanViewResult(homework, studentId);
        List<HomeworkQuestionAnswer> answers = homeworkQuestionAnswerMapper.findByHomeworkAndStudent(homeworkId, studentId);
        if (answers.isEmpty()) {
            throw new BusinessException("您尚未提交该作业");
        }
        UserHomeworkRecord record = userHomeworkRecordMapper.find(studentId, homeworkId);
        return buildReviewResponse(homeworkId, studentId, answers, record);
    }

    /** 教师公布作业成绩与答案 */
    @Transactional
    public void publishResult(Long teacherId, Long homeworkId) {
        requireOwnedHomework(teacherId, homeworkId);
        homeworkMapper.updateResultPublished(homeworkId, RESULT_PUBLISHED);
    }

    /** 教师查看某次作业全部学生得分排名 */
    public List<ScoreRankItemVO> listScoreRanking(Long teacherId, Long homeworkId) {
        requireOwnedHomework(teacherId, homeworkId);
        List<UserHomeworkRecord> records = userHomeworkRecordMapper.findByHomeworkId(homeworkId);
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<Long> studentIds = records.stream().map(UserHomeworkRecord::getStudentId).distinct().collect(Collectors.toList());
        Map<Long, Student> studentMap = new HashMap<>();
        if (!studentIds.isEmpty()) {
            for (Student s : studentMapper.findByIds(studentIds)) {
                studentMap.put(s.getStudentId(), s);
            }
        }
        List<ScoreRankItemVO> items = new ArrayList<>();
        for (UserHomeworkRecord record : records) {
            ScoreRankItemVO item = new ScoreRankItemVO();
            item.setStudentId(record.getStudentId());
            Student student = studentMap.get(record.getStudentId());
            if (student != null) {
                item.setNickname(student.getNickname());
                item.setAvatar(student.getAvatar());
            }
            item.setAutoScore(record.getHomeworkScore());
            item.setTeacherScore(record.getTeacherScore());
            BigDecimal finalScore = record.getTeacherScore() != null
                    && record.getTeacherScore().compareTo(BigDecimal.ZERO) > 0
                    ? record.getTeacherScore()
                    : (record.getHomeworkScore() == null ? BigDecimal.ZERO : record.getHomeworkScore());
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

    /** 教师查看指定学生的作业作答 */
    public HomeworkReviewResponse reviewHomeworkForTeacher(Long teacherId, Long homeworkId, Long studentId) {
        requireOwnedHomework(teacherId, homeworkId);
        if (studentId == null) {
            throw new BusinessException("学生ID不能为空");
        }
        List<HomeworkQuestionAnswer> answers =
                homeworkQuestionAnswerMapper.findByHomeworkAndStudent(homeworkId, studentId);
        if (answers.isEmpty()) {
            throw new BusinessException("该学生尚未提交作业");
        }
        UserHomeworkRecord record = userHomeworkRecordMapper.find(studentId, homeworkId);
        return buildReviewResponse(homeworkId, studentId, answers, record);
    }

    // ==================== 学生端: 作答并提交 ====================

    @Transactional
    public HomeworkReviewResponse submitHomework(HomeworkSubmitRequest request) {
        if (request.getStudentId() == null || request.getHomeworkId() == null) {
            throw new BusinessException("学生ID与作业ID不能为空");
        }
        Homework homework = requireHomework(request.getHomeworkId());
        if (coursePersonnelMapper.find(homework.getCourseId(), request.getStudentId()) == null) {
            throw new BusinessException("您未选修该课程, 无法提交此作业");
        }
        if (homework.getDeadline() != null && LocalDateTime.now().isAfter(homework.getDeadline())) {
            throw new BusinessException("该作业已超过截止时间, 无法提交");
        }

        // 作业提交必须幂等：防止重复请求覆盖已经入库的答卷。
        if (homeworkQuestionAnswerMapper.countByHomeworkAndStudent(homework.getHomeworkId(), request.getStudentId()) > 0) {
            throw new BusinessException(409, "该作业已提交，请勿重复提交");
        }

        List<HomeworkQuestion> homeworkQuestions = homeworkQuestionMapper.findByHomeworkId(homework.getHomeworkId());
        if (homeworkQuestions.isEmpty()) {
            throw new BusinessException("该作业尚未配置任何题目");
        }
        Map<String, String> answerMap = toAnswerMap(request.getAnswers());

        BigDecimal total = BigDecimal.ZERO;
        List<HomeworkQuestionAnswer> savedAnswers = new ArrayList<>();
        for (HomeworkQuestion hq : homeworkQuestions) {
            String key = key(hq.getQuestionId(), hq.getQuestionType());
            String studentAnswer = answerMap.get(key);

            HomeworkQuestionAnswer answer = new HomeworkQuestionAnswer();
            answer.setHomeworkId(homework.getHomeworkId());
            answer.setQuestionId(hq.getQuestionId());
            answer.setQuestionType(hq.getQuestionType());
            answer.setStudentId(request.getStudentId());
            answer.setCourseId(homework.getCourseId());
            answer.setStudentAnswer(studentAnswer);

            if (!questionService.existsQuestion(hq.getQuestionId(), hq.getQuestionType())) {
                answer.setScore(hq.getScore());
                answer.setAnnotation(LOST_QUESTION_ANNOTATION);
            } else if (QuestionType.SOLVE == hq.getQuestionType()) {
                answer.setScore(BigDecimal.ZERO);
                answer.setAnnotation(PENDING_GRADE_ANNOTATION);
            } else if (questionService.isAnswerCorrect(hq.getQuestionId(), hq.getQuestionType(), studentAnswer)) {
                answer.setScore(hq.getScore());
            } else {
                answer.setScore(BigDecimal.ZERO);
                addToErrorBook(request.getStudentId(), hq.getQuestionId(), hq.getQuestionType());
            }

            homeworkQuestionAnswerMapper.insert(answer);
            savedAnswers.add(answer);
            total = total.add(answer.getScore() == null ? BigDecimal.ZERO : answer.getScore());
        }

        UserHomeworkRecord record = new UserHomeworkRecord();
        record.setStudentId(request.getStudentId());
        record.setHomeworkId(homework.getHomeworkId());
        record.setHomeworkScore(total);
        record.setTeacherScore(total);
        userHomeworkRecordMapper.insert(record);

        courseService.recalculateAndUpdateStudentScore(homework.getCourseId(), request.getStudentId());
        courseService.updateComprehensiveScoreForStudent(request.getStudentId());

        HomeworkReviewResponse response = buildReviewResponse(homework.getHomeworkId(), request.getStudentId(), savedAnswers, record);
        if (!canStudentViewResult(homework)) {
            maskReviewForHiddenResult(response);
        }
        return response;
    }

    // ==================== 教师端: 解答题人工批改 ====================

    public List<PendingGradeItemVO> listPendingGrade(Long teacherId, Long homeworkId) {
        Homework homework = requireOwnedHomework(teacherId, homeworkId);
        List<HomeworkQuestionAnswer> answers = homeworkQuestionAnswerMapper.findPendingSolveByHomework(homeworkId);
        if (answers.isEmpty()) {
            return List.of();
        }
        Map<String, HomeworkQuestion> hqMap = toHomeworkQuestionMap(homeworkQuestionMapper.findByHomeworkId(homeworkId));
        List<QuestionView> views = questionService.resolveQuestions(answers.stream()
                .map(a -> new QuestionService.QuestionRef(a.getQuestionId(), a.getQuestionType()))
                .collect(Collectors.toList()), false);
        List<PendingGradeItemVO> result = new ArrayList<>();
        for (int i = 0; i < answers.size(); i++) {
            HomeworkQuestionAnswer answer = answers.get(i);
            HomeworkQuestion hq = hqMap.get(key(answer.getQuestionId(), answer.getQuestionType()));
            PendingGradeItemVO vo = new PendingGradeItemVO();
            vo.setHomeworkId(homeworkId);
            vo.setQuestionId(answer.getQuestionId());
            vo.setQuestionType(answer.getQuestionType());
            vo.setStudentId(answer.getStudentId());
            vo.setCourseId(homework.getCourseId());
            vo.setStudentAnswer(answer.getStudentAnswer());
            vo.setFullScore(hq != null ? hq.getScore() : null);
            vo.setAnnotation(answer.getAnnotation());
            vo.setQuestion(views.get(i));
            result.add(vo);
        }
        return result;
    }

    @Transactional
    public void gradeSolveAnswer(Long teacherId, GradeAnswerRequest request) {
        if (request.getHomeworkId() == null || request.getQuestionId() == null
                || request.getQuestionType() == null || request.getStudentId() == null) {
            throw new BusinessException("作业ID/题目ID/题目类型/学生ID不能为空");
        }
        if (request.getQuestionType() != QuestionType.SOLVE) {
            throw new BusinessException("仅解答题支持人工批改");
        }
        if (request.getScore() == null || request.getScore().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("分数不能为空且不能为负数");
        }
        Homework homework = requireOwnedHomework(teacherId, request.getHomeworkId());
        HomeworkQuestion hq = homeworkQuestionMapper.find(
                request.getHomeworkId(), request.getQuestionId(), request.getQuestionType());
        if (hq == null) {
            throw new BusinessException("该作业不包含此题目");
        }
        if (request.getScore().compareTo(hq.getScore()) > 0) {
            throw new BusinessException("分数不能超过该题满分(" + hq.getScore() + ")");
        }
        HomeworkQuestionAnswer answer = homeworkQuestionAnswerMapper.find(
                request.getHomeworkId(), request.getQuestionId(), request.getQuestionType(), request.getStudentId());
        if (answer == null) {
            throw new BusinessException("未找到该学生的作答记录");
        }

        answer.setScore(request.getScore());
        answer.setAnnotation(resolveGradedAnnotation(request.getAnnotation()));
        homeworkQuestionAnswerMapper.updateScore(answer);

        if (request.getScore().compareTo(hq.getScore()) < 0) {
            addToErrorBook(request.getStudentId(), request.getQuestionId(), request.getQuestionType());
        }

        BigDecimal total = homeworkQuestionAnswerMapper.sumScoreByHomeworkAndStudent(
                request.getHomeworkId(), request.getStudentId());
        if (total == null) {
            total = BigDecimal.ZERO;
        }
        userHomeworkRecordMapper.updateHomeworkScore(request.getStudentId(), request.getHomeworkId(), total);
        userHomeworkRecordMapper.updateTeacherScore(request.getStudentId(), request.getHomeworkId(), total);

        courseService.recalculateAndUpdateStudentScore(homework.getCourseId(), request.getStudentId());
        courseService.updateComprehensiveScoreForStudent(request.getStudentId());
    }

    // ==================== 供管理员复用: 教师注销级联清理 ====================

    /**
     * 删除某教师发布的全部作业及其题目配置/答题记录/总记录(管理员注销教师账号时调用)。
     */
    @Transactional
    public void deleteHomeworksByPublisher(Long teacherId) {
        homeworkQuestionAnswerMapper.deleteByPublisherId(teacherId);
        userHomeworkRecordMapper.deleteByPublisherId(teacherId);
        homeworkQuestionMapper.deleteByPublisherId(teacherId);
        homeworkMapper.deleteByPublisherId(teacherId);
    }

    // ==================== 内部工具方法 ====================

    private HomeworkReviewResponse buildReviewResponse(Long homeworkId, Long studentId,
                                                          List<HomeworkQuestionAnswer> answers, UserHomeworkRecord record) {
        Map<String, HomeworkQuestion> hqMap = toHomeworkQuestionMap(homeworkQuestionMapper.findByHomeworkId(homeworkId));
        List<QuestionView> views = questionService.resolveQuestions(answers.stream()
                .map(a -> new QuestionService.QuestionRef(a.getQuestionId(), a.getQuestionType()))
                .collect(Collectors.toList()), false);
        List<AnswerReviewItem> items = new ArrayList<>();
        for (int i = 0; i < answers.size(); i++) {
            HomeworkQuestionAnswer answer = answers.get(i);
            HomeworkQuestion hq = hqMap.get(key(answer.getQuestionId(), answer.getQuestionType()));
            AnswerReviewItem item = new AnswerReviewItem();
            item.setFullScore(hq != null ? hq.getScore() : answer.getScore());
            item.setScore(answer.getScore());
            item.setStudentAnswer(answer.getStudentAnswer());
            item.setAnnotation(formatAnnotationForReview(answer.getAnnotation(), answer.getQuestionType()));
            item.setQuestion(views.get(i));
            items.add(item);
        }
        HomeworkReviewResponse response = new HomeworkReviewResponse();
        response.setHomeworkId(homeworkId);
        response.setStudentId(studentId);
        response.setHomeworkScore(record != null ? record.getHomeworkScore() : null);
        response.setTeacherScore(record != null ? record.getTeacherScore() : null);
        response.setAnswers(items);
        return response;
    }

    private Map<String, HomeworkQuestion> toHomeworkQuestionMap(List<HomeworkQuestion> questions) {
        Map<String, HomeworkQuestion> map = new HashMap<>();
        if (questions != null) {
            for (HomeworkQuestion hq : questions) {
                map.put(key(hq.getQuestionId(), hq.getQuestionType()), hq);
            }
        }
        return map;
    }

    private void addToErrorBook(Long studentId, Long questionId, Integer questionType) {
        if (errorBookMapper.find(studentId, questionId, questionType) == null) {
            ErrorBook errorBook = new ErrorBook();
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
            throw new BusinessException("作业题目列表不能为空");
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

    private Homework requireHomework(Long homeworkId) {
        if (homeworkId == null) {
            throw new BusinessException("作业ID不能为空");
        }
        Homework homework = homeworkMapper.findById(homeworkId);
        if (homework == null) {
            throw new BusinessException("因为一些原因，该作业丢失了");
        }
        return homework;
    }

    private Homework requireOwnedHomework(Long teacherId, Long homeworkId) {
        Homework homework = requireHomework(homeworkId);
        Course course = courseMapper.findById(homework.getCourseId());
        if (course == null) {
            throw new BusinessException("作业所属课程不存在");
        }
        if (!course.getTeacherId().equals(teacherId) && !teacherId.equals(homework.getPublisherId())) {
            throw new BusinessException("无权批改该作业(非课程所有者)");
        }
        return homework;
    }

    private int normalizeHideFlag(Integer hideResultUntilPublish) {
        return hideResultUntilPublish != null && hideResultUntilPublish == RESULT_HIDDEN ? RESULT_HIDDEN : 0;
    }

    private boolean canStudentViewResult(Homework homework) {
        if (homework.getHideResultUntilPublish() == null || homework.getHideResultUntilPublish() != RESULT_HIDDEN) {
            return true;
        }
        return homework.getIsResultPublished() != null && homework.getIsResultPublished() == RESULT_PUBLISHED;
    }

    private void assertStudentCanViewResult(Homework homework, Long studentId) {
        if (studentId == null) {
            return;
        }
        if (!canStudentViewResult(homework)) {
            throw new BusinessException("教师尚未公布本次作业成绩与答案, 暂不可查看复盘");
        }
    }

    private void maskReviewForHiddenResult(HomeworkReviewResponse response) {
        response.setHomeworkScore(null);
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
