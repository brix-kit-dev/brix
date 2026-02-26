package io.brix.platform.common.util;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * 平台统一编号生成器，提供 9 位数字 ID 及带前缀的插件/用户标识，确保编码规则一致。
 */
public final class IdGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DEFAULT_LENGTH = 9;

    private IdGenerator() {
    }

    /**
     * 生成 9 位纯数字 ID，适用于用户、插件等主键。
     */
    public static String numericId() {
        return numericId(DEFAULT_LENGTH);
    }

    /**
     * 按指定长度生成纯数字 ID。
     *
     * @param length 目标长度
     * @return 指定长度的数字串
     */
    public static String numericId(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("ID 长度必须大于 0");
        }
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }

    /**
     * 生成带 USR 前缀的用户 ID（示例：USR-123456789）。
     */
    public static String userId() {
        return format("USR", numericId());
    }

    /**
     * 生成带 PLG 前缀的插件 ID（示例：PLG-123456789）。
     */
    public static String pluginId() {
        return format("PLG", numericId());
    }

    private static String format(String prefix, String id) {
        return prefix.toUpperCase(Locale.ROOT) + '-' + id;
    }
}
