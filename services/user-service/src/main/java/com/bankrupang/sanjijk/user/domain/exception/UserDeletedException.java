package com.bankrupang.sanjijk.user.domain.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class UserDeletedException extends BaseException {
    public UserDeletedException() {
        super(UserErrorCode.USER_DELETED);
    }
}
