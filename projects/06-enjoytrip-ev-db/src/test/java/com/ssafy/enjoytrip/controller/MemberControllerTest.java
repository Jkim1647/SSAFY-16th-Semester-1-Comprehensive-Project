package com.ssafy.enjoytrip.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MemberControllerTest {

    @Test
    void safeNextKeepsInternalPaths() {
        assertEquals("/plan?action=detail&planId=4", MemberController.safeNext("/plan?action=detail&planId=4"));
        assertEquals("/ev?action=station&statId=ST600925", MemberController.safeNext("/ev?action=station&statId=ST600925"));
    }

    @Test
    void safeNextRejectsExternalOrObfuscatedTargets() {
        for (String bad : new String[] { "//evil.com", "/\t/evil.com", "/\\evil.com", " /x", "https://evil.com",
                "/%0d%0aSet-Cookie:x", "/\n/evil.com", "", null }) {
            assertEquals("/", MemberController.safeNext(bad), "막아야 함: " + bad);
        }
    }
}
