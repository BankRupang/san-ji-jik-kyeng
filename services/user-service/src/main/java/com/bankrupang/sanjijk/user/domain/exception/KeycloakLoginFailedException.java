package com.bankrupang.sanjijk.user.domain.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class KeycloakLoginFailedException extends BaseException {
    public KeycloakLoginFailedException() {
        super(UserErrorCode.LOGIN_FAILED);
    }
}
