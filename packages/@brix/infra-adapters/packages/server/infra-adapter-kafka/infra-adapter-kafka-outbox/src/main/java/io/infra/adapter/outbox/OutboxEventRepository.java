/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.infra.adapter.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbox 事件仓储接口
 *
 * <p>Spring Data JPA 仓储，提供 Outbox 事件的 CRUD 和批量操作能力。</p>
 *
 * <h3>架构定位</h3>
 * <p>
 * 本接口属于 {@code infra-adapter-outbox} 独立模块（Layer 2.5: Adapter 层）。
 * 通过 Spring Data JPA 自动代理实现，无需手动编写 SQL。
 * </p>
 *
 * <h3>核心查询</h3>
 * <ul>
 *   <li>{@link #findPendingEvents(int)} - 查询待发送的事件（供定时任务使用）</li>
 *   <li>{@link #findRetryableEvents(int, int)} - 查询可重试的失败事件</li>
 *   <li>{@link #markAsProcessing(List)} - 批量标记为处理中（防止并发重复处理）</li>
 *   <li>{@link #deleteCompletedBefore(Instant)} - 清理已完成的历史事件</li>
 * </ul>
 *
 * @author Brix Platform Authors
 * @since 3.0.0
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * 根据事件 ID 查找 Outbox 记录
     *
     * @param eventId 事件唯一标识
     * @return Outbox 事件的 Optional 包装
     */
    Optional<OutboxEvent> findByEventId(String eventId);

    /**
     * 检查指定事件 ID 是否已存在（幂等性检查）
     *
     * @param eventId 事件唯一标识
     * @return 已存在返回 true
     */
    boolean existsByEventId(String eventId);

    /**
     * 查询待处理的事件（PENDING 状态），按创建时间升序排列
     *
     * <p>供 {@link OutboxEventPublisher#processOutbox()} 定时任务使用。</p>
     *
     * @param limit 最大查询数量（批次大小）
     * @return 待处理事件列表
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' ORDER BY e.createdAt ASC LIMIT :limit")
    List<OutboxEvent> findPendingEvents(@Param("limit") int limit);

    /**
     * 查询可重试的失败事件（FAILED 状态且未超过最大重试次数）
     *
     * <p>供 {@link OutboxEventPublisher#retryFailedEvents()} 定时任务使用。</p>
     *
     * @param maxRetryCount 最大重试次数上限
     * @param limit         最大查询数量（批次大小）
     * @return 可重试事件列表
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'FAILED' AND e.retryCount < :maxRetryCount ORDER BY e.createdAt ASC LIMIT :limit")
    List<OutboxEvent> findRetryableEvents(@Param("maxRetryCount") int maxRetryCount, @Param("limit") int limit);

    /**
     * 批量标记事件为处理中
     *
     * <p>使用乐观锁语义：仅更新当前状态为 PENDING 的记录，
     * 防止多个定时任务实例并发处理同一批事件。</p>
     *
     * @param ids 事件 ID 列表
     * @return 实际更新的记录数
     */
    @Modifying
    @Query("UPDATE OutboxEvent e SET e.status = 'PROCESSING' WHERE e.id IN :ids AND e.status = 'PENDING'")
    int markAsProcessing(@Param("ids") List<UUID> ids);

    /**
     * 删除指定时间之前的已完成事件
     *
     * <p>供 {@link OutboxEventPublisher#cleanupOldEvents()} 定时任务使用，
     * 防止 Outbox 表无限膨胀。</p>
     *
     * @param before 截止时间（删除此时间之前的已完成事件）
     * @return 实际删除的记录数
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = 'COMPLETED' AND e.processedAt < :before")
    int deleteCompletedBefore(@Param("before") Instant before);

    /**
     * 按状态统计事件数量（用于监控和告警）
     *
     * @param status 事件状态
     * @return 该状态的事件数量
     */
    long countByStatus(OutboxEvent.Status status);
}
