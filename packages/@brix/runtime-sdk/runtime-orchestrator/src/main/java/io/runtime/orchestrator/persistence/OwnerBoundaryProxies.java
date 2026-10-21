/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.orchestrator.persistence;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;

import io.runtime.sdk.capability.UnitOfWork;

/**
 * Wraps objects provided by a data owner so that every call runs inside that owner's unit of work.
 *
 * <p>This is the runtime side of root blueprint BRX-RS-HOST-020 and BRX-RS-DATA-028: when another
 * module calls an internal contract, the caller's transaction is suspended and the data owner runs
 * the call in its own local transaction. {@link UnitOfWork#required} implements the suspension; this
 * class only routes every interface method through it. A call made while the owner's unit of work
 * is already active joins it.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class OwnerBoundaryProxies {

    private OwnerBoundaryProxies() {
    }

    /**
     * Wraps {@code target} so that every call of a {@code contractType} method runs in
     * {@code unitOfWork}.
     *
     * @param contractType interface exposed to other modules
     * @param target owner-side implementation
     * @param unitOfWork unit of work of the owner that implements {@code target}
     * @param <C> contract type
     * @return a proxy implementing only {@code contractType}
     */
    public static <C> C wrap(Class<C> contractType, C target, UnitOfWork unitOfWork) {
        Objects.requireNonNull(contractType, "contractType must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(unitOfWork, "unitOfWork must not be null");
        if (!contractType.isInterface()) {
            throw new IllegalArgumentException("Owner boundary requires an interface: " + contractType.getName());
        }
        if (!contractType.isInstance(target)) {
            throw new IllegalArgumentException(
                "Owner boundary target does not implement " + contractType.getName());
        }
        Object proxy = Proxy.newProxyInstance(
            contractType.getClassLoader(),
            new Class<?>[] {contractType},
            new OwnerBoundaryHandler(target, unitOfWork));
        return contractType.cast(proxy);
    }

    private static final class OwnerBoundaryHandler implements InvocationHandler {

        private final Object target;
        private final UnitOfWork unitOfWork;

        private OwnerBoundaryHandler(Object target, UnitOfWork unitOfWork) {
            this.target = target;
            this.unitOfWork = unitOfWork;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, args);
            }
            try {
                return unitOfWork.required(() -> invokeTarget(method, args));
            } catch (CheckedTargetFailure failure) {
                throw failure.getCause();
            }
        }

        private Object invokeTarget(Method method, Object[] args) {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new CheckedTargetFailure(cause);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Owner boundary cannot invoke " + method, e);
            }
        }

        private Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "OwnerBoundary[" + target + "]";
                default -> throw new IllegalStateException("Unsupported Object method " + method);
            };
        }
    }

    /**
     * Carries a checked exception thrown by the target through the unit of work so that the owner
     * transaction rolls back and the original exception reaches the caller unchanged.
     */
    private static final class CheckedTargetFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private CheckedTargetFailure(Throwable cause) {
            super(cause);
        }
    }
}
