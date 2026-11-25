package com.sujan.tripitaka.chat;

import com.sujan.tripitaka.cost.UsageGuard;
import com.sujan.tripitaka.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AskControllerTest {

    private static MockHttpServletRequest request(String remote, String forwarded) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        if (forwarded != null) {
            request.addHeader("X-Forwarded-For", forwarded);
        }
        return request;
    }

    @Test
    void behindALocalProxyThePersonIsTheForwardedAddress() {
        assertEquals("203.0.113.7", AskController.person(request("127.0.0.1", "203.0.113.7, 10.0.0.1")));
        assertEquals("203.0.113.7", AskController.person(request("0:0:0:0:0:0:0:1", "203.0.113.7")));
    }

    @Test
    void aForwardedHeaderFromAnywhereElseIsIgnored() {
        assertEquals("198.51.100.4", AskController.person(request("198.51.100.4", "1.2.3.4")));
        assertEquals("127.0.0.1", AskController.person(request("127.0.0.1", null)));
    }

    @Test
    void overTheLimitIs429WithRetryAfterInBothLanguages() throws Exception {
        var service = mock(AskService.class);
        // Build the exception the way the guard does: a guard with no allowance for anyone.
        var guard = new UsageGuard(0, 0, 1000);
        UsageGuard.LimitReached limited = null;
        try {
            guard.acquire("x");
        } catch (UsageGuard.LimitReached e) {
            limited = e;
        }
        when(service.ask(any(), anyString())).thenThrow(limited);
        var mvc = MockMvcBuilders.standaloneSetup(new AskController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        var result = mvc.perform(post("/api/ask").contentType("application/json").content("{\"question\":\"What is kamma?\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail").exists())
                .andReturn();
        String detail = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(detail.contains("Please try again") && detail.contains("නැවත උත්සාහ කරන්න"));
    }
}
