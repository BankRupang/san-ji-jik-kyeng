package com.bankrupang.sanjijk.user.domain.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class UserUsernameExistsException extends BaseException {
    public UserUsernameExistsException() {
        super(UserErrorCode.USERNAME_ALREADY_EXISTS);
    }
}
