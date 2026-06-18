package com.bankrupang.sanjijk.user.domain.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class UserKeycloakCreationFailedException extends BaseException {
    public UserKeycloakCreationFailedException() {
        super(UserErrorCode.KEYCLOAK_USER_CREATION_FAILED);
    }
}
