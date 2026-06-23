package com.bankrupang.sanjijk.ai.infrastructure.config;

import org.apache.tika.Tika;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class InfrastructureConfig {

    @Value("${ai.document.chunk-size:500}")
    private int chunkSize;

    @Value("${ai.document.chunk-overlap:50}")
    private int chunkOverlap;

    @Value("${ai.document.tika-max-string-length:500000}")
    private int tikaMaxStringLength;

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    public Tika tika() {
        Tika tika = new Tika();
        tika.setMaxStringLength(tikaMaxStringLength);
        return tika;
    }

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter(chunkSize, chunkOverlap, 5, 10000, true);
    }
}
