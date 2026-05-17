/**
 * @file timing.ts
 * @description Time control utility functions
 * @module @brix-sdk/utils/timing
 * @version 3.0.0
 * 
 * ## Module Overview
 * Provides various time control utility functions including debounce, throttle, delay, timeout, etc.
 * These functions are the most commonly used performance optimization tools in frontend development.
 * 
 * ## Use Cases
 * - Debounce: search input, form validation, window resize
 * - Throttle: scroll events, button clicks, drag operations
 * - Delay: animation intervals, polling waits, user feedback
 * - Timeout: network requests, long-running task control
 * 
 * @license Apache-2.0
 */

// ============================================================
// Debounce
// ============================================================

/**
 * Debounce function
 * 
 * Delays execution of the target function; if called again during the delay period,
 * the timer resets. Suitable for high-frequency trigger scenarios such as search input
 * and window resizing.
 * 
 * How it works:
 * 1. Each call clears the previous timer
 * 2. Sets a new timer
 * 3. The function is only executed when the timer expires
 * 
 * @template T - Function type
 * @param func - Function to debounce
 * @param wait - Delay time in milliseconds
 * @param immediate - Whether to execute immediately on first call (default: false)
 * @returns Debounced function
 * 
 * @example
 * ```typescript
 * // Basic usage: search debounce
 * const debouncedSearch = debounce((text: string) => {
 *   searchApi(text);
 * }, 300);
 * 
 * input.addEventListener('input', (e) => {
 *   debouncedSearch(e.target.value);
 * });
 * 
 * // Immediate mode: prevent repeated button clicks
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
 * Cancelable debounced function interface
 * 
 * @template T - Original function type
 */
export interface DebouncedFunction<T extends (...args: Parameters<T>) => ReturnType<T>> {
  /** Call the debounced function */
  (...args: Parameters<T>): void;
  /** Cancel pending invocation */
  cancel: () => void;
  /** Immediately execute pending invocation */
  flush: () => void;
}

/**
 * Create a cancelable debounced function
 * 
 * Extended debounce function that supports cancel and immediate flush.
 * Suitable for scenarios requiring manual control over debounce behavior.
 * 
 * @template T - Function type
 * @param func - Function to debounce
 * @param wait - Delay time in milliseconds
 * @returns Cancelable debounced function
 * 
 * @example
 * ```typescript
 * const debouncedSave = debounceWithCancel((data) => {
 *   saveToServer(data);
 * }, 1000);
 * 
 * // Normal call
 * debouncedSave(formData);
 * 
 * // Cancel on component unmount
 * onUnmount(() => {
 *   debouncedSave.cancel();
 * });
 * 
 * // Flush before leaving page
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
// Throttle
// ============================================================

/**
 * Throttle function
 * 
 * Limits a function to execute at most once within a specified time interval.
 * Suitable for scenarios requiring frequency limiting such as scroll events
 * and button clicks.
 * 
 * How it works:
 * 1. Records the last execution time
 * 2. If elapsed time since last execution exceeds the limit, execute the function
 * 3. Otherwise ignore the current call
 * 
 * @template T - Function type
 * @param func - Function to throttle
 * @param limit - Time limit in milliseconds
 * @returns Throttled function
 * 
 * @example
 * ```typescript
 * // Scroll event throttle
 * const throttledScroll = throttle(() => {
 *   updateScrollIndicator();
 * }, 100);
 * 
 * window.addEventListener('scroll', throttledScroll);
 * 
 * // Button click throttle
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
 * Throttle function with trailing call
 * 
 * The last call during the throttle period will execute after throttle ends.
 * Ensures the final state is correctly updated.
 * 
 * @template T - Function type
 * @param func - Function to throttle
 * @param limit - Time limit in milliseconds
 * @returns Throttled function
 * 
 * @example
 * ```typescript
 * // Drag event: responsive yet ensures final position is correct
 * const throttledDrag = throttleWithTrailing((position) => {
 *   updateElementPosition(position);
 * }, 16); // ~60fps
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
// Delay
// ============================================================

/**
 * Delay execution
 * 
 * Returns a Promise that resolves after a specified time.
 * Used for implementing wait effects in async/await syntax.
 * 
 * @param ms - Delay time in milliseconds
 * @returns Promise<void>
 * 
 * @example
 * ```typescript
 * // Basic usage
 * await delay(1000);
 * console.log('Executed after 1 second');
 * 
 * // Animation intervals
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
 * Delay with return value
 * 
 * Returns a specified value after a delay.
 * Suitable for scenarios requiring delayed return of specific values.
 * 
 * @template T - Return value type
 * @param ms - Delay time in milliseconds
 * @param value - Return value
 * @returns Promise<T>
 * 
 * @example
 * ```typescript
 * // Delayed default value
 * const result = await delayWith(1000, { status: 'ready' });
 * 
 * // Promise.race timeout fallback
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
 * Cancelable delay interface
 */
export interface CancelableDelay {
  /** Delay Promise */
  promise: Promise<void>;
  /** Cancel the delay */
  cancel: () => void;
}

/**
 * Create a cancelable delay
 * 
 * Creates a delay that can be manually cancelled.
 * Suitable for scenarios requiring interruption of waits, such as component unmount.
 * 
 * @param ms - Delay time in milliseconds
 * @returns Cancelable delay object
 * 
 * @example
 * ```typescript
 * const { promise, cancel } = cancelableDelay(5000);
 * 
 * // Set cancel condition
 * button.onclick = cancel;
 * 
 * try {
 *   await promise;
 *   showMessage('Operation completed');
 * } catch (e) {
 *   showMessage('Operation cancelled');
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
// Timeout
// ============================================================

/**
 * Promise wrapper with timeout
 * 
 * Adds a timeout limit to any Promise; throws an error on timeout.
 * Suitable for network requests, long-running operations requiring timeout control.
 * 
 * @template T - Promise resolution type
 * @param promise - Original Promise
 * @param ms - Timeout in milliseconds
 * @param message - Timeout error message (optional)
 * @returns Promise<T>
 * @throws Error when timed out
 * 
 * @example
 * ```typescript
 * // API request timeout
 * try {
 *   const data = await withTimeout(
 *     fetch('/api/data'),
 *     5000,
 *     'Request timed out, please check network'
 *   );
 * } catch (e) {
 *   if (e.message.includes('timed out')) {
 *     showRetryDialog();
 *   }
 * }
 * 
 * // File upload timeout
 * await withTimeout(uploadFile(file), 60000, 'Upload timed out');
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
// Scheduling
// ============================================================

/**
 * Execute in next macro task
 * 
 * Schedules a function for execution in the next event loop iteration.
 * Similar to Vue's nextTick but simpler.
 * 
 * @param fn - Function to execute
 * 
 * @example
 * ```typescript
 * // Ensure execution after DOM update
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
 * Request idle callback (with fallback)
 * 
 * Executes tasks during browser idle time with fallback for incompatible browsers.
 * Suitable for low-priority background tasks.
 * 
 * @param fn - Function to execute
 * @param options - Configuration options
 * @param options.timeout - Timeout in milliseconds
 * @returns Request ID
 * 
 * @example
 * ```typescript
 * // Send analytics data during idle time
 * requestIdleCallback(() => {
 *   sendAnalytics(analyticsData);
 * }, { timeout: 5000 });
 * 
 * // Preload resources during idle time
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
  // Fallback to setTimeout
  return setTimeout(fn, options?.timeout ?? 1) as unknown as number;
}