package com.bankrupang.sanjijk.bid.infrastructure.config;

import java.security.Principal;

public record UserPrincipal(String name, String role) implements Principal {
    @Override
    public String getName() {
        return name;
    }

}
