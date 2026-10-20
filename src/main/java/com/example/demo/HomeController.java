package com.example.demo;

import java.net.InetAddress;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    private final GreetingService service;

    public HomeController(GreetingService service) {
        this.service = service;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String home() throws Exception {
        String pod = InetAddress.getLocalHost().getHostName();
        return "<!doctype html><html><head><meta charset='utf-8'><title>CI/CD Demo</title>"
             + "<style>body{font-family:sans-serif;max-width:640px;margin:4rem auto;padding:0 1rem}"
             + "code{background:#eee;padding:2px 6px;border-radius:4px}</style></head><body>"
             + "<h1>" + service.message() + "</h1>"
             + "<p>Build: <code>" + service.buildVersion() + "</code></p>"
             + "<p>Served by pod: <code>" + pod + "</code></p>"
             + "</body></html>";
    }

    @GetMapping("/api/info")
    public Map<String, String> info() {
        return Map.of("message", service.message(), "build", service.buildVersion());
    }
}
