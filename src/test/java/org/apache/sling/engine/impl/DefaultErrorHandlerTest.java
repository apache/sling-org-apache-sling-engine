/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.engine.impl;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.request.RequestProgressTracker;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultErrorHandler}: with no {@code ErrorHandler}/
 * {@code JakartaErrorHandler} service bound, the built-in error response must
 * not leak the exception's stacktrace or the {@link RequestProgressTracker}
 * dump to the client.
 */
public class DefaultErrorHandlerTest {

    private DefaultErrorHandler handler;
    private SlingJakartaHttpServletRequest request;
    private SlingJakartaHttpServletResponse response;
    private StringWriter responseBody;
    private RequestProgressTracker tracker;

    @Before
    public void setup() throws Exception {
        handler = new DefaultErrorHandler();

        tracker = mock(RequestProgressTracker.class);

        request = mock(SlingJakartaHttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/content/test");
        when(request.getRequestProgressTracker()).thenReturn(tracker);

        response = mock(SlingJakartaHttpServletResponse.class);
        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
    }

    @Test
    public void testHandleThrowableWithoutDelegateDoesNotLeakStacktraceOrTracker() throws Exception {
        final Exception cause = new IllegalStateException("some internal detail: /etc/secret-path");

        handler.handleError(cause, request, response);

        verify(response).setStatus(500);
        responseBody.flush();
        final String body = responseBody.toString();

        // the exception's stacktrace must never be written to the response
        assertFalse(
                "response body must not contain a stacktrace frame",
                body.contains("at org.apache.sling.engine.impl.DefaultErrorHandlerTest"));
        assertFalse("response body must not mention the stacktrace section", body.contains("Exception stacktrace"));

        // the RequestProgressTracker must never be dumped into the response
        assertFalse("response body must not contain the tracker dump section", body.contains("Request Progress"));
        verify(tracker, never()).dump(org.mockito.ArgumentMatchers.any(PrintWriter.class));

        // a minimal, generic error page is still rendered
        assertTrue(body.contains("RequestURI="));
    }

    @Test
    public void testHandleStatusWithoutDelegateStillRendersMessage() throws Exception {
        handler.handleError(404, "not found", request, response);

        verify(response).setStatus(404);
        responseBody.flush();
        assertTrue(responseBody.toString().contains("not found"));
    }

    @Test
    public void testHandleThrowableWithDelegateDoesNotUseFallback() throws Exception {
        final org.apache.sling.api.servlets.JakartaErrorHandler delegate =
                mock(org.apache.sling.api.servlets.JakartaErrorHandler.class);
        handler.setDelegate(null, delegate);

        final Exception cause = new IllegalStateException("boom");
        handler.handleError(cause, request, response);

        verify(delegate).handleError(cause, request, response);
        // the built-in fallback must not have written anything to the response
        responseBody.flush();
        assertTrue(responseBody.toString().isEmpty());
    }
}
