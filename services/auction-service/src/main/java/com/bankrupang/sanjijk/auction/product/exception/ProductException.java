package com.bankrupang.sanjijk.auction.product.exception;

import com.bankrupang.sanjijk.common.exception.BaseException;

public class ProductException extends BaseException {

    public ProductException(ProductErrorCode errorCode) {
        super(errorCode);
    }

}
