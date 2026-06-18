package com.bankrupang.sanjijk.user.domain.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class UserBusinessNumberExistsException extends BaseException {
    public UserBusinessNumberExistsException() {
        super(UserErrorCode.BUSINESS_NUMBER_ALREADY_EXISTS);
    }
}
