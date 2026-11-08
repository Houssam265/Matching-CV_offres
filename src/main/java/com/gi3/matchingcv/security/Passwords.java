package com.gi3.matchingcv.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class Passwords {
    private static final BCryptPasswordEncoder ENCODER=new BCryptPasswordEncoder(12);
    private Passwords() {}
    public static boolean encoded(String value) { return value!=null && value.matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$"); }
    public static String hash(String password) { return ENCODER.encode(password); }
    public static boolean matches(String password,String stored) {
        if (password==null || stored==null) return false;
        return encoded(stored) ? ENCODER.matches(password,stored)
                : MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8),stored.getBytes(StandardCharsets.UTF_8));
    }
}
