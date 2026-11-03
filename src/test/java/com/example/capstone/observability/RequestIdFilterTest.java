package com.example.capstone.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    private String run(String incomingHeader, AtomicReference<String> mdcDuringRequest, MockHttpServletResponse response)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (incomingHeader != null) {
            request.addHeader(RequestIdFilter.HEADER, incomingHeader);
        }
        FilterChain chain = (req, res) -> mdcDuringRequest.set(MDC.get(RequestIdFilter.MDC_KEY));
        filter.doFilter(request, response, chain);
        return response.getHeader(RequestIdFilter.HEADER);
    }

    @Test
    void generatesAnIdWhenTheClientSendsNone() throws Exception {
        AtomicReference<String> inMdc = new AtomicReference<>();

        String id = run(null, inMdc, new MockHttpServletResponse());

        assertNotNull(id);
        assertEquals(id, inMdc.get());
    }

    @Test
    void reusesASafeClientId() throws Exception {
        AtomicReference<String> inMdc = new AtomicReference<>();

        String id = run("client-trace_123", inMdc, new MockHttpServletResponse());

        assertEquals("client-trace_123", id);
        assertEquals("client-trace_123", inMdc.get());
    }

    @Test
    void replacesAnUnsafeClientIdSoLogLinesCannotBeInjected() throws Exception {
        AtomicReference<String> inMdc = new AtomicReference<>();

        String id = run("abc\nERROR fake log line", inMdc, new MockHttpServletResponse());

        assertNotEquals("abc\nERROR fake log line", id);
        assertTrue(id.matches("[0-9a-f-]{36}"));
    }

    @Test
    void clearsTheMdcAfterTheRequest() throws Exception {
        run("abc", new AtomicReference<>(), new MockHttpServletResponse());

        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }
}
