package de.spinscale.linkrating.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BaseController {

    private final List<String> admins;

    public BaseController(List<String> admins) {
        this.admins = admins;
    }

    protected void enrichModelWithPrincipal(final Model model, final OAuth2User principal) {
        model.addAttribute("user", principal);
        model.addAttribute("is_admin", isAdmin(principal));
    }

    protected void ensureAdmin(OAuth2User principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        // ensure only admins can go here
        final String githubLogin = principal.getAttribute("login");
        if (!this.admins.contains(githubLogin)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    protected boolean isAdmin(OAuth2User principal) {
        if (principal == null) {
            return false;
        }
        final String githubLogin = principal.getAttribute("login");
        return this.admins.contains(githubLogin);
    }

    static List<String> loadAdmins() {
        final String[] admins = System.getenv("ADMINS").split(",");
        return Arrays.stream(admins).map(String::trim).collect(Collectors.toList());
    }
}
