package com.bankrupang.sanjijk.user.domain.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class UserNotSuspendedException extends BaseException {
    public UserNotSuspendedException() {
        super(UserErrorCode.USER_NOT_SUSPENDED);
    }
}
