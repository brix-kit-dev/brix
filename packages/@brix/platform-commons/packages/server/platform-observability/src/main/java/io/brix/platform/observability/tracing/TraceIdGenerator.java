package io.brix.platform.observability.tracing;

import java.util.UUID;

/**
 * TraceId 生成
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
public class TraceIdGenerator {

    /**
     * 生成新的 TraceId
     * <p>
     * 使用 UUID v4 格式，移除连字符以减少传输开销
     * </p>
     *
     * @return 32位十六进制字符串
     */
    public String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
