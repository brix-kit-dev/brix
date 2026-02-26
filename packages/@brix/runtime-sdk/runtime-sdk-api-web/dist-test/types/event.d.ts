/**
 * @file 事件相关类型定义
 * @description 定义事件系统的核心类型，包括事件消息、处理器、订阅选项等
 * @module @brix/runtime-sdk-api-web/types/event
 * @version 3.2.0
 *
 * 【v3.2 变更】
 * 从 index.ts 拆分出独立的类型文件。
 *
 * 【设计原则】
 * - 支持简单事件总线和可治理事件总线两种模式
 * - 可治理事件总线提供完整的事件元数据和审计信息
 */
/**
 * 事件消息
 *
 * <p>封装事件的完整信息，包括类型、载荷、时间戳和来源。</p>
 */
export interface EventMessage<T = unknown> {
    /** 事件类型 */
    readonly eventType: string;
    /** 事件载荷 */
    readonly payload: T;
    /** 时间戳 */
    readonly timestamp: number;
    /** 事件来源（插件 ID） */
    readonly source?: string;
}
/**
 * 事件订阅选项
 */
export interface SubscriptionOptions {
    /** 是否只触发一次 */
    readonly once?: boolean;
    /** 过滤函数 */
    readonly filter?: (payload: unknown) => boolean;
}
/**
 * 事件处理器
 *
 * @typeParam T - 事件载荷类型
 */
export type EventHandler<T = unknown> = (payload: T) => void;
/**
 * 取消订阅函数
 */
export type Unsubscribe = () => void;
/**
 * 事件总线能力类型标识
 */
export declare const EventBusCapabilityType: unique symbol;
/**
 * 事件总线能力契约
 *
 * <p>为插件提供跨插件通信能力。</p>
 *
 * <h3>使用示例</h3>
 * ```typescript
 * const eventBus = context.getCapability<EventBusCapability>(EventBusCapabilityType);
 *
 * // 发送事件
 * eventBus.emit('booking:selected', { bookingId: '123' });
 *
 * // 订阅事件
 * const unsubscribe = eventBus.on('booking:selected', (payload) => {
 *   console.log('Booking selected:', payload);
 * });
 * ```
 */
export interface EventBusCapability {
    /**
     * 发送事件
     *
     * @param eventType 事件类型
     * @param payload 事件载荷
     */
    emit(eventType: string, payload: unknown): void;
    /**
     * 订阅事件
     *
     * @param eventType 事件类型
     * @param handler 事件处理器
     * @returns 取消订阅函数
     */
    on(eventType: string, handler: EventHandler): () => void;
    /**
     * 取消订阅
     *
     * @param eventType 事件类型
     * @param handler 事件处理器
     */
    off(eventType: string, handler: EventHandler): void;
}
/**
 * 可治理事件总线能力类型标识
 */
export declare const GovernedEventBusCapabilityType: unique symbol;
/**
 * 可治理事件
 *
 * <p>包含完整元数据的事件，用于可观测性和审计。</p>
 */
export interface GovernedEvent<T = unknown> {
    /** 事件类型 */
    readonly type: string;
    /** 事件载荷 */
    readonly payload: T;
    /** 事件元数据 */
    readonly metadata: GovernedEventMetadata;
}
/**
 * 可治理事件元数据
 */
export interface GovernedEventMetadata {
    /** 事件 ID（唯一标识） */
    readonly eventId: string;
    /** 发送时间戳 */
    readonly timestamp: number;
    /** 发送者（插件 ID） */
    readonly source: string;
    /** 租户 ID */
    readonly tenantId?: string;
    /**
     * 事件作用域
     * - 'plugin': 仅插件内可见
     * - 'host': 全局可见
     */
    readonly scope: 'plugin' | 'host';
}
/**
 * 事件元数据
 *
 * <p>用于 Web 端事件追踪和可观测性的扩展元数据。
 * 相比 GovernedEventMetadata，提供更灵活的字段命名以适应 Web 场景。</p>
 *
 * <h3>与 GovernedEventMetadata 的关系</h3>
 * <ul>
 *   <li>GovernedEventMetadata：用于可治理事件总线</li>
 *   <li>EventMetadata：用于通用事件发送场景</li>
 * </ul>
 *
 * @since 3.2.0
 */
export interface EventMetadata {
    /** 发送者插件 ID */
    readonly sourcePlugin: string;
    /**
     * 事件作用域
     * - 'plugin': 仅插件内可见
     * - 'host': 全局可见（跨插件）
     */
    readonly scope: 'plugin' | 'host';
    /** 追踪 ID（用于分布式追踪） */
    readonly traceId: string;
    /** 发送时间戳（毫秒） */
    readonly timestamp: number;
    /** 租户 ID（多租户场景） */
    readonly tenantId?: string;
    /** 自定义标签（用于过滤和分类） */
    readonly tags?: ReadonlyArray<string>;
}
/**
 * Web 端事件发送选项
 *
 * <p>控制事件发送行为的配置参数。</p>
 *
 * @since 3.2.0
 */
export interface WebEventEmitOptions {
    /**
     * 事件作用域
     * - 'plugin': 仅当前插件内可见
     * - 'host': 全局可见，可跨插件订阅
     * @default 'host'
     */
    readonly scope?: 'plugin' | 'host';
    /**
     * 是否同步发送
     *
     * <p>默认异步发送（通过 queueMicrotask），
     * 设为 true 时立即同步调用所有订阅者。</p>
     *
     * @default false
     */
    readonly sync?: boolean;
    /**
     * 自定义标签
     *
     * <p>用于事件分类和过滤，在日志和监控中可见。</p>
     */
    readonly tags?: ReadonlyArray<string>;
}
/**
 * Web 端事件订阅选项
 *
 * <p>控制事件订阅行为的配置参数。</p>
 *
 * @since 3.2.0
 */
export interface WebEventSubscribeOptions {
    /**
     * 防抖时间（毫秒）
     *
     * <p>短时间内连续触发的事件只处理最后一次。</p>
     */
    readonly debounce?: number;
    /**
     * 节流时间（毫秒）
     *
     * <p>限制事件处理的最小时间间隔。</p>
     */
    readonly throttle?: number;
    /**
     * 事件过滤器
     *
     * <p>返回 true 时才调用处理器。</p>
     */
    readonly filter?: (payload: unknown) => boolean;
    /**
     * 是否只触发一次
     *
     * @default false
     */
    readonly once?: boolean;
}
/**
 * 可治理事件处理函数
 */
export type GovernedEventHandler<T = unknown> = (event: GovernedEvent<T>) => void;
/**
 * 可治理事件总线能力契约
 *
 * <p>与简单的 EventBusCapability 不同，GovernedEventBusCapability 提供：</p>
 * <ul>
 *   <li>自动注入事件元数据（eventId, timestamp, source）</li>
 *   <li>事件作用域控制（plugin/host）</li>
 *   <li>完整的事件审计信息</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * ```typescript
 * const eventBus = context.getCapability<GovernedEventBusCapability>(GovernedEventBusCapabilityType);
 *
 * // 发送全局事件
 * eventBus.emit('booking:created', { bookingId: '123' }, 'host');
 *
 * // 订阅事件（接收完整元数据）
 * eventBus.on('booking:created', (event) => {
 *   console.log(`Event ${event.metadata.eventId} from ${event.metadata.source}`);
 *   console.log('Payload:', event.payload);
 * });
 * ```
 */
export interface GovernedEventBusCapability {
    /**
     * 发送事件
     *
     * @param eventType 事件类型
     * @param payload 事件载荷
     * @param scope 事件作用域（默认 'host'）
     */
    emit<T = unknown>(eventType: string, payload: T, scope?: 'plugin' | 'host'): void;
    /**
     * 订阅事件
     *
     * @param eventType 事件类型
     * @param handler 事件处理函数，接收完整的 GovernedEvent
     * @returns 取消订阅函数
     */
    on<T = unknown>(eventType: string, handler: GovernedEventHandler<T>): Unsubscribe;
    /**
     * 订阅一次事件
     *
     * @param eventType 事件类型
     * @param handler 事件处理函数
     * @returns 取消订阅函数
     */
    once<T = unknown>(eventType: string, handler: GovernedEventHandler<T>): Unsubscribe;
    /**
     * 取消订阅
     *
     * @param eventType 事件类型
     * @param handler 事件处理函数
     */
    off<T = unknown>(eventType: string, handler: GovernedEventHandler<T>): void;
}
//# sourceMappingURL=event.d.ts.map