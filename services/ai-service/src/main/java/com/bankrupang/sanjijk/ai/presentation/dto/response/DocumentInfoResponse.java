package com.bankrupang.sanjijk.ai.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DocumentInfoResponse {

    private String title;
    private String source;
    private int chunkCount;
}
