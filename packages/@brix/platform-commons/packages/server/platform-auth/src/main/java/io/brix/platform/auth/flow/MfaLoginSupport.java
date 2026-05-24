package io.brix.platform.auth.flow;

import io.runtime.sdk.capability.AuthFlowCapability.LoginResult;
import io.runtime.sdk.capability.AuthFlowCapability.MfaVerifyCommand;

/** Internal support SPI for completing MFA login challenges. */
public interface MfaLoginSupport {

    LoginResult verify(MfaVerifyCommand command);
}