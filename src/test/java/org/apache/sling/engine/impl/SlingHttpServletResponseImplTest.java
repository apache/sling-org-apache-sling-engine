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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.engine.impl.request.DispatchingInfo;
import org.apache.sling.engine.impl.request.RequestData;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SlingHttpServletResponseImplTest {

    private static final String ACTIVE_SERVLET_NAME = "activeServlet";
    String[] logMessages = {
        "0 TIMER_START{Request Processing}",
        "6 COMMENT timer_end format is {<elapsed microseconds>,<timer name>} <optional message>",
        "17 LOG Method=GET, PathInfo=null",
        "20 TIMER_START{handleSecurity}",
        "2104 TIMER_END{2081,handleSecurity} authenticator org.apache.sling.auth.core.impl.SlingAuthenticator@6367091e returns true",
        "2478 TIMER_START{ResourceResolution}",
        "2668 TIMER_END{189,ResourceResolution} URI=/content/slingshot.html resolves to Resource=JcrNodeResource, type=slingshot/Home, superType=null, path=/content/slingshot",
        "2678 LOG Resource Path Info: SlingRequestPathInfo: path='/content/slingshot', selectorString='null', extension='html', suffix='null'",
        "2678 TIMER_START{ServletResolution}",
        "2683 TIMER_START{resolveServlet(/content/slingshot)}",
        "3724 TIMER_END{1040,resolveServlet(/content/slingshot)} Using servlet /libs/slingshot/Home/html.jsp",
        "3727 TIMER_END{1047,ServletResolution} URI=/content/slingshot.html handled by Servlet=/libs/slingshot/Home/html.jsp",
        "3736 LOG Applying REQUESTfilters",
        "3751 LOG Calling filter: com.composum.sling.nodes.mount.remote.RemoteRequestFilter",
        "4722 TIMER_START{/libs/slingshot/Component/head.html.jsp#1}",
        "3757 LOG Calling filter: org.apache.sling.i18n.impl.I18NFilter",
        "4859 TIMER_END{135,/libs/slingshot/Component/head.html.jsp#1}",
        "3765 LOG Calling filter: org.apache.sling.engine.impl.debug.RequestProgressTrackerLogFilter",
        "2678 TIMER_START{ServletResolution}",
        "2683 TIMER_START{resolveServlet(/content/slingshot)}",
        "2678 TIMER_START{ServletResolution}",
        "2683 TIMER_START{resolveServlet(/content/slingshot)}",
        "3724 TIMER_END{1040,resolveServlet(/content/slingshot)} Using servlet /libs/slingshot/Home/html.jsp",
        "3727 TIMER_END{1047,ServletResolution} URI=/content/slingshot.html handled by Servlet=/libs/slingshot/Home/html.jsp",
        "3724 TIMER_END{1040,resolveServlet(/content/slingshot)} Using servlet /libs/slingshot/Home/html.jsp",
        "3727 TIMER_END{1047,ServletResolution} URI=/content/slingshot.html handled by Servlet=/libs/slingshot/Home/html.jsp",
        "3774 LOG Applying Componentfilters",
        "3797 TIMER_START{/libs/slingshot/Home/html.jsp#0}",
        "3946 LOG Adding bindings took 18 microseconds",
        "4405 LOG Including resource JcrNodeResource, type=slingshot/Home, superType=null, path=/content/slingshot (SlingRequestPathInfo: path='/content/slingshot', selectorString='head', extension='html', suffix='null')",
        "4414 TIMER_START{resolveServlet(/content/slingshot)}",
        "4670 TIMER_END{253,resolveServlet(/content/slingshot)} Using servlet /libs/slingshot/Component/head.html.jsp",
        "4673 LOG Applying Includefilters",
        "4722 TIMER_START{/libs/slingshot/Component/head.html.jsp#1}",
        "4749 LOG Adding bindings took 4 microseconds"
    };

    @Test
    public void testNoViolationChecksOnCommittedResponseWhenSendRedirect() throws IOException {
        final SlingJakartaHttpServletResponse orig = Mockito.mock(SlingJakartaHttpServletResponse.class);
        Mockito.when(orig.isCommitted()).thenReturn(true);

        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);

        final SlingJakartaHttpServletResponseImpl include = new SlingJakartaHttpServletResponseImpl(requestData, orig);
        SlingJakartaHttpServletResponseImpl spyInclude = Mockito.spy(include);

        spyInclude.sendRedirect("somewhere");

        spyInclude.setContentType("someOtherType");
        Mockito.verify(orig, times(1)).setContentType(Mockito.any());
        Mockito.verify(spyInclude, never()).checkContentTypeOverride(Mockito.any());
    }

    @Test
    public void testNoViolationChecksOnCommittedResponseWhenSendError() throws IOException {
        final SlingJakartaHttpServletResponse orig = Mockito.mock(SlingJakartaHttpServletResponse.class);

        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(requestData.getSlingRequestProcessor()).thenReturn(mock(SlingRequestProcessorImpl.class));

        final SlingJakartaHttpServletResponseImpl include = new SlingJakartaHttpServletResponseImpl(requestData, orig);
        SlingJakartaHttpServletResponseImpl spyInclude = Mockito.spy(include);

        spyInclude.sendError(501);
        // send error will eventually commit the response, let's mock this
        Mockito.when(orig.isCommitted()).thenReturn(true);

        spyInclude.setContentType("someOtherType");
        Mockito.verify(orig, times(1)).setContentType(Mockito.any());
        Mockito.verify(spyInclude, never()).checkContentTypeOverride(Mockito.any());
    }

    @Test
    public void testViolationChecksOnCommittedResponses() {
        final SlingJakartaHttpServletResponse orig = Mockito.mock(SlingJakartaHttpServletResponse.class);
        Mockito.when(orig.isCommitted()).thenReturn(true);

        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(requestData.getSlingRequestProcessor()).thenReturn(mock(SlingRequestProcessorImpl.class));
        final RequestProgressTracker rpt = mock(RequestProgressTracker.class);
        when(rpt.getMessages()).thenReturn(new ArrayList<String>().iterator());
        when(requestData.getRequestProgressTracker()).thenReturn(rpt);

        final SlingJakartaHttpServletResponseImpl include = new SlingJakartaHttpServletResponseImpl(requestData, orig);
        SlingJakartaHttpServletResponseImpl spyInclude = Mockito.spy(include);

        spyInclude.setContentType("someOtherType");
        Mockito.verify(orig, times(1)).setContentType(Mockito.any());
        Mockito.verify(spyInclude, Mockito.times(1)).checkContentTypeOverride(Mockito.any());
    }

    @Test
    public void testReset() {
        final SlingJakartaHttpServletResponse originalResponse = mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        DispatchingInfo dispatchingInfo = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(dispatchingInfo);
        dispatchingInfo.setProtectHeadersOnInclude(true);

        final HttpServletResponse includeResponse =
                new SlingJakartaHttpServletResponseImpl(requestData, originalResponse);

        when(originalResponse.isCommitted()).thenReturn(false);
        includeResponse.reset();
        verify(originalResponse, times(1)).isCommitted();
        Mockito.verifyNoMoreInteractions(originalResponse);

        when(originalResponse.isCommitted()).thenReturn(true);
        includeResponse.reset();
        verify(originalResponse, times(2)).isCommitted();
        verify(originalResponse, times(1)).reset();
        Mockito.verifyNoMoreInteractions(originalResponse);
    }

    @Test
    public void testResetOnError() {
        final SlingJakartaHttpServletResponseImpl originalResponse = mock(SlingJakartaHttpServletResponseImpl.class);
        final RequestData requestData = mock(RequestData.class);

        // Simulate an error dispatching scenario on a uncommitted response
        DispatchingInfo dispatchingInfo = new DispatchingInfo(DispatcherType.ERROR);
        final HttpServletResponse includeResponse =
                new SlingJakartaHttpServletResponseImpl(requestData, originalResponse);
        dispatchingInfo.setProtectHeadersOnInclude(true);
        when(requestData.getDispatchingInfo()).thenReturn(dispatchingInfo);
        when(originalResponse.isCommitted()).thenReturn(false);

        includeResponse.reset();
        verify(originalResponse, times(1)).reset();

        Mockito.verifyNoMoreInteractions(originalResponse);
    }

    private String callTesteeAndGetRequestProgressTrackerMessage(String[] logMessages) {
        final SlingJakartaHttpServletResponse orig = Mockito.mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        when(requestData.getActiveServletName()).thenReturn(ACTIVE_SERVLET_NAME);

        final SlingRequestProcessorImpl requestProcessor = mock(SlingRequestProcessorImpl.class);
        when(requestData.getSlingRequestProcessor()).thenReturn(requestProcessor);

        ArrayList<String> logMessagesList = new ArrayList<>(Arrays.asList(logMessages));
        when(requestProgressTracker.getMessages()).thenAnswer(invocation -> logMessagesList.iterator());
        info.setProtectHeadersOnInclude(true);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        include.setContentLength(54);
        include.setContentLengthLong(33L);
        include.setContentType("text/plain");
        include.setLocale(null);
        include.setBufferSize(4500);

        Mockito.verify(orig, never()).setContentLength(54);
        Mockito.verify(orig, never()).setContentLengthLong(33L);
        Mockito.verify(orig, never()).setContentType("text/plain");
        Mockito.verify(orig, never()).setLocale(null);
        Mockito.verify(orig, Mockito.times(1)).setBufferSize(4500);

        Mockito.verify(requestProcessor, atMostOnce()).setContentTypeHeaderState(Mockito.any());

        ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestProgressTracker, times(1)).log(logCaptor.capture());
        return logCaptor.getValue();
    }

    @Test
    public void testRecursiveCalls() {

        // build a string array which resembles the log of recursive includes (50 levels
        // deep)
        String[] recursivePartStrings = Arrays.copyOfRange(logMessages, 14, logMessages.length - 2);
        String[] concatenatedArray = Stream.concat(Arrays.stream(logMessages), Arrays.stream(recursivePartStrings))
                .toArray(String[]::new);
        for (int i = 0; i < 50; i++) {
            concatenatedArray = Stream.concat(Arrays.stream(concatenatedArray), Arrays.stream(recursivePartStrings))
                    .toArray(String[]::new);
        }

        String logMessage = callTesteeAndGetRequestProgressTrackerMessage(concatenatedArray);

        // validate that the log message is cut off and only the last MAX_NR_OF_MESSAGES
        // remain in the log message, check for the cut message
        assertTrue(logMessage.contains("... cut 504 messages ..."));
    }

    @Test
    public void testContentMethods() {
        String logMessage = callTesteeAndGetRequestProgressTrackerMessage(logMessages);
        assertEquals(
                String.format(
                        "ERROR: Servlet %s tried to override the 'Content-Type' header from 'null' to 'text/plain'. This is a violation of the RequestDispatcher.include() contract - https://jakarta.ee/specifications/servlet/4.0/apidocs/javax/servlet/requestdispatcher#include-javax.servlet.ServletRequest-javax.servlet.ServletResponse-. , Include stack: /libs/slingshot/Component/head.html.jsp#1 -> /libs/slingshot/Home/html.jsp#0. All RequestProgressTracker messages: %s",
                        ACTIVE_SERVLET_NAME,
                        Arrays.asList(logMessages).stream().collect(Collectors.joining(System.lineSeparator()))),
                logMessage);
    }

    @Test
    public void testContentMethodsOnForward() {
        final SlingJakartaHttpServletResponse orig = Mockito.mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.FORWARD);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        when(requestData.getActiveServletName()).thenReturn(ACTIVE_SERVLET_NAME);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        include.setContentLength(54);
        include.setContentLengthLong(33L);
        include.setContentType("text/plain");
        include.setLocale(null);
        include.setBufferSize(4500);

        Mockito.verify(orig, times(1)).setContentLength(54);
        Mockito.verify(orig, times(1)).setContentLengthLong(33L);
        Mockito.verify(orig, times(1)).setContentType("text/plain");
        Mockito.verify(orig, times(1)).setLocale(null);
        Mockito.verify(orig, times(1)).setBufferSize(4500);

        Mockito.verifyNoInteractions(requestProgressTracker);
    }

    @Test
    public void testContentTypeOverrideEnabled() {
        final SlingJakartaHttpServletResponse orig = Mockito.mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(orig.getContentType()).thenReturn("text/html");
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        ArrayList<String> logMessagesList = new ArrayList<>(Arrays.asList(logMessages));
        when(requestProgressTracker.getMessages()).thenAnswer(invocation -> logMessagesList.iterator());
        info.setCheckContentTypeOnInclude(true);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);
        when(requestData.getActiveServletName()).thenReturn(ACTIVE_SERVLET_NAME);

        final SlingRequestProcessorImpl requestProcessor = mock(SlingRequestProcessorImpl.class);
        when(requestData.getSlingRequestProcessor()).thenReturn(requestProcessor);

        Throwable throwable = null;
        try {
            include.setContentType("application/json");
        } catch (RuntimeException e) {
            throwable = e;
        }
        Mockito.verify(orig, never()).setContentType("application/json");
        ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestProgressTracker, times(1)).log(logCaptor.capture());
        String logMessage = logCaptor.getValue();
        assertTrue(logMessage.startsWith(String.format(
                "ERROR: Servlet %s tried to override the 'Content-Type' header from 'text/html'"
                        + " to 'application/json', however the org.apache.sling.engine.impl.SlingMainServlet"
                        + " forbids this via the sling.includes.checkcontenttype configuration property."
                        + " This is a violation of the RequestDispatcher.include() contract -"
                        + " https://jakarta.ee/specifications/servlet/4.0/apidocs/javax/servlet/requestdispatcher#include-javax.servlet.ServletRequest-javax.servlet.ServletResponse-. , Include stack: /libs/slingshot/Component/head.html.jsp#1 -> /libs/slingshot/Home/html.jsp#0. All RequestProgressTracker messages: %s",
                ACTIVE_SERVLET_NAME,
                Arrays.asList(logMessages).stream().collect(Collectors.joining(System.lineSeparator())))));
        assertNotNull("Expected a RuntimeException.", throwable);
        assertTrue(throwable
                .getMessage()
                .startsWith(String.format(
                        "Servlet %s tried to override the 'Content-Type' header from 'text/html' to"
                                + " 'application/json', however the org.apache.sling.engine.impl.SlingMainServlet forbids this"
                                + " via the sling.includes.checkcontenttype configuration property."
                                + " This is a violation of the RequestDispatcher.include() contract -"
                                + " https://jakarta.ee/specifications/servlet/4.0/apidocs/javax/servlet/requestdispatcher#include-javax.servlet.ServletRequest-javax.servlet.ServletResponse-. , Include stack: /libs/slingshot/Component/head.html.jsp#1 -> /libs/slingshot/Home/html.jsp#0. All RequestProgressTracker messages: %s",
                        ACTIVE_SERVLET_NAME,
                        Arrays.asList(logMessages).stream().collect(Collectors.joining(System.lineSeparator())))));
    }

    @Test
    public void testContentTypeOverrideDisabled() {
        final SlingJakartaHttpServletResponse orig = Mockito.mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(orig.getContentType()).thenReturn("text/html");
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        ArrayList<String> logMessagesList = new ArrayList<>(Arrays.asList(logMessages));
        when(requestProgressTracker.getMessages()).thenAnswer(invocation -> logMessagesList.iterator());

        final SlingRequestProcessorImpl requestProcessor = mock(SlingRequestProcessorImpl.class);
        when(requestData.getSlingRequestProcessor()).thenReturn(requestProcessor);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);
        when(requestData.getActiveServletName()).thenReturn(ACTIVE_SERVLET_NAME);
        include.setContentType("application/json");
        Mockito.verify(orig, times(1)).setContentType("application/json");

        ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestProgressTracker, times(1)).log(logCaptor.capture());
        String logMessage = logCaptor.getValue();
        assertEquals(
                String.format(
                        "WARN: Servlet %s tried to override the 'Content-Type' header from 'text/html'"
                                + " to 'application/json'. This is a violation of the RequestDispatcher.include()"
                                + " contract -"
                                + " https://jakarta.ee/specifications/servlet/4.0/apidocs/javax/servlet/requestdispatcher#include-javax.servlet.ServletRequest-javax.servlet.ServletResponse-. , Include stack: /libs/slingshot/Component/head.html.jsp#1 -> /libs/slingshot/Home/html.jsp#0. All RequestProgressTracker messages: %s",
                        ACTIVE_SERVLET_NAME,
                        Arrays.asList(logMessages).stream().collect(Collectors.joining(System.lineSeparator()))),
                logMessage);
    }

    @Test
    public void testNoOverrideProtectHeadersContentTypeOverride() {
        final SlingJakartaHttpServletResponse orig = Mockito.mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        info.setProtectHeadersOnInclude(true);
        info.setCheckContentTypeOnInclude(true);
        when(orig.getContentType()).thenReturn("application/json");
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);

        final SlingRequestProcessorImpl requestProcessor = mock(SlingRequestProcessorImpl.class);
        when(requestData.getSlingRequestProcessor()).thenReturn(requestProcessor);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);
        when(requestData.getActiveServletName()).thenReturn(ACTIVE_SERVLET_NAME);
        include.setContentType("application/json");
        Mockito.verify(orig, times(1)).setContentType("application/json");
        Mockito.verifyNoInteractions(requestProgressTracker);
    }

    @Test
    public void testCookies() {
        final SlingJakartaHttpServletResponse orig = Mockito.mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        info.setProtectHeadersOnInclude(true);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        include.addCookie(new Cookie("foo", "bar"));

        Mockito.verifyNoInteractions(orig);
    }

    @Test
    public void testSendError() throws IOException {
        final SlingJakartaHttpServletResponse orig = Mockito.mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        info.setProtectHeadersOnInclude(true);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        include.sendError(500);
        include.sendError(500, "Error");

        Mockito.verifyNoInteractions(orig);
    }

    @Test
    public void testSetStatus() {
        final SlingJakartaHttpServletResponse orig = Mockito.mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        info.setProtectHeadersOnInclude(true);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        include.setStatus(500);

        Mockito.verifyNoInteractions(orig);
    }

    @Test
    public void testHeaders() {
        final SlingJakartaHttpServletResponse orig = Mockito.mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        info.setProtectHeadersOnInclude(true);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        include.setDateHeader("foo-d", 2000L);
        include.addDateHeader("bar-d", 3000L);
        include.setIntHeader("foo-i", 1);
        include.addIntHeader("bar-i", 2);
        include.setHeader("foo", "value");
        include.addHeader("bar", "another");

        Mockito.verifyNoInteractions(orig);
    }
}
