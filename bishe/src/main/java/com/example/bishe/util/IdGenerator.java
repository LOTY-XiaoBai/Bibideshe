package com.example.bishe.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * ID 生成工具。
 *
 * <ul>
 *   <li>学生ID: 1 开头 + 9 位随机数字 (共 10 位)</li>
 *   <li>教师ID: 2 开头 + 9 位随机数字 (共 10 位)</li>
 *   <li>管理员ID: "admin" + 5 位随机数字</li>
 * </ul>
 *
 * 生成结果可能重复, 因此调用方(Service)需要配合数据库做唯一性校验后重试。
 */
public final class IdGenerator {

    private static final long STUDENT_PREFIX = 1L;
    private static final long TEACHER_PREFIX = 2L;
    private static final long GROUP_PREFIX = 3L;
    private static final long ORDER_PREFIX = 4L;
    /** 前缀后随机部分的位数(9位) */
    private static final long RANDOM_BOUND = 1_000_000_000L;

    private IdGenerator() {
    }

    /**
     * 生成学生ID: 1 开头, 共 10 位数字。
     */
    public static Long generateStudentId() {
        return buildNumericId(STUDENT_PREFIX);
    }

    /**
     * 生成教师ID: 2 开头, 共 10 位数字。
     */
    public static Long generateTeacherId() {
        return buildNumericId(TEACHER_PREFIX);
    }

    /**
     * 生成管理员ID: "admin" + 5 位随机数字, 例如 admin01234。
     */
    public static String generateAdminId() {
        int random = ThreadLocalRandom.current().nextInt(0, 100_000);
        return "admin" + String.format("%05d", random);
    }

    /**
     * 生成群号ID: 3 开头, 共 10 位数字。
     */
    public static Long generateGroupId() {
        return buildNumericId(GROUP_PREFIX);
    }

    /**
     * 生成订单ID: 4 开头, 共 10 位数字。
     */
    public static Long generateOrderId() {
        return buildNumericId(ORDER_PREFIX);
    }

    /**
     * 构造以指定数字开头的定长ID: prefix * 10^9 + [0, 10^9)。
     */
    private static long buildNumericId(long prefixDigit) {
        long random = ThreadLocalRandom.current().nextLong(0, RANDOM_BOUND);
        return prefixDigit * RANDOM_BOUND + random;
    }
}
