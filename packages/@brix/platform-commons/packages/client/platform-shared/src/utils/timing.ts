/**
 * @file timing.ts
 * @description 时间控制工具函数集
 * @module @brix/utils/timing
 * @version 3.0.0
 * 
 * 【模块说明】
 * 提供各种时间控制相关的工具函数，包括防抖、节流、延迟、超时等。
 * 这些函数是前端开发中最常用的性能优化工具。
 * 
 * 【使用场景】
 * - 防抖：搜索输入、表单校验、窗口调整
 * - 节流：滚动事件、按钮点击、拖拽操作
 * - 延迟：动画间隔、轮询等待、用户反馈
 * - 超时：网络请求、长时任务控制
 * 
 * @license Apache-2.0
 */

// ============================================================
// 防抖（Debounce）
// ============================================================

/**
 * 防抖函数
 * 
 * 【功能说明】
 * 延迟执行目标函数，在延迟期间如果再次调用则重新计时。
 * 适用于搜索输入、窗口调整等高频触发场景。
 * 
 * 【工作原理】
 * 1. 每次调用时清除之前的定时器
 * 2. 设置新的定时器
 * 3. 仅当定时器到期后才真正执行函数
 * 
 * @template T 函数类型
 * @param func 需要防抖的函数
 * @param wait 延迟时间（毫秒）
 * @param immediate 是否立即执行首次调用（默认 false）
 * @returns 防抖后的函数
 * 
 * @example
 * ```typescript
 * // 基础用法：搜索防抖
 * const debouncedSearch = debounce((text: string) => {
 *   searchApi(text);
 * }, 300);
 * 
 * input.addEventListener('input', (e) => {
 *   debouncedSearch(e.target.value);
 * });
 * 
 * // 立即执行模式：按钮防连点
 * const debouncedClick = debounce(() => {
 *   submitForm();
 * }, 1000, true);
 * ```
 */
export function debounce<T extends (...args: Parameters<T>) => ReturnType<T>>(
  func: T,
  wait: number,
  immediate: boolean = false
): (...args: Parameters<T>) => void {
  let timeoutId: ReturnType<typeof setTimeout> | null = null;

  return function (this: unknown, ...args: Parameters<T>): void {
    const callNow = immediate && !timeoutId;

    if (timeoutId) {
      clearTimeout(timeoutId);
    }

    timeoutId = setTimeout(() => {
      timeoutId = null;
      if (!immediate) {
        func.apply(this, args);
      }
    }, wait);

    if (callNow) {
      func.apply(this, args);
    }
  };
}

/**
 * 带取消和刷新功能的防抖函数接口
 * 
 * @template T 原函数类型
 */
export interface DebouncedFunction<T extends (...args: Parameters<T>) => ReturnType<T>> {
  /** 调用防抖函数 */
  (...args: Parameters<T>): void;
  /** 取消待执行的调用 */
  cancel: () => void;
  /** 立即执行待执行的调用 */
  flush: () => void;
}

/**
 * 创建可取消的防抖函数
 * 
 * 【功能说明】
 * 扩展版防抖函数，支持取消和立即刷新。
 * 适用于需要手动控制防抖行为的场景。
 * 
 * @template T 函数类型
 * @param func 需要防抖的函数
 * @param wait 延迟时间（毫秒）
 * @returns 带取消功能的防抖函数
 * 
 * @example
 * ```typescript
 * const debouncedSave = debounceWithCancel((data) => {
 *   saveToServer(data);
 * }, 1000);
 * 
 * // 正常调用
 * debouncedSave(formData);
 * 
 * // 组件卸载时取消
 * onUnmount(() => {
 *   debouncedSave.cancel();
 * });
 * 
 * // 离开页面前立即保存
 * onBeforeUnload(() => {
 *   debouncedSave.flush();
 * });
 * ```
 */
export function debounceWithCancel<T extends (...args: Parameters<T>) => ReturnType<T>>(
  func: T,
  wait: number
): DebouncedFunction<T> {
  let timeoutId: ReturnType<typeof setTimeout> | null = null;
  let lastArgs: Parameters<T> | null = null;
  let lastThis: unknown = null;

  const debounced = function (this: unknown, ...args: Parameters<T>): void {
    lastArgs = args;
    lastThis = this;

    if (timeoutId) {
      clearTimeout(timeoutId);
    }

    timeoutId = setTimeout(() => {
      timeoutId = null;
      if (lastArgs) {
        func.apply(lastThis, lastArgs);
        lastArgs = null;
        lastThis = null;
      }
    }, wait);
  } as DebouncedFunction<T>;

  debounced.cancel = () => {
    if (timeoutId) {
      clearTimeout(timeoutId);
      timeoutId = null;
    }
    lastArgs = null;
    lastThis = null;
  };

  debounced.flush = () => {
    if (timeoutId) {
      clearTimeout(timeoutId);
      timeoutId = null;
    }
    if (lastArgs) {
      func.apply(lastThis, lastArgs);
      lastArgs = null;
      lastThis = null;
    }
  };

  return debounced;
}

// ============================================================
// 节流（Throttle）
// ============================================================

/**
 * 节流函数
 * 
 * 【功能说明】
 * 限制函数在指定时间内只能执行一次。
 * 适用于滚动事件、按钮点击等需要限制频率的场景。
 * 
 * 【工作原理】
 * 1. 记录上次执行时间
 * 2. 如果距离上次执行超过限制时间，则执行函数
 * 3. 否则忽略本次调用
 * 
 * @template T 函数类型
 * @param func 需要节流的函数
 * @param limit 时间限制（毫秒）
 * @returns 节流后的函数
 * 
 * @example
 * ```typescript
 * // 滚动事件节流
 * const throttledScroll = throttle(() => {
 *   updateScrollIndicator();
 * }, 100);
 * 
 * window.addEventListener('scroll', throttledScroll);
 * 
 * // 按钮点击节流
 * const throttledSubmit = throttle(() => {
 *   submitForm();
 * }, 2000);
 * ```
 */
export function throttle<T extends (...args: Parameters<T>) => ReturnType<T>>(
  func: T,
  limit: number
): (...args: Parameters<T>) => void {
  let inThrottle = false;

  return function (this: unknown, ...args: Parameters<T>): void {
    if (!inThrottle) {
      func.apply(this, args);
      inThrottle = true;
      setTimeout(() => {
        inThrottle = false;
      }, limit);
    }
  };
}

/**
 * 带尾调用的节流函数
 * 
 * 【功能说明】
 * 在节流期间的最后一次调用会在节流结束后执行。
 * 确保最终状态能够被正确更新。
 * 
 * @template T 函数类型
 * @param func 需要节流的函数
 * @param limit 时间限制（毫秒）
 * @returns 节流后的函数
 * 
 * @example
 * ```typescript
 * // 拖拽事件：既要及时响应，又要确保最终位置正确
 * const throttledDrag = throttleWithTrailing((position) => {
 *   updateElementPosition(position);
 * }, 16); // 约 60fps
 * ```
 */
export function throttleWithTrailing<T extends (...args: Parameters<T>) => ReturnType<T>>(
  func: T,
  limit: number
): (...args: Parameters<T>) => void {
  let inThrottle = false;
  let lastArgs: Parameters<T> | null = null;
  let lastThis: unknown = null;

  return function (this: unknown, ...args: Parameters<T>): void {
    if (!inThrottle) {
      func.apply(this, args);
      inThrottle = true;
      setTimeout(() => {
        inThrottle = false;
        if (lastArgs) {
          func.apply(lastThis, lastArgs);
          lastArgs = null;
          lastThis = null;
        }
      }, limit);
    } else {
      lastArgs = args;
      lastThis = this;
    }
  };
}

// ============================================================
// 延迟（Delay）
// ============================================================

/**
 * 延迟执行
 * 
 * 【功能说明】
 * 返回一个在指定时间后解决的 Promise。
 * 用于 async/await 语法中实现等待效果。
 * 
 * @param ms 延迟时间（毫秒）
 * @returns Promise<void>
 * 
 * @example
 * ```typescript
 * // 基础用法
 * await delay(1000);
 * console.log('1秒后执行');
 * 
 * // 动画间隔
 * for (const item of items) {
 *   await fadeIn(item);
 *   await delay(200);
 * }
 * ```
 */
export function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * 带返回值的延迟
 * 
 * 【功能说明】
 * 延迟指定时间后返回指定的值。
 * 适用于需要延迟返回特定值的场景。
 * 
 * @template T 返回值类型
 * @param ms 延迟时间（毫秒）
 * @param value 返回值
 * @returns Promise<T>
 * 
 * @example
 * ```typescript
 * // 延迟返回默认值
 * const result = await delayWith(1000, { status: 'ready' });
 * 
 * // Promise.race 超时降级
 * const data = await Promise.race([
 *   fetchData(),
 *   delayWith(5000, defaultData),
 * ]);
 * ```
 */
export function delayWith<T>(ms: number, value: T): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms));
}

/**
 * 可取消延迟接口
 */
export interface CancelableDelay {
  /** 延迟 Promise */
  promise: Promise<void>;
  /** 取消延迟 */
  cancel: () => void;
}

/**
 * 创建可取消的延迟
 * 
 * 【功能说明】
 * 创建一个可以被手动取消的延迟。
 * 适用于需要中断等待的场景，如组件卸载时。
 * 
 * @param ms 延迟时间（毫秒）
 * @returns 可取消的延迟对象
 * 
 * @example
 * ```typescript
 * const { promise, cancel } = cancelableDelay(5000);
 * 
 * // 设置取消条件
 * button.onclick = cancel;
 * 
 * try {
 *   await promise;
 *   showMessage('操作完成');
 * } catch (e) {
 *   showMessage('操作已取消');
 * }
 * ```
 */
export function cancelableDelay(ms: number): CancelableDelay {
  let timeoutId: ReturnType<typeof setTimeout>;
  let rejectFn: (reason?: unknown) => void;

  const promise = new Promise<void>((resolve, reject) => {
    rejectFn = reject;
    timeoutId = setTimeout(resolve, ms);
  });

  return {
    promise,
    cancel: () => {
      clearTimeout(timeoutId);
      rejectFn(new Error('Delay cancelled'));
    },
  };
}

// ============================================================
// 超时（Timeout）
// ============================================================

/**
 * 带超时的 Promise 包装
 * 
 * 【功能说明】
 * 为任意 Promise 添加超时限制，超时后抛出错误。
 * 适用于网络请求、长时间操作等需要超时控制的场景。
 * 
 * @template T Promise 解析类型
 * @param promise 原始 Promise
 * @param ms 超时时间（毫秒）
 * @param message 超时错误消息（可选）
 * @returns Promise<T>
 * @throws Error 超时时抛出错误
 * 
 * @example
 * ```typescript
 * // API 请求超时
 * try {
 *   const data = await withTimeout(
 *     fetch('/api/data'),
 *     5000,
 *     '请求超时，请检查网络'
 *   );
 * } catch (e) {
 *   if (e.message.includes('超时')) {
 *     showRetryDialog();
 *   }
 * }
 * 
 * // 文件上传超时
 * await withTimeout(uploadFile(file), 60000, '上传超时');
 * ```
 */
export function withTimeout<T>(
  promise: Promise<T>,
  ms: number,
  message: string = 'Operation timed out'
): Promise<T> {
  let timeoutId: ReturnType<typeof setTimeout>;

  const timeoutPromise = new Promise<never>((_, reject) => {
    timeoutId = setTimeout(() => {
      reject(new Error(message));
    }, ms);
  });

  return Promise.race([promise, timeoutPromise]).finally(() => {
    clearTimeout(timeoutId);
  });
}

// ============================================================
// 调度（Scheduling）
// ============================================================

/**
 * 在下一个宏任务中执行
 * 
 * 【功能说明】
 * 将函数调度到下一个事件循环执行。
 * 类似于 Vue 的 nextTick，但更简单。
 * 
 * @param fn 要执行的函数
 * 
 * @example
 * ```typescript
 * // 确保 DOM 更新后执行
 * element.innerHTML = newContent;
 * nextTick(() => {
 *   element.querySelector('.new-element').focus();
 * });
 * ```
 */
export function nextTick(fn: () => void): void {
  setTimeout(fn, 0);
}

/**
 * 请求空闲回调（带降级）
 * 
 * 【功能说明】
 * 在浏览器空闲时执行任务，支持不兼容浏览器的降级处理。
 * 适用于低优先级的后台任务。
 * 
 * @param fn 要执行的函数
 * @param options 配置选项
 * @param options.timeout 超时时间（毫秒）
 * @returns 请求 ID
 * 
 * @example
 * ```typescript
 * // 空闲时发送分析数据
 * requestIdleCallback(() => {
 *   sendAnalytics(analyticsData);
 * }, { timeout: 5000 });
 * 
 * // 空闲时预加载资源
 * requestIdleCallback(() => {
 *   preloadNextPageResources();
 * });
 * ```
 */
export function requestIdleCallback(
  fn: () => void,
  options?: { timeout?: number }
): number {
  if (typeof window !== 'undefined' && 'requestIdleCallback' in window) {
    return (window as Window & { requestIdleCallback: (cb: () => void, opts?: { timeout?: number }) => number }).requestIdleCallback(fn, options);
  }
  // 降级到 setTimeout
  return setTimeout(fn, options?.timeout ?? 1) as unknown as number;
}
