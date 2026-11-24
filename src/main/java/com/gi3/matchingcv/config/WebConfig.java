package com.gi3.matchingcv.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Une seule page de connexion pour tous les rôles : l'ancienne adresse admin y renvoie.
        registry.addRedirectViewController("/admin-login.html", "/login.html");
    }
}
