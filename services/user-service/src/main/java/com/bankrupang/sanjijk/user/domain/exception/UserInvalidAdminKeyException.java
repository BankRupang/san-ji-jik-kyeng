package com.bankrupang.sanjijk.user.domain.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class UserInvalidAdminKeyException extends BaseException {
    public UserInvalidAdminKeyException() {
        super(UserErrorCode.INVALID_ADMIN_KEY);
    }
}
