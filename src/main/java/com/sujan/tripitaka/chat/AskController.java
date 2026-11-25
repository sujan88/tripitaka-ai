package com.sujan.tripitaka.chat;

import com.sujan.tripitaka.model.AskRequest;
import com.sujan.tripitaka.model.AskResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** JSON API used by the web UI now and by a mobile client later. */
@RestController
@RequestMapping("/api")
public class AskController {

    private final AskService askService;

    public AskController(AskService askService) {
        this.askService = askService;
    }

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request, HttpServletRequest http) {
        return askService.ask(request, person(http));
    }

    /**
     * Who is asking, for the usage limits: the caller's address. Behind a proxy on this machine (Tailscale Funnel,
     * a Cloudflare tunnel, Caddy) every request arrives from loopback, so the first X-Forwarded-For address is used
     * instead; from anywhere else that header is ignored, because a caller could set it to anything.
     */
    static String person(HttpServletRequest http) {
        String remote = http.getRemoteAddr();
        boolean viaLocalProxy = remote != null && (remote.startsWith("127.") || remote.equals("0:0:0:0:0:0:0:1")
                || remote.equals("::1"));
        String forwarded = http.getHeader("X-Forwarded-For");
        if (viaLocalProxy && forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return remote == null ? "unknown" : remote;
    }
}
