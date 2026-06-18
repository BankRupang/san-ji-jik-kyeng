package com.bankrupang.sanjijk.user.domain.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class UserEmailExistsException extends BaseException {
    public UserEmailExistsException() {
        super(UserErrorCode.EMAIL_ALREADY_EXISTS);
    }
}
