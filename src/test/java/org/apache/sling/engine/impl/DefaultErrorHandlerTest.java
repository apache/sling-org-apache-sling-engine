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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Hashtable;

import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.api.servlets.JakartaErrorHandler;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultErrorHandler}: with no {@code ErrorHandler}/
 * {@code JakartaErrorHandler} service bound, the built-in error response must
 * not leak the exception's stacktrace, the {@link RequestProgressTracker}
 * dump, or the server/JVM/OS fingerprint footer to the client.
 */
public class DefaultErrorHandlerTest {

    @Rule
    public final OsgiContext context = new OsgiContext();

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
        when(request.getHeader("Accept")).thenReturn("text/html");
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

        // the server/JVM/OS fingerprint footer must never be written either
        assertFalse("response body must not contain a server/address footer", body.contains("<address>"));

        // a minimal, generic error page is still rendered
        assertTrue(body.contains("RequestURI="));
    }

    @Test
    public void testHandleStatusWithoutDelegateStillRendersMessage() throws Exception {
        when(request.getHeader("Accept")).thenReturn("text/html");

        handler.handleError(404, "not found", request, response);

        verify(response).setStatus(404);
        responseBody.flush();
        final String body = responseBody.toString();
        assertTrue(body.contains("not found"));
        assertFalse("response body must not contain a server/address footer", body.contains("<address>"));
    }

    @Test
    public void testHandleStatusWithoutDelegateForNonHtmlRequestDoesNotRenderBody() throws Exception {
        when(request.getHeader("Accept")).thenReturn("application/json");

        handler.handleError(404, "not found", request, response);

        verify(response).setStatus(404);
        verify(response).flushBuffer();
        verify(response, never()).getWriter();
        assertEquals("", responseBody.toString());
    }

    @Test
    public void testHandleThrowableWithoutDelegateForNonHtmlRequestDoesNotRenderBody() throws Exception {
        when(request.getHeader("Accept")).thenReturn("text/plain");

        handler.handleError(new IllegalStateException("some internal detail"), request, response);

        verify(response).setStatus(500);
        verify(response).flushBuffer();
        verify(response, never()).getWriter();
        assertEquals("", responseBody.toString());
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

    // ---------- named / non-html delegate selection ----------

    @SuppressWarnings("unchecked")
    private ServiceReference<JakartaErrorHandler> registerNamed(
            final JakartaErrorHandler eh, final int ranking, final Object names) {
        final Hashtable<String, Object> props = new Hashtable<>();
        props.put(JakartaErrorHandler.PROPERTY_NAME, names);
        props.put(Constants.SERVICE_RANKING, ranking);
        return (ServiceReference<JakartaErrorHandler>) context.bundleContext()
                .registerService(JakartaErrorHandler.class, eh, props)
                .getReference();
    }

    private void accept(final String accept) {
        when(request.getHeader("Accept")).thenReturn(accept);
    }

    @Test
    public void testNonHtmlRequestUsesNonHtmlDelegateForStatus() throws Exception {
        final JakartaErrorHandler main = mock(JakartaErrorHandler.class);
        final JakartaErrorHandler nonHtml = mock(JakartaErrorHandler.class);
        handler.setDelegate(null, main);
        handler.addNamedDelegate(registerNamed(nonHtml, 0, JakartaErrorHandler.NAME_NONHTML), nonHtml);
        accept("application/json");

        handler.handleError(404, "not found", request, response);

        verify(nonHtml).handleError(404, "not found", request, response);
        verifyNoInteractions(main);
    }

    @Test
    public void testNonHtmlRequestUsesNonHtmlDelegateForThrowable() throws Exception {
        final JakartaErrorHandler main = mock(JakartaErrorHandler.class);
        final JakartaErrorHandler nonHtml = mock(JakartaErrorHandler.class);
        handler.setDelegate(null, main);
        handler.addNamedDelegate(registerNamed(nonHtml, 0, JakartaErrorHandler.NAME_NONHTML), nonHtml);
        accept("application/json");

        final Exception cause = new IllegalStateException("boom");
        handler.handleError(cause, request, response);

        verify(nonHtml).handleError(cause, request, response);
        verifyNoInteractions(main);
    }

    @Test
    public void testHtmlRequestWithWildcardUsesNonHtmlDelegate() throws Exception {
        final JakartaErrorHandler main = mock(JakartaErrorHandler.class);
        final JakartaErrorHandler nonHtml = mock(JakartaErrorHandler.class);
        handler.setDelegate(null, main);
        handler.addNamedDelegate(registerNamed(nonHtml, 0, JakartaErrorHandler.NAME_NONHTML), nonHtml);
        accept("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

        handler.handleError(500, "error", request, response);

        verify(nonHtml).handleError(500, "error", request, response);
        verifyNoInteractions(main);
    }

    @Test
    public void testMissingAcceptHeaderIsTreatedAsNonHtml() throws Exception {
        final JakartaErrorHandler main = mock(JakartaErrorHandler.class);
        final JakartaErrorHandler nonHtml = mock(JakartaErrorHandler.class);
        handler.setDelegate(null, main);
        handler.addNamedDelegate(registerNamed(nonHtml, 0, JakartaErrorHandler.NAME_NONHTML), nonHtml);

        handler.handleError(404, null, request, response);

        verify(nonHtml).handleError(404, null, request, response);
        verifyNoInteractions(main);
    }

    @Test
    public void testBlankAcceptHeaderIsTreatedAsNonHtml() throws Exception {
        final JakartaErrorHandler main = mock(JakartaErrorHandler.class);
        final JakartaErrorHandler nonHtml = mock(JakartaErrorHandler.class);
        handler.setDelegate(null, main);
        handler.addNamedDelegate(registerNamed(nonHtml, 0, JakartaErrorHandler.NAME_NONHTML), nonHtml);
        accept("  ");

        handler.handleError(404, null, request, response);

        verify(nonHtml).handleError(404, null, request, response);
        verifyNoInteractions(main);
    }

    @Test
    public void testMissingAcceptHeaderWithoutNonHtmlDelegateUsesMainDelegate() throws Exception {
        final JakartaErrorHandler main = mock(JakartaErrorHandler.class);
        handler.setDelegate(null, main);

        handler.handleError(404, null, request, response);

        verify(main).handleError(404, null, request, response);
    }

    @Test
    public void testWildcardAcceptIsTreatedAsNonHtml() throws Exception {
        final JakartaErrorHandler main = mock(JakartaErrorHandler.class);
        final JakartaErrorHandler nonHtml = mock(JakartaErrorHandler.class);
        handler.setDelegate(null, main);
        handler.addNamedDelegate(registerNamed(nonHtml, 0, JakartaErrorHandler.NAME_NONHTML), nonHtml);
        accept("*/*");

        handler.handleError(404, null, request, response);

        verify(nonHtml).handleError(404, null, request, response);
        verifyNoInteractions(main);
    }

    @Test
    public void testWildcardWithLowerQualityIsTreatedAsNonHtml() throws Exception {
        final JakartaErrorHandler main = mock(JakartaErrorHandler.class);
        final JakartaErrorHandler nonHtml = mock(JakartaErrorHandler.class);
        handler.setDelegate(null, main);
        handler.addNamedDelegate(registerNamed(nonHtml, 0, JakartaErrorHandler.NAME_NONHTML), nonHtml);
        accept("application/json, */*;q=0.1");

        handler.handleError(404, null, request, response);

        verify(nonHtml).handleError(404, null, request, response);
        verifyNoInteractions(main);
    }

    @Test
    public void testTextWildcardAcceptIsTreatedAsHtml() throws Exception {
        final JakartaErrorHandler main = mock(JakartaErrorHandler.class);
        final JakartaErrorHandler nonHtml = mock(JakartaErrorHandler.class);
        handler.setDelegate(null, main);
        handler.addNamedDelegate(registerNamed(nonHtml, 0, JakartaErrorHandler.NAME_NONHTML), nonHtml);
        accept("text/*");

        handler.handleError(404, null, request, response);

        verify(main).handleError(404, null, request, response);
        verifyNoInteractions(nonHtml);
    }

    @Test
    public void testHtmlWithLowerQualityIsStillTreatedAsHtml() throws Exception {
        final JakartaErrorHandler main = mock(JakartaErrorHandler.class);
        final JakartaErrorHandler nonHtml = mock(JakartaErrorHandler.class);
        handler.setDelegate(null, main);
        handler.addNamedDelegate(registerNamed(nonHtml, 0, JakartaErrorHandler.NAME_NONHTML), nonHtml);
        accept("application/json, text/html;q=0.1");

        handler.handleError(404, null, request, response);

        verify(main).handleError(404, null, request, response);
        verifyNoInteractions(nonHtml);
    }

    @Test
    public void testRequestParametersAreNotEvaluated() throws Exception {
        final JakartaErrorHandler main = mock(JakartaErrorHandler.class);
        final JakartaErrorHandler nonHtml = mock(JakartaErrorHandler.class);
        handler.setDelegate(null, main);
        handler.addNamedDelegate(registerNamed(nonHtml, 0, JakartaErrorHandler.NAME_NONHTML), nonHtml);
        accept("application/json");
        // parameter parsing might be the cause of the error being handled
        when(request.getParameter(any())).thenThrow(new IllegalStateException("parameter parsing failed"));

        handler.handleError(400, null, request, response);

        verify(nonHtml).handleError(400, null, request, response);
        verifyNoInteractions(main);
        verify(request, never()).getParameter(any());
    }

    @Test
    public void testNonHtmlRequestWithoutNonHtmlDelegateFallsBackToMainDelegate() throws Exception {
        final JakartaErrorHandler main = mock(JakartaErrorHandler.class);
        handler.setDelegate(null, main);
        accept("application/json");

        handler.handleError(404, null, request, response);

        verify(main).handleError(404, null, request, response);
    }

    @Test
    public void testNonHtmlRequestWithoutAnyDelegateUsesBuiltInResponse() throws Exception {
        accept("application/json");

        handler.handleError(404, "not found", request, response);

        verify(response).setStatus(404);
        responseBody.flush();
        assertTrue(responseBody.toString().contains("not found"));
    }

    @Test
    public void testNonHtmlDelegateWithoutMainDelegate() throws Exception {
        final JakartaErrorHandler nonHtml = mock(JakartaErrorHandler.class);
        handler.addNamedDelegate(registerNamed(nonHtml, 0, JakartaErrorHandler.NAME_NONHTML), nonHtml);

        accept("application/json");
        handler.handleError(404, null, request, response);
        verify(nonHtml).handleError(404, null, request, response);

        // html requests must not be routed to the non-html delegate
        accept("text/html");
        handler.handleError(403, null, request, response);
        verify(nonHtml, never()).handleError(403, null, request, response);
        verify(response).setStatus(403);
    }

    @Test
    public void testNamedDelegateWithOtherNameIsIgnored() throws Exception {
        final JakartaErrorHandler main = mock(JakartaErrorHandler.class);
        final JakartaErrorHandler other = mock(JakartaErrorHandler.class);
        handler.setDelegate(null, main);
        handler.addNamedDelegate(registerNamed(other, 100, "something-else"), other);
        accept("application/json");

        handler.handleError(404, null, request, response);

        verify(main).handleError(404, null, request, response);
        verifyNoInteractions(other);
    }

    @Test
    public void testNamedDelegateWithMultipleNamesIncludingNonHtml() throws Exception {
        final JakartaErrorHandler nonHtml = mock(JakartaErrorHandler.class);
        handler.addNamedDelegate(
                registerNamed(nonHtml, 0, new String[] {"something-else", JakartaErrorHandler.NAME_NONHTML}), nonHtml);
        accept("application/json");

        handler.handleError(404, null, request, response);

        verify(nonHtml).handleError(404, null, request, response);
    }

    @Test
    public void testHighestRankedNonHtmlDelegateWinsRegardlessOfBindOrder() throws Exception {
        final JakartaErrorHandler low = mock(JakartaErrorHandler.class);
        final JakartaErrorHandler high = mock(JakartaErrorHandler.class);
        final JakartaErrorHandler mid = mock(JakartaErrorHandler.class);
        handler.addNamedDelegate(registerNamed(low, -10, JakartaErrorHandler.NAME_NONHTML), low);
        handler.addNamedDelegate(registerNamed(high, 100, JakartaErrorHandler.NAME_NONHTML), high);
        handler.addNamedDelegate(registerNamed(mid, 10, JakartaErrorHandler.NAME_NONHTML), mid);
        accept("application/json");

        handler.handleError(404, null, request, response);

        verify(high).handleError(404, null, request, response);
        verifyNoInteractions(low, mid);
    }

    @Test
    public void testOlderNonHtmlDelegateWinsOnEqualRanking() throws Exception {
        final JakartaErrorHandler first = mock(JakartaErrorHandler.class);
        final JakartaErrorHandler second = mock(JakartaErrorHandler.class);
        final ServiceReference<JakartaErrorHandler> firstRef =
                registerNamed(first, 0, JakartaErrorHandler.NAME_NONHTML);
        final ServiceReference<JakartaErrorHandler> secondRef =
                registerNamed(second, 0, JakartaErrorHandler.NAME_NONHTML);
        // bind in reverse registration order
        handler.addNamedDelegate(secondRef, second);
        handler.addNamedDelegate(firstRef, first);
        accept("application/json");

        handler.handleError(404, null, request, response);

        verify(first).handleError(404, null, request, response);
        verifyNoInteractions(second);
    }

    @Test
    public void testRemovingNonHtmlDelegateFallsBackToNextRankedAndThenToMain() throws Exception {
        final JakartaErrorHandler main = mock(JakartaErrorHandler.class);
        final JakartaErrorHandler low = mock(JakartaErrorHandler.class);
        final JakartaErrorHandler high = mock(JakartaErrorHandler.class);
        handler.setDelegate(null, main);
        final ServiceReference<JakartaErrorHandler> lowRef = registerNamed(low, 0, JakartaErrorHandler.NAME_NONHTML);
        final ServiceReference<JakartaErrorHandler> highRef = registerNamed(high, 10, JakartaErrorHandler.NAME_NONHTML);
        handler.addNamedDelegate(lowRef, low);
        handler.addNamedDelegate(highRef, high);
        accept("application/json");

        handler.removeNamedDelegate(highRef);
        handler.handleError(404, null, request, response);
        verify(low).handleError(404, null, request, response);
        verifyNoInteractions(high);

        handler.removeNamedDelegate(lowRef);
        handler.handleError(410, null, request, response);
        verify(main).handleError(410, null, request, response);
        verify(low, never()).handleError(410, null, request, response);
    }

    @Test
    public void testRemovingUnknownNamedDelegateIsHarmless() throws Exception {
        final JakartaErrorHandler nonHtml = mock(JakartaErrorHandler.class);
        final JakartaErrorHandler unknown = mock(JakartaErrorHandler.class);
        handler.addNamedDelegate(registerNamed(nonHtml, 0, JakartaErrorHandler.NAME_NONHTML), nonHtml);
        accept("application/json");

        handler.removeNamedDelegate(registerNamed(unknown, 0, JakartaErrorHandler.NAME_NONHTML));
        handler.handleError(404, null, request, response);

        verify(nonHtml).handleError(404, null, request, response);
    }

    @Test
    public void testFailingNonHtmlDelegateFallsBackToBuiltInResponse() throws Exception {
        final JakartaErrorHandler main = mock(JakartaErrorHandler.class);
        final JakartaErrorHandler nonHtml = mock(JakartaErrorHandler.class);
        doThrow(new IOException("failed"))
                .when(nonHtml)
                .handleError(
                        anyInt(),
                        any(),
                        any(SlingJakartaHttpServletRequest.class),
                        any(SlingJakartaHttpServletResponse.class));
        handler.setDelegate(null, main);
        handler.addNamedDelegate(registerNamed(nonHtml, 0, JakartaErrorHandler.NAME_NONHTML), nonHtml);
        accept("application/json");

        handler.handleError(404, "not found", request, response);

        verify(response).reset();
        verify(response).setStatus(404);
        verifyNoInteractions(main);
        responseBody.flush();
        assertTrue(responseBody.toString().contains("not found"));
    }

    @Test
    public void testDisabledAcceptHeaderCheckAlwaysUsesMainDelegate() throws Exception {
        final JakartaErrorHandler main = mock(JakartaErrorHandler.class);
        final JakartaErrorHandler nonHtml = mock(JakartaErrorHandler.class);
        handler.setDelegate(null, main);
        handler.addNamedDelegate(registerNamed(nonHtml, 0, JakartaErrorHandler.NAME_NONHTML), nonHtml);
        handler.setEnableAcceptHeaderCheck(false);
        accept("application/json");

        handler.handleError(404, null, request, response);
        final Exception cause = new IllegalStateException("boom");
        handler.handleError(cause, request, response);

        verify(main).handleError(404, null, request, response);
        verify(main).handleError(cause, request, response);
        verifyNoInteractions(nonHtml);
        verify(request, never()).getHeader(any());
    }

    @Test
    public void testDisabledAcceptHeaderCheckWithoutMainDelegateUsesBuiltInResponse() throws Exception {
        final JakartaErrorHandler nonHtml = mock(JakartaErrorHandler.class);
        handler.addNamedDelegate(registerNamed(nonHtml, 0, JakartaErrorHandler.NAME_NONHTML), nonHtml);
        handler.setEnableAcceptHeaderCheck(false);
        accept("application/json");

        handler.handleError(404, "not found", request, response);

        verifyNoInteractions(nonHtml);
        verify(response).setStatus(404);
        responseBody.flush();
        assertTrue(responseBody.toString().contains("not found"));
    }

    @Test
    public void testReEnablingAcceptHeaderCheckUsesNonHtmlDelegateAgain() throws Exception {
        final JakartaErrorHandler main = mock(JakartaErrorHandler.class);
        final JakartaErrorHandler nonHtml = mock(JakartaErrorHandler.class);
        handler.setDelegate(null, main);
        handler.addNamedDelegate(registerNamed(nonHtml, 0, JakartaErrorHandler.NAME_NONHTML), nonHtml);
        accept("application/json");

        handler.setEnableAcceptHeaderCheck(false);
        handler.handleError(404, null, request, response);
        verify(main).handleError(404, null, request, response);

        handler.setEnableAcceptHeaderCheck(true);
        handler.handleError(410, null, request, response);
        verify(nonHtml).handleError(410, null, request, response);
        verify(main, never()).handleError(410, null, request, response);
    }
}
