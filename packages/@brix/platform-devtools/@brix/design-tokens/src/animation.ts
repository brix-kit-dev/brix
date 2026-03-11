/**
 * Copyright 2026 Brix Platform Authors
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
/**
 * @file animation.ts
 * @description Animation Tokens
 */

/**
 * Duration
 */
export const duration = {
  fastest: '50ms',
  faster: '100ms',
  fast: '150ms',
  normal: '200ms',
  slow: '300ms',
  slower: '400ms',
  slowest: '500ms',
};

/**
 * Easing Functions
 */
export const easing = {
  linear: 'linear',
  easeIn: 'cubic-bezier(0.4, 0, 1, 1)',
  easeOut: 'cubic-bezier(0, 0, 0.2, 1)',
  easeInOut: 'cubic-bezier(0.4, 0, 0.2, 1)',
  easeInBack: 'cubic-bezier(0.6, -0.28, 0.735, 0.045)',
  easeOutBack: 'cubic-bezier(0.175, 0.885, 0.32, 1.275)',
  easeInOutBack: 'cubic-bezier(0.68, -0.55, 0.265, 1.55)',
};

/**
 * Transition Properties
 */
export const transitionProperty = {
  none: 'none',
  all: 'all',
  default: 'color, background-color, border-color, text-decoration-color, fill, stroke, opacity, box-shadow, transform, filter, backdrop-filter',
  colors: 'color, background-color, border-color, text-decoration-color, fill, stroke',
  opacity: 'opacity',
  shadow: 'box-shadow',
  transform: 'transform',
};

/**
 * Keyframe Animations
 */
export const keyframes = {
  spin: {
    from: { transform: 'rotate(0deg)' },
    to: { transform: 'rotate(360deg)' },
  },
  ping: {
    '75%, 100%': { transform: 'scale(2)', opacity: '0' },
  },
  pulse: {
    '0%, 100%': { opacity: '1' },
    '50%': { opacity: '0.5' },
  },
  bounce: {
    '0%, 100%': {
      transform: 'translateY(-25%)',
      animationTimingFunction: 'cubic-bezier(0.8, 0, 1, 1)',
    },
    '50%': {
      transform: 'translateY(0)',
      animationTimingFunction: 'cubic-bezier(0, 0, 0.2, 1)',
    },
  },
  fadeIn: {
    from: { opacity: '0' },
    to: { opacity: '1' },
  },
  fadeOut: {
    from: { opacity: '1' },
    to: { opacity: '0' },
  },
  slideInUp: {
    from: { transform: 'translateY(100%)', opacity: '0' },
    to: { transform: 'translateY(0)', opacity: '1' },
  },
  slideInDown: {
    from: { transform: 'translateY(-100%)', opacity: '0' },
    to: { transform: 'translateY(0)', opacity: '1' },
  },
  slideInLeft: {
    from: { transform: 'translateX(-100%)', opacity: '0' },
    to: { transform: 'translateX(0)', opacity: '1' },
  },
  slideInRight: {
    from: { transform: 'translateX(100%)', opacity: '0' },
    to: { transform: 'translateX(0)', opacity: '1' },
  },
  scaleIn: {
    from: { transform: 'scale(0.95)', opacity: '0' },
    to: { transform: 'scale(1)', opacity: '1' },
  },
  scaleOut: {
    from: { transform: 'scale(1)', opacity: '1' },
    to: { transform: 'scale(0.95)', opacity: '0' },
  },
};

/**
 * Predefined Animations
 */
export const animations = {
  spin: `spin 1s ${easing.linear} infinite`,
  ping: `ping 1s ${easing.easeInOut} infinite`,
  pulse: `pulse 2s ${easing.easeInOut} infinite`,
  bounce: `bounce 1s infinite`,
  fadeIn: `fadeIn ${duration.normal} ${easing.easeOut}`,
  fadeOut: `fadeOut ${duration.normal} ${easing.easeIn}`,
  slideInUp: `slideInUp ${duration.slow} ${easing.easeOut}`,
  slideInDown: `slideInDown ${duration.slow} ${easing.easeOut}`,
  slideInLeft: `slideInLeft ${duration.slow} ${easing.easeOut}`,
  slideInRight: `slideInRight ${duration.slow} ${easing.easeOut}`,
  scaleIn: `scaleIn ${duration.normal} ${easing.easeOut}`,
  scaleOut: `scaleOut ${duration.normal} ${easing.easeIn}`,
};

/**
 * Animation Aggregate
 */
export const animation = {
  duration,
  easing,
  transitionProperty,
  keyframes,
  animations,
};

/**
 * Get Transition Style
 */
export function getTransition(
  property: keyof typeof transitionProperty = 'default',
  dur: keyof typeof duration = 'normal',
  ease: keyof typeof easing = 'easeInOut'
): string {
  return `${transitionProperty[property]} ${duration[dur]} ${easing[ease]}`;
}
