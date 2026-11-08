package com.gi3.matchingcv.security;

import com.gi3.matchingcv.model.enums.Role;
import java.io.Serializable;
import java.security.Principal;

public record AccountPrincipal(Long id, Role role, long version) implements Principal, Serializable {
    @Override public String getName() { return id.toString(); }
}
