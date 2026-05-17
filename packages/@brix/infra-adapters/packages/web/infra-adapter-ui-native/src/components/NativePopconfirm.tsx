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
 * @file Native Popconfirm Component
 * @description Native HTML/CSS implementation of the Popconfirm contract
 */

import React, { useState, useRef, useEffect } from 'react';
import type { FC } from 'react';
import type { PopconfirmProps } from '@brix-sdk/runtime-sdk-api-web';

export const NativePopconfirm: FC<PopconfirmProps> = ({
  title,
  onConfirm,
  onCancel,
  okText = 'OK',
  cancelText = 'Cancel',
  disabled = false,
  children,
  style,
  className,
}) => {
  const [visible, setVisible] = useState(false);
  const wrapperRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setVisible(false);
      }
    };
    if (visible) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [visible]);

  const handleConfirm = () => {
    setVisible(false);
    onConfirm?.();
  };

  const handleCancel = () => {
    setVisible(false);
    onCancel?.();
  };

  return (
    <div ref={wrapperRef} className={className} style={{ position: 'relative', display: 'inline-block', ...style }}>
      <div onClick={disabled ? undefined : () => setVisible(!visible)}>
        {children}
      </div>
      {visible && (
        <div
          style={{
            position: 'absolute',
            bottom: '100%',
            left: '50%',
            transform: 'translateX(-50%)',
            marginBottom: 8,
            padding: '12px 16px',
            backgroundColor: '#fff',
            borderRadius: 8,
            boxShadow: '0 6px 16px rgba(0,0,0,0.12)',
            zIndex: 1000,
            minWidth: 180,
          }}
        >
          <div style={{ marginBottom: 12, fontSize: 14, color: '#333' }}>
            {title}
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <button
              onClick={handleCancel}
              style={{
                padding: '4px 12px',
                fontSize: 13,
                border: '1px solid #d9d9d9',
                borderRadius: 4,
                backgroundColor: '#fff',
                cursor: 'pointer',
              }}
            >
              {cancelText}
            </button>
            <button
              onClick={handleConfirm}
              style={{
                padding: '4px 12px',
                fontSize: 13,
                border: 'none',
                borderRadius: 4,
                backgroundColor: '#1677ff',
                color: '#fff',
                cursor: 'pointer',
              }}
            >
              {okText}
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default NativePopconfirm;
