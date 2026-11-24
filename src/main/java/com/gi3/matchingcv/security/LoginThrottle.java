package com.gi3.matchingcv.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verrouillage temporaire d'un compte après plusieurs échecs de connexion.
 * Complète la limite par adresse IP ({@link AccountRateLimitFilter}) : une attaque
 * répartie sur plusieurs adresses ne peut pas tester indéfiniment un même compte.
 */
@Component
public class LoginThrottle {

    static final int MAX_ECHECS = 5;
    static final Duration FENETRE = Duration.ofMinutes(15);

    private record Etat(long debut, int echecs) {}

    private final ConcurrentHashMap<String, Etat> etats = new ConcurrentHashMap<>();

    private static String cle(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Minutes restantes de blocage, ou 0 si la connexion est autorisée. */
    public long minutesRestantes(String email) {
        Etat e = etats.get(cle(email));
        if (e == null || e.echecs() < MAX_ECHECS) return 0;
        long reste = e.debut() + FENETRE.toMillis() - System.currentTimeMillis();
        if (reste <= 0) {
            etats.remove(cle(email));
            return 0;
        }
        return Math.max(1, (reste + 59_999) / 60_000);
    }

    public void echec(String email) {
        long now = System.currentTimeMillis();
        if (etats.size() > 50_000) etats.entrySet().removeIf(x -> now - x.getValue().debut() > FENETRE.toMillis());
        etats.compute(cle(email), (k, old) -> old == null || now - old.debut() > FENETRE.toMillis()
                ? new Etat(now, 1) : new Etat(old.debut(), old.echecs() + 1));
    }

    public void succes(String email) {
        etats.remove(cle(email));
    }
}
