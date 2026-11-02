package com.example.bishe.service;

import com.example.bishe.common.BusinessException;
import com.example.bishe.common.UserRole;
import com.example.bishe.dto.ClassRankItemVO;
import com.example.bishe.dto.ClassRankVO;
import com.example.bishe.dto.ScorePkVO;
import com.example.bishe.dto.UserProfileVO;
import com.example.bishe.entity.Course;
import com.example.bishe.entity.CoursePersonnel;
import com.example.bishe.entity.Student;
import com.example.bishe.mapper.CourseMapper;
import com.example.bishe.mapper.CoursePersonnelMapper;
import com.example.bishe.mapper.StudentMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 成绩对比: 好友 PK + 班级排名(不展示他人具体分数)。
 */
@Service
public class ScoreCompareService {

    private final FriendService friendService;
    private final SocialUserService socialUserService;
    private final CourseMapper courseMapper;
    private final CoursePersonnelMapper coursePersonnelMapper;
    private final StudentMapper studentMapper;

    public ScoreCompareService(FriendService friendService,
                               SocialUserService socialUserService,
                               CourseMapper courseMapper,
                               CoursePersonnelMapper coursePersonnelMapper,
                               StudentMapper studentMapper) {
        this.friendService = friendService;
        this.socialUserService = socialUserService;
        this.courseMapper = courseMapper;
        this.coursePersonnelMapper = coursePersonnelMapper;
        this.studentMapper = studentMapper;
    }

    /**
     * 与好友 PK。
     * courseId 为空时对比综合评分(comprehensive_score);
     * 指定课程时双方须均为该班学生, 对比课程成绩。
     */
    public ScorePkVO pkWithFriend(Long myId, Long friendId, Long courseId) {
        if (friendId == null) {
            throw new BusinessException("好友ID不能为空");
        }
        friendService.requireFriendship(myId, friendId);

        ScorePkVO vo = new ScorePkVO();
        UserProfileVO me = socialUserService.requireProfile(myId);
        UserProfileVO friend = socialUserService.requireProfile(friendId);
        vo.setMe(me);
        vo.setFriend(friend);

        if (!UserRole.STUDENT.equals(me.getRole()) || !UserRole.STUDENT.equals(friend.getRole())) {
            vo.setResult("INCOMPARABLE");
            vo.setTip("教师与学生之间不可进行成绩 PK");
            return vo;
        }

        if (courseId == null) {
            vo.setScoreType("COMPREHENSIVE");
            BigDecimal myScore = me.getComprehensiveScore() == null ? BigDecimal.ZERO : me.getComprehensiveScore();
            BigDecimal friendScore = friend.getComprehensiveScore() == null ? BigDecimal.ZERO : friend.getComprehensiveScore();
            // 教师无人打分时综合分为 null
            if (UserRole.TEACHER.equals(friend.getRole()) && Boolean.FALSE.equals(friend.getHasRating())) {
                friendScore = BigDecimal.ZERO;
            }
            if (UserRole.TEACHER.equals(me.getRole()) && Boolean.FALSE.equals(me.getHasRating())) {
                myScore = BigDecimal.ZERO;
            }
            vo.setMyScore(myScore);
            vo.setFriendScore(friendScore);
            vo.setResult(compareResult(myScore, friendScore));
            vo.setTip("对比综合评分");
            return vo;
        }

        vo.setScoreType("COURSE");
        vo.setCourseId(courseId);
        Course course = courseMapper.findById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        vo.setCourseName(course.getCourseName());

        if (!UserRole.STUDENT.equals(me.getRole()) || !UserRole.STUDENT.equals(friend.getRole())) {
            vo.setResult("INCOMPARABLE");
            vo.setTip("课程成绩 PK 仅支持双方均为学生");
            return vo;
        }

        CoursePersonnel mine = coursePersonnelMapper.find(courseId, myId);
        CoursePersonnel theirs = coursePersonnelMapper.find(courseId, friendId);
        if (mine == null || theirs == null) {
            vo.setResult("INCOMPARABLE");
            vo.setTip("双方须都在该课程班级中才能 PK");
            return vo;
        }
        BigDecimal myScore = mine.getStudentScore() == null ? BigDecimal.ZERO : mine.getStudentScore();
        BigDecimal friendScore = theirs.getStudentScore() == null ? BigDecimal.ZERO : theirs.getStudentScore();
        vo.setMyScore(myScore);
        vo.setFriendScore(friendScore);
        vo.setResult(compareResult(myScore, friendScore));
        vo.setTip("对比课程「" + course.getCourseName() + "」成绩");
        return vo;
    }

    /**
     * 班级成绩排名: 返回排名列表, 他人不显示具体分数, 仅本人可见自己的分数。
     */
    public ClassRankVO classRanking(Long studentId, Long courseId) {
        if (courseId == null) {
            throw new BusinessException("课程ID不能为空");
        }
        if (studentMapper.findById(studentId) == null) {
            throw new BusinessException("学生不存在");
        }
        Course course = courseMapper.findById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        CoursePersonnel self = coursePersonnelMapper.find(courseId, studentId);
        if (self == null) {
            throw new BusinessException("您未选修该课程");
        }

        List<CoursePersonnel> members = new ArrayList<>(coursePersonnelMapper.findByCourseId(courseId));
        members.sort(Comparator
                .comparing((CoursePersonnel m) -> m.getStudentScore() == null ? BigDecimal.ZERO : m.getStudentScore())
                .reversed()
                .thenComparing(CoursePersonnel::getStudentId));

        ClassRankVO vo = new ClassRankVO();
        vo.setCourseId(courseId);
        vo.setCourseName(course.getCourseName());
        vo.setTotalStudents(members.size());
        vo.setMyScore(self.getStudentScore() == null ? BigDecimal.ZERO : self.getStudentScore());

        List<ClassRankItemVO> ranking = new ArrayList<>();
        int rank = 0;
        BigDecimal lastScore = null;
        int index = 0;
        for (CoursePersonnel member : members) {
            index++;
            BigDecimal score = member.getStudentScore() == null ? BigDecimal.ZERO : member.getStudentScore();
            if (lastScore == null || score.compareTo(lastScore) != 0) {
                rank = index;
                lastScore = score;
            }
            boolean isMe = studentId.equals(member.getStudentId());
            if (isMe) {
                vo.setMyRank(rank);
            }

            ClassRankItemVO item = new ClassRankItemVO();
            item.setRank(rank);
            item.setStudentId(member.getStudentId());
            item.setIsMe(isMe);
            Student student = studentMapper.findById(member.getStudentId());
            if (student != null) {
                item.setNickname(student.getNickname());
                item.setAvatar(student.getAvatar());
            }
            // 仅本人显示具体分数
            item.setScore(isMe ? score : null);
            ranking.add(item);
        }
        vo.setRanking(ranking);
        return vo;
    }

    private String compareResult(BigDecimal mine, BigDecimal theirs) {
        int cmp = mine.compareTo(theirs);
        if (cmp > 0) {
            return "WIN";
        }
        if (cmp < 0) {
            return "LOSE";
        }
        return "DRAW";
    }
}
