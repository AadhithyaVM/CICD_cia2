package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class GreetingServiceTest {

    @Test
    void messageIsNotEmpty() {
        GreetingService s = new GreetingService("abc123");
        assertFalse(s.message().isBlank());
    }

    @Test
    void buildVersionIsReturned() {
        GreetingService s = new GreetingService("abc123");
        assertEquals("abc123", s.buildVersion());
    }
}
