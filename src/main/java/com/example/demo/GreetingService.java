package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GreetingService {

    // CHANGE THIS TEXT during the redeployment demo (e.g. "v2") and push to Git.
    public static final String MESSAGE = "Hello from the CI/CD pipeline - version 2";

    private final String buildVersion;

    public GreetingService(@Value("${app.build-version:local}") String buildVersion) {
        this.buildVersion = buildVersion;
    }

    public String message() {
        return MESSAGE;
    }

    public String buildVersion() {
        return buildVersion;
    }
}
