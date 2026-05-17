/**
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * @file Native Steps Component
 * @description Native HTML/CSS implementation of the Steps contract
 */

import React from 'react';
import type { FC } from 'react';
import type { StepsProps } from '@brix-sdk/runtime-sdk-api-web';

export const NativeSteps: FC<StepsProps> = ({
  current = 0,
  items,
  direction = 'horizontal',
  size,
  onChange,
  style,
  className,
}) => {
  const isVertical = direction === 'vertical';
  const isSmall = size === 'small';

  return (
    <div
      className={className}
      style={{
        display: 'flex',
        flexDirection: isVertical ? 'column' : 'row',
        gap: isSmall ? 8 : 16,
        ...style,
      }}
    >
      {items.map((item, index) => {
        const status = item.status ?? (
          index < current ? 'finish' :
          index === current ? 'process' : 'wait'
        );
        const isClickable = onChange && !item.disabled;

        return (
          <div
            key={index}
            onClick={isClickable ? () => onChange(index) : undefined}
            style={{
              display: 'flex',
              alignItems: isVertical ? 'flex-start' : 'center',
              flexDirection: isVertical ? 'row' : 'column',
              gap: 8,
              cursor: isClickable ? 'pointer' : 'default',
              opacity: item.disabled ? 0.5 : 1,
              flex: isVertical ? undefined : 1,
            }}
          >
            <div
              style={{
                width: isSmall ? 24 : 32,
                height: isSmall ? 24 : 32,
                borderRadius: '50%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: isSmall ? 12 : 14,
                fontWeight: 600,
                backgroundColor:
                  status === 'finish' ? '#52c41a' :
                  status === 'process' ? '#1677ff' :
                  status === 'error' ? '#ff4d4f' : '#f0f0f0',
                color:
                  status === 'wait' ? '#999' : '#fff',
                flexShrink: 0,
              }}
            >
              {item.icon ?? (status === 'finish' ? '✓' : index + 1)}
            </div>
            <div style={{ textAlign: isVertical ? 'left' : 'center' }}>
              <div style={{
                fontSize: isSmall ? 13 : 14,
                fontWeight: status === 'process' ? 600 : 400,
                color: status === 'wait' ? '#999' : '#333',
              }}>
                {item.title}
              </div>
              {item.description && (
                <div style={{ fontSize: 12, color: '#999', marginTop: 2 }}>
                  {item.description}
                </div>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
};

export default NativeSteps;
