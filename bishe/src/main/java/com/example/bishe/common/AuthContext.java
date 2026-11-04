package com.example.bishe.common;

/**
 * 基于 ThreadLocal 的登录用户上下文。
 */
public final class AuthContext {

    private static final ThreadLocal<AuthUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthUser user) {
        HOLDER.set(user);
    }

    public static AuthUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static AuthUser requireLogin() {
        AuthUser user = HOLDER.get();
        if (user == null) {
            throw new BusinessException(401, "未登录或登录已失效, 请重新登录");
        }
        return user;
    }

    public static Long requireTeacherId() {
        AuthUser user = requireLogin();
        if (!UserRole.TEACHER.equals(user.getRole()) || user.getUserId() == null) {
            throw new BusinessException(403, "需要教师身份才能执行此操作");
        }
        return user.getUserId();
    }

    public static Long requireStudentId() {
        AuthUser user = requireLogin();
        if (!UserRole.STUDENT.equals(user.getRole()) || user.getUserId() == null) {
            throw new BusinessException(403, "需要学生身份才能执行此操作");
        }
        return user.getUserId();
    }

    public static void requireAdmin() {
        AuthUser user = requireLogin();
        if (!UserRole.ADMIN.equals(user.getRole())) {
            throw new BusinessException(403, "需要管理员身份才能执行此操作");
        }
    }

    /** 要求管理员身份, 并返回管理员账号ID字符串。 */
    public static String requireAdminId() {
        AuthUser user = requireLogin();
        if (!UserRole.ADMIN.equals(user.getRole())) {
            throw new BusinessException(403, "需要管理员身份才能执行此操作");
        }
        return user.getSubject();
    }

    /**
     * 学生或教师均可(交际模块共用)。
     */
    public static Long requireUserId() {
        AuthUser user = requireLogin();
        if ((!UserRole.STUDENT.equals(user.getRole()) && !UserRole.TEACHER.equals(user.getRole())) || user.getUserId() == null) {
            throw new BusinessException(403, "当前账号不支持此操作");
        }
        return user.getUserId();
    }

    /**
     * 要求当前用户是指定学生本人, 或管理员。
     */
    public static Long requireSelfStudentOrAdmin(Long studentId) {
        AuthUser user = requireLogin();
        if (UserRole.ADMIN.equals(user.getRole())) {
            return studentId;
        }
        if (!UserRole.STUDENT.equals(user.getRole()) || user.getUserId() == null) {
            throw new BusinessException(403, "无权访问该学生数据");
        }
        if (studentId != null && !user.getUserId().equals(studentId)) {
            throw new BusinessException(403, "只能操作自己的数据");
        }
        return user.getUserId();
    }

    /**
     * 要求当前用户是指定教师本人, 或管理员。
     */
    public static Long requireSelfTeacherOrAdmin(Long teacherId) {
        AuthUser user = requireLogin();
        if (UserRole.ADMIN.equals(user.getRole())) {
            return teacherId;
        }
        if (!UserRole.TEACHER.equals(user.getRole()) || user.getUserId() == null) {
            throw new BusinessException(403, "无权访问该教师数据");
        }
        if (teacherId != null && !user.getUserId().equals(teacherId)) {
            throw new BusinessException(403, "只能操作自己的数据");
        }
        return user.getUserId();
    }
}
