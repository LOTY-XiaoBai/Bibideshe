package com.example.bishe.service;

import com.example.bishe.common.BusinessException;
import com.example.bishe.common.QuestionType;
import com.example.bishe.dto.QuestionCreateRequest;
import com.example.bishe.dto.QuestionOptionView;
import com.example.bishe.dto.QuestionView;
import com.example.bishe.entity.ExamQuestion;
import com.example.bishe.entity.ExamQuestionAnswer;
import com.example.bishe.entity.HomeworkQuestion;
import com.example.bishe.entity.HomeworkQuestionAnswer;
import com.example.bishe.entity.QuestionAnswer;
import com.example.bishe.entity.QuestionChoice;
import com.example.bishe.entity.QuestionChoiceOption;
import com.example.bishe.entity.QuestionJudge;
import com.example.bishe.mapper.ExamQuestionAnswerMapper;
import com.example.bishe.mapper.ExamQuestionMapper;
import com.example.bishe.mapper.HomeworkQuestionAnswerMapper;
import com.example.bishe.mapper.HomeworkQuestionMapper;
import com.example.bishe.mapper.QuestionAnswerMapper;
import com.example.bishe.mapper.QuestionChoiceMapper;
import com.example.bishe.mapper.QuestionChoiceOptionMapper;
import com.example.bishe.mapper.QuestionJudgeMapper;
import com.example.bishe.mapper.TeacherMapper;
import com.example.bishe.mapper.UserExamRecordMapper;
import com.example.bishe.mapper.UserHomeworkRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 题库服务。教师端"做题上传到题库"、管理员端题目审核与删除均在此实现。
 *
 * 选择题/判断题/解答题分别存放在三张独立的表中, 因此本服务对外始终以
 * (questionId, questionType) 二元组唯一标识一道题目, 并通过 {@link QuestionView}
 * 统一屏蔽三张表的差异。
 */
@Service
public class QuestionService {

    private static final String LOST_ANNOTATION = "该题目已被管理员删除, 系统自动给予满分";

    private final QuestionChoiceMapper questionChoiceMapper;
    private final QuestionChoiceOptionMapper questionChoiceOptionMapper;
    private final QuestionJudgeMapper questionJudgeMapper;
    private final QuestionAnswerMapper questionAnswerMapper;
    private final TeacherMapper teacherMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final ExamQuestionAnswerMapper examQuestionAnswerMapper;
    private final UserExamRecordMapper userExamRecordMapper;
    private final HomeworkQuestionMapper homeworkQuestionMapper;
    private final HomeworkQuestionAnswerMapper homeworkQuestionAnswerMapper;
    private final UserHomeworkRecordMapper userHomeworkRecordMapper;

    public QuestionService(QuestionChoiceMapper questionChoiceMapper,
                            QuestionChoiceOptionMapper questionChoiceOptionMapper,
                            QuestionJudgeMapper questionJudgeMapper,
                            QuestionAnswerMapper questionAnswerMapper,
                            TeacherMapper teacherMapper,
                            ExamQuestionMapper examQuestionMapper,
                            ExamQuestionAnswerMapper examQuestionAnswerMapper,
                            UserExamRecordMapper userExamRecordMapper,
                            HomeworkQuestionMapper homeworkQuestionMapper,
                            HomeworkQuestionAnswerMapper homeworkQuestionAnswerMapper,
                            UserHomeworkRecordMapper userHomeworkRecordMapper) {
        this.questionChoiceMapper = questionChoiceMapper;
        this.questionChoiceOptionMapper = questionChoiceOptionMapper;
        this.questionJudgeMapper = questionJudgeMapper;
        this.questionAnswerMapper = questionAnswerMapper;
        this.teacherMapper = teacherMapper;
        this.examQuestionMapper = examQuestionMapper;
        this.examQuestionAnswerMapper = examQuestionAnswerMapper;
        this.userExamRecordMapper = userExamRecordMapper;
        this.homeworkQuestionMapper = homeworkQuestionMapper;
        this.homeworkQuestionAnswerMapper = homeworkQuestionAnswerMapper;
        this.userHomeworkRecordMapper = userHomeworkRecordMapper;
    }

    // ==================== 教师端: 做题上传到题库 ====================

    /**
     * 教师创建一道题目并存入题库(私有或申请公开)。
     */
    @Transactional
    public QuestionView createQuestion(QuestionCreateRequest request) {
        if (request.getPublisherId() == null) {
            throw new BusinessException("发布者ID不能为空");
        }
        if (teacherMapper.findById(request.getPublisherId()) == null) {
            throw new BusinessException("教师不存在");
        }
        if (!QuestionType.isValid(request.getQuestionType())) {
            throw new BusinessException("题目类型不合法");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new BusinessException("题目内容不能为空");
        }
        if (!StringUtils.hasText(request.getAnswer())) {
            throw new BusinessException("答案不能为空");
        }
        int isPublic = (request.getIsPublic() != null && request.getIsPublic() == 1) ? 1 : 0;

        switch (request.getQuestionType()) {
            case QuestionType.CHOICE:
                return createChoiceQuestion(request, isPublic);
            case QuestionType.JUDGE:
                return createJudgeQuestion(request, isPublic);
            default:
                return createAnswerQuestion(request, isPublic);
        }
    }

    private QuestionView createChoiceQuestion(QuestionCreateRequest request, int isPublic) {
        if (request.getOptions() == null || request.getOptions().size() < 2) {
            throw new BusinessException("选择题至少需要2个选项");
        }
        QuestionChoice question = new QuestionChoice();
        question.setContent(request.getContent());
        question.setAnswer(request.getAnswer());
        question.setPublisherId(request.getPublisherId());
        question.setIsPublic(isPublic);
        question.setAuditStatus(0);
        questionChoiceMapper.insert(question);

        List<QuestionChoiceOption> options = new ArrayList<>();
        List<QuestionOptionView> optionViews = new ArrayList<>();
        char code = 'A';
        for (String content : request.getOptions()) {
            String optionCode = String.valueOf(code);
            QuestionChoiceOption option = new QuestionChoiceOption();
            option.setQuestionId(question.getQuestionId());
            option.setOptionCode(optionCode);
            option.setOptionContent(content);
            options.add(option);
            optionViews.add(new QuestionOptionView(optionCode, content));
            code++;
        }
        questionChoiceOptionMapper.batchInsert(options);

        QuestionView view = toView(question);
        view.setOptions(optionViews);
        return view;
    }

    private QuestionView createJudgeQuestion(QuestionCreateRequest request, int isPublic) {
        QuestionJudge question = new QuestionJudge();
        question.setContent(request.getContent());
        question.setAnswer(request.getAnswer());
        question.setPublisherId(request.getPublisherId());
        question.setIsPublic(isPublic);
        question.setAuditStatus(0);
        questionJudgeMapper.insert(question);
        return toView(question);
    }

    private QuestionView createAnswerQuestion(QuestionCreateRequest request, int isPublic) {
        QuestionAnswer question = new QuestionAnswer();
        question.setContent(request.getContent());
        question.setAnswer(request.getAnswer());
        question.setPublisherId(request.getPublisherId());
        question.setIsPublic(isPublic);
        question.setAuditStatus(0);
        questionAnswerMapper.insert(question);
        return toView(question);
    }

    /** 查询某教师自己上传的全部题目(不区分题型) */
    public List<QuestionView> listMine(Long publisherId) {
        if (publisherId == null) {
            throw new BusinessException("教师ID不能为空");
        }
        List<QuestionView> result = new ArrayList<>();
        for (QuestionChoice q : questionChoiceMapper.findByPublisherId(publisherId)) {
            result.add(toChoiceViewWithOptions(q));
        }
        for (QuestionJudge q : questionJudgeMapper.findByPublisherId(publisherId)) {
            result.add(toView(q));
        }
        for (QuestionAnswer q : questionAnswerMapper.findByPublisherId(publisherId)) {
            result.add(toView(q));
        }
        return result;
    }

    /** 查询审核通过的公共题库题目(供教师组卷时挑选) */
    public List<QuestionView> listPublicApproved() {
        List<QuestionView> result = new ArrayList<>();
        for (QuestionChoice q : questionChoiceMapper.findPublicApproved()) {
            result.add(toChoiceViewWithOptions(q));
        }
        for (QuestionJudge q : questionJudgeMapper.findPublicApproved()) {
            result.add(toView(q));
        }
        for (QuestionAnswer q : questionAnswerMapper.findPublicApproved()) {
            result.add(toView(q));
        }
        return result;
    }

    // ==================== 管理员端: 审核与删除 ====================

    /** 查询全部待审核(已申请公开但未通过审核)的题目 */
    public List<QuestionView> listPendingAudit() {
        List<QuestionView> result = new ArrayList<>();
        for (QuestionChoice q : questionChoiceMapper.findPendingAudit()) {
            result.add(toChoiceViewWithOptions(q));
        }
        for (QuestionJudge q : questionJudgeMapper.findPendingAudit()) {
            result.add(toView(q));
        }
        for (QuestionAnswer q : questionAnswerMapper.findPendingAudit()) {
            result.add(toView(q));
        }
        return result;
    }

    /**
     * 管理员审核公共题库题目。通过则标记公开, 驳回则退回教师私有题库(不公开)。
     */
    @Transactional
    public void auditQuestion(Long questionId, Integer questionType, boolean approve) {
        requireValidType(questionType);
        int isPublic = approve ? 1 : 0;
        int auditStatus = approve ? 1 : 0;
        switch (questionType) {
            case QuestionType.CHOICE: {
                QuestionChoice question = questionChoiceMapper.findById(questionId);
                requireExists(question, questionId);
                question.setIsPublic(isPublic);
                question.setAuditStatus(auditStatus);
                questionChoiceMapper.updateAuditStatus(question);
                break;
            }
            case QuestionType.JUDGE: {
                QuestionJudge question = questionJudgeMapper.findById(questionId);
                requireExists(question, questionId);
                question.setIsPublic(isPublic);
                question.setAuditStatus(auditStatus);
                questionJudgeMapper.updateAuditStatus(question);
                break;
            }
            default: {
                QuestionAnswer question = questionAnswerMapper.findById(questionId);
                requireExists(question, questionId);
                question.setIsPublic(isPublic);
                question.setAuditStatus(auditStatus);
                questionAnswerMapper.updateAuditStatus(question);
                break;
            }
        }
    }

    /**
     * 管理员删除题目(无论是否已上传至公共题库均可删除)。
     *
     * 删除逻辑:
     * <ol>
     *   <li>删除题库底层表中的题目记录(选择题还需删除其选项);</li>
     *   <li>已经引用了该题目的考试/作业题目配置(exam_question / homework_question)不会被删除,
     *       以保持试卷总分不变; 之后学生查看该题时会看到占位文案"因为一些原因，该题目丢失了";</li>
     *   <li>已经提交过该题答案的学生, 其得分被直接补满该题的满分, 并重新汇总考试/作业总分。</li>
     * </ol>
     */
    @Transactional
    public void deleteQuestion(Long questionId, Integer questionType) {
        requireValidType(questionType);
        if (!existsQuestion(questionId, questionType)) {
            throw new BusinessException("题目不存在");
        }

        fixExamAnswersForDeletedQuestion(questionId, questionType);
        fixHomeworkAnswersForDeletedQuestion(questionId, questionType);

        switch (questionType) {
            case QuestionType.CHOICE:
                questionChoiceOptionMapper.deleteByQuestionId(questionId);
                questionChoiceMapper.deleteById(questionId);
                break;
            case QuestionType.JUDGE:
                questionJudgeMapper.deleteById(questionId);
                break;
            default:
                questionAnswerMapper.deleteById(questionId);
                break;
        }
    }

    /**
     * 删除某教师上传的全部题目(管理员注销教师账号时调用)。
     * 对每道题走与 {@link #deleteQuestion} 相同的逻辑: 先给其他教师卷子中已作答学生补满分,
     * 再删除题库记录; 试卷题目配置行保留, 之后展示为"因为一些原因，该题目丢失了"。
     */
    @Transactional
    public void deleteAllByPublisher(Long teacherId) {
        if (teacherId == null) {
            return;
        }
        for (QuestionChoice q : questionChoiceMapper.findByPublisherId(teacherId)) {
            deleteQuestion(q.getQuestionId(), QuestionType.CHOICE);
        }
        for (QuestionJudge q : questionJudgeMapper.findByPublisherId(teacherId)) {
            deleteQuestion(q.getQuestionId(), QuestionType.JUDGE);
        }
        for (QuestionAnswer q : questionAnswerMapper.findByPublisherId(teacherId)) {
            deleteQuestion(q.getQuestionId(), QuestionType.SOLVE);
        }
    }

    private void fixExamAnswersForDeletedQuestion(Long questionId, Integer questionType) {
        List<ExamQuestion> examQuestions = examQuestionMapper.findByQuestion(questionId, questionType);
        for (ExamQuestion examQuestion : examQuestions) {
            List<ExamQuestionAnswer> answers = examQuestionAnswerMapper.findByQuestion(questionId, questionType);
            for (ExamQuestionAnswer answer : answers) {
                if (!answer.getExamId().equals(examQuestion.getExamId())) {
                    continue;
                }
                answer.setScore(examQuestion.getScore());
                answer.setAnnotation(LOST_ANNOTATION);
                examQuestionAnswerMapper.updateScore(answer);

                BigDecimal total = examQuestionAnswerMapper.sumScoreByExamAndStudent(answer.getExamId(), answer.getStudentId());
                if (userExamRecordMapper.find(answer.getStudentId(), answer.getExamId()) != null) {
                    userExamRecordMapper.updateExamScore(answer.getStudentId(), answer.getExamId(), total);
                }
            }
        }
    }

    private void fixHomeworkAnswersForDeletedQuestion(Long questionId, Integer questionType) {
        List<HomeworkQuestion> homeworkQuestions = homeworkQuestionMapper.findByQuestion(questionId, questionType);
        for (HomeworkQuestion homeworkQuestion : homeworkQuestions) {
            List<HomeworkQuestionAnswer> answers = homeworkQuestionAnswerMapper.findByQuestion(questionId, questionType);
            for (HomeworkQuestionAnswer answer : answers) {
                if (!answer.getHomeworkId().equals(homeworkQuestion.getHomeworkId())) {
                    continue;
                }
                answer.setScore(homeworkQuestion.getScore());
                answer.setAnnotation(LOST_ANNOTATION);
                homeworkQuestionAnswerMapper.updateScore(answer);

                BigDecimal total = homeworkQuestionAnswerMapper.sumScoreByHomeworkAndStudent(answer.getHomeworkId(), answer.getStudentId());
                if (userHomeworkRecordMapper.find(answer.getStudentId(), answer.getHomeworkId()) != null) {
                    userHomeworkRecordMapper.updateHomeworkScore(answer.getStudentId(), answer.getHomeworkId(), total);
                }
            }
        }
    }

    // ==================== 供其他服务复用的题目解析 ====================

    /** 判断题目是否存在(供组卷时校验题目ID合法性) */
    public boolean existsQuestion(Long questionId, Integer questionType) {
        if (!QuestionType.isValid(questionType) || questionId == null) {
            return false;
        }
        switch (questionType) {
            case QuestionType.CHOICE:
                return questionChoiceMapper.findById(questionId) != null;
            case QuestionType.JUDGE:
                return questionJudgeMapper.findById(questionId) != null;
            default:
                return questionAnswerMapper.findById(questionId) != null;
        }
    }

    /**
     * 解析题目详情, 供组卷/答题/复盘/错题本展示使用。
     * 若题目已被删除, 返回占位视图(content="因为一些原因，该题目丢失了")。
     *
     * @param hideAnswer 是否隐藏正确答案(学生正在作答时应置为true, 避免提前看到答案)
     */
    public QuestionView resolveQuestion(Long questionId, Integer questionType, boolean hideAnswer) {
        QuestionView view;
        switch (questionType == null ? -1 : questionType) {
            case QuestionType.CHOICE: {
                QuestionChoice question = questionChoiceMapper.findById(questionId);
                if (question == null) {
                    return QuestionView.lostPlaceholder(questionId, questionType);
                }
                view = toView(question);
                List<QuestionOptionView> options = new ArrayList<>();
                for (QuestionChoiceOption option : questionChoiceOptionMapper.findByQuestionId(questionId)) {
                    options.add(new QuestionOptionView(option.getOptionCode(), option.getOptionContent()));
                }
                view.setOptions(options);
                break;
            }
            case QuestionType.JUDGE: {
                QuestionJudge question = questionJudgeMapper.findById(questionId);
                if (question == null) {
                    return QuestionView.lostPlaceholder(questionId, questionType);
                }
                view = toView(question);
                break;
            }
            case QuestionType.SOLVE: {
                QuestionAnswer question = questionAnswerMapper.findById(questionId);
                if (question == null) {
                    return QuestionView.lostPlaceholder(questionId, questionType);
                }
                view = toView(question);
                break;
            }
            default:
                return QuestionView.lostPlaceholder(questionId, questionType);
        }
        if (hideAnswer) {
            view.setAnswer(null);
        }
        return view;
    }

    /**
     * 题目引用 (questionId + questionType), 供批量解析使用。
     */
    public static final class QuestionRef {
        private final Long questionId;
        private final Integer questionType;

        public QuestionRef(Long questionId, Integer questionType) {
            this.questionId = questionId;
            this.questionType = questionType;
        }

        public Long getQuestionId() {
            return questionId;
        }

        public Integer getQuestionType() {
            return questionType;
        }
    }

    /**
     * 批量解析题目详情, 按类型分组查询, 避免 N+1。
     * 返回列表与入参 refs 顺序一一对应。
     */
    public List<QuestionView> resolveQuestions(List<QuestionRef> refs, boolean hideAnswer) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        List<Long> choiceIds = refs.stream()
                .filter(r -> Objects.equals(r.getQuestionType(), QuestionType.CHOICE))
                .map(QuestionRef::getQuestionId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<Long> judgeIds = refs.stream()
                .filter(r -> Objects.equals(r.getQuestionType(), QuestionType.JUDGE))
                .map(QuestionRef::getQuestionId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<Long> solveIds = refs.stream()
                .filter(r -> Objects.equals(r.getQuestionType(), QuestionType.SOLVE))
                .map(QuestionRef::getQuestionId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, QuestionChoice> choiceMap = choiceIds.isEmpty() ? Map.of()
                : questionChoiceMapper.findByIds(choiceIds).stream()
                .collect(Collectors.toMap(QuestionChoice::getQuestionId, q -> q, (a, b) -> a));
        Map<Long, QuestionJudge> judgeMap = judgeIds.isEmpty() ? Map.of()
                : questionJudgeMapper.findByIds(judgeIds).stream()
                .collect(Collectors.toMap(QuestionJudge::getQuestionId, q -> q, (a, b) -> a));
        Map<Long, QuestionAnswer> solveMap = solveIds.isEmpty() ? Map.of()
                : questionAnswerMapper.findByIds(solveIds).stream()
                .collect(Collectors.toMap(QuestionAnswer::getQuestionId, q -> q, (a, b) -> a));

        Map<Long, List<QuestionOptionView>> optionMap = new HashMap<>();
        if (!choiceIds.isEmpty()) {
            for (QuestionChoiceOption option : questionChoiceOptionMapper.findByQuestionIds(choiceIds)) {
                optionMap.computeIfAbsent(option.getQuestionId(), k -> new ArrayList<>())
                        .add(new QuestionOptionView(option.getOptionCode(), option.getOptionContent()));
            }
        }

        List<QuestionView> result = new ArrayList<>(refs.size());
        for (QuestionRef ref : refs) {
            QuestionView view;
            Integer type = ref.getQuestionType();
            Long id = ref.getQuestionId();
            if (Objects.equals(type, QuestionType.CHOICE)) {
                QuestionChoice question = choiceMap.get(id);
                if (question == null) {
                    view = QuestionView.lostPlaceholder(id, type);
                } else {
                    view = toView(question);
                    view.setOptions(optionMap.getOrDefault(id, Collections.emptyList()));
                }
            } else if (Objects.equals(type, QuestionType.JUDGE)) {
                QuestionJudge question = judgeMap.get(id);
                view = question == null ? QuestionView.lostPlaceholder(id, type) : toView(question);
            } else if (Objects.equals(type, QuestionType.SOLVE)) {
                QuestionAnswer question = solveMap.get(id);
                view = question == null ? QuestionView.lostPlaceholder(id, type) : toView(question);
            } else {
                view = QuestionView.lostPlaceholder(id, type);
            }
            if (hideAnswer) {
                view.setAnswer(null);
            }
            result.add(view);
        }
        return result;
    }

    /**
     * 判断学生答案是否与标准答案一致(去除首尾空格、忽略大小写)。
     * 题目已丢失时视为无法判定, 返回true以便调用方直接给满分。
     */
    public boolean isAnswerCorrect(Long questionId, Integer questionType, String studentAnswer) {
        String correctAnswer = findRawAnswer(questionId, questionType);
        if (correctAnswer == null) {
            return true;
        }
        if (studentAnswer == null) {
            return false;
        }
        if (Objects.equals(questionType, QuestionType.JUDGE)) {
            return normalizeJudge(correctAnswer).equals(normalizeJudge(studentAnswer));
        }
        return normalize(correctAnswer).equalsIgnoreCase(normalize(studentAnswer));
    }

    private String normalizeJudge(String value) {
        String v = normalize(value).toLowerCase();
        if ("true".equals(v) || "1".equals(v) || "正确".equals(v) || "对".equals(v) || "t".equals(v) || "yes".equals(v)) {
            return "true";
        }
        if ("false".equals(v) || "0".equals(v) || "错误".equals(v) || "错".equals(v) || "f".equals(v) || "no".equals(v)) {
            return "false";
        }
        return v;
    }

    private String findRawAnswer(Long questionId, Integer questionType) {
        if (questionType == null) {
            return null;
        }
        switch (questionType) {
            case QuestionType.CHOICE: {
                QuestionChoice question = questionChoiceMapper.findById(questionId);
                return question == null ? null : question.getAnswer();
            }
            case QuestionType.JUDGE: {
                QuestionJudge question = questionJudgeMapper.findById(questionId);
                return question == null ? null : question.getAnswer();
            }
            case QuestionType.SOLVE: {
                QuestionAnswer question = questionAnswerMapper.findById(questionId);
                return question == null ? null : question.getAnswer();
            }
            default:
                return null;
        }
    }

    private String normalize(String value) {
        return value.trim().replace(" ", "");
    }

    private void requireValidType(Integer questionType) {
        if (!QuestionType.isValid(questionType)) {
            throw new BusinessException("题目类型不合法");
        }
    }

    private void requireExists(Object question, Long questionId) {
        if (question == null) {
            throw new BusinessException("题目不存在: " + questionId);
        }
    }

    private QuestionView toChoiceViewWithOptions(QuestionChoice question) {
        QuestionView view = toView(question);
        List<QuestionOptionView> options = new ArrayList<>();
        for (QuestionChoiceOption option : questionChoiceOptionMapper.findByQuestionId(question.getQuestionId())) {
            options.add(new QuestionOptionView(option.getOptionCode(), option.getOptionContent()));
        }
        view.setOptions(options);
        return view;
    }

    private QuestionView toView(QuestionChoice question) {
        QuestionView view = new QuestionView();
        view.setQuestionId(question.getQuestionId());
        view.setQuestionType(QuestionType.CHOICE);
        view.setContent(question.getContent());
        view.setAnswer(question.getAnswer());
        view.setPublisherId(question.getPublisherId());
        view.setIsPublic(question.getIsPublic());
        view.setAuditStatus(question.getAuditStatus());
        return view;
    }

    private QuestionView toView(QuestionJudge question) {
        QuestionView view = new QuestionView();
        view.setQuestionId(question.getQuestionId());
        view.setQuestionType(QuestionType.JUDGE);
        view.setContent(question.getContent());
        view.setAnswer(question.getAnswer());
        view.setPublisherId(question.getPublisherId());
        view.setIsPublic(question.getIsPublic());
        view.setAuditStatus(question.getAuditStatus());
        return view;
    }

    private QuestionView toView(QuestionAnswer question) {
        QuestionView view = new QuestionView();
        view.setQuestionId(question.getQuestionId());
        view.setQuestionType(QuestionType.SOLVE);
        view.setContent(question.getContent());
        view.setAnswer(question.getAnswer());
        view.setPublisherId(question.getPublisherId());
        view.setIsPublic(question.getIsPublic());
        view.setAuditStatus(question.getAuditStatus());
        return view;
    }
}
