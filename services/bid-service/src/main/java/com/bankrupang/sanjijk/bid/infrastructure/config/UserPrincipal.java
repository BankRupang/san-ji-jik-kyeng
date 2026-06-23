package com.bankrupang.sanjijk.bid.infrastructure.config;

import java.security.Principal;

public record UserPrincipal(String name) implements Principal {
    @Override
    public String getName() {
        return name;
    }
}
