package io.brix.platform.admin.service;

import org.springframework.stereotype.Service;

import io.brix.platform.auth.flow.MfaLoginSupport;
import io.runtime.sdk.capability.AuthFlowCapability.LoginResult;
import io.runtime.sdk.capability.AuthFlowCapability.MfaVerifyCommand;

/** Platform-admin implementation of the AuthFlow MFA verification support SPI. */
@Service
public class PlatformMfaLoginSupport implements MfaLoginSupport {

    private final PlatformMfaLoginService platformMfaLoginService;

    public PlatformMfaLoginSupport(PlatformMfaLoginService platformMfaLoginService) {
        this.platformMfaLoginService = platformMfaLoginService;
    }

    @Override
    public LoginResult verify(MfaVerifyCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("MFA verify command is required");
        }
        return platformMfaLoginService.verifyToLoginResult(command.challengeToken(), command.otpCode(), null);
    }
}