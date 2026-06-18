package com.bankrupang.sanjijk.user.domain.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class UserInvalidRoleForSignupException extends BaseException {
    public UserInvalidRoleForSignupException() {
        super(UserErrorCode.INVALID_ROLE_FOR_SIGNUP);
    }
}
