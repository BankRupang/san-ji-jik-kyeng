package com.bankrupang.sanjijk.user.domain.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class UserSuspendedException extends BaseException {
    public UserSuspendedException() {
        super(UserErrorCode.USER_SUSPENDED);
    }
}
