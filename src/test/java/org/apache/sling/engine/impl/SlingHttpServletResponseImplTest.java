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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
        "3757 LOG Calling filter: org.apache.sling.i18n.impl.I18NFilter",
        "4722 TIMER_START{/libs/slingshot/Component/head.html.jsp#1}",
        "4859 TIMER_END{135,/libs/slingshot/Component/head.html.jsp#1}",
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

    // the messages above minus the "LOG Calling filter:" statements
    String[] expectedMessagesLogged = {
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
        "4722 TIMER_START{/libs/slingshot/Component/head.html.jsp#1}",
        "4859 TIMER_END{135,/libs/slingshot/Component/head.html.jsp#1}",
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
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        when(orig.isCommitted()).thenReturn(true);

        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(requestData.getRequestProgressTracker()).thenReturn(mock(RequestProgressTracker.class));

        final SlingJakartaHttpServletResponseImpl include = new SlingJakartaHttpServletResponseImpl(requestData, orig);
        SlingJakartaHttpServletResponseImpl spyInclude = spy(include);

        spyInclude.sendRedirect("somewhere");

        spyInclude.setContentType("someOtherType");
        verify(orig, times(1)).setContentType(any());
        verify(spyInclude, never()).checkContentTypeOverride(any());
    }

    @Test
    public void testSendRedirectOverloadsProtectedOnInclude() throws IOException {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        info.setProtectHeadersOnInclude(true);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        include.sendRedirect("/target");
        include.sendRedirect("/target", HttpServletResponse.SC_MOVED_PERMANENTLY);
        include.sendRedirect("/target", false);
        include.sendRedirect("/target", HttpServletResponse.SC_MOVED_PERMANENTLY, false);
        include.setTrailerFields(java.util.Collections::emptyMap);

        verifyNoInteractions(orig);
    }

    @Test
    public void testSendRedirectOverloadsDelegateWhenNotProtected() throws IOException {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(requestData.getRequestProgressTracker()).thenReturn(mock(RequestProgressTracker.class));

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        include.sendRedirect("/target", HttpServletResponse.SC_MOVED_PERMANENTLY);
        include.sendRedirect("/target", true);
        include.sendRedirect("/target", HttpServletResponse.SC_MOVED_PERMANENTLY, false);

        verify(orig, times(1)).sendRedirect("/target", HttpServletResponse.SC_MOVED_PERMANENTLY);
        verify(orig, times(1)).sendRedirect("/target", true);
        verify(orig, times(1)).sendRedirect("/target", HttpServletResponse.SC_MOVED_PERMANENTLY, false);
    }

    @Test
    public void testNoViolationChecksOnCommittedResponseWhenSendError() throws IOException {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);

        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(requestData.getSlingRequestProcessor()).thenReturn(mock(SlingRequestProcessorImpl.class));
        when(requestData.getRequestProgressTracker()).thenReturn(mock(RequestProgressTracker.class));

        final SlingJakartaHttpServletResponseImpl include = new SlingJakartaHttpServletResponseImpl(requestData, orig);
        SlingJakartaHttpServletResponseImpl spyInclude = spy(include);

        spyInclude.sendError(501);
        // send error will eventually commit the response, let's mock this
        when(orig.isCommitted()).thenReturn(true);

        spyInclude.setContentType("someOtherType");
        verify(orig, times(1)).setContentType(any());
        verify(spyInclude, never()).checkContentTypeOverride(any());
    }

    @Test
    public void testViolationChecksOnCommittedResponses() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        when(orig.isCommitted()).thenReturn(true);

        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(requestData.getSlingRequestProcessor()).thenReturn(mock(SlingRequestProcessorImpl.class));
        final RequestProgressTracker rpt = mock(RequestProgressTracker.class);
        when(rpt.getMessages()).thenReturn(new ArrayList<String>().iterator());
        when(requestData.getRequestProgressTracker()).thenReturn(rpt);

        final SlingJakartaHttpServletResponseImpl include = new SlingJakartaHttpServletResponseImpl(requestData, orig);
        SlingJakartaHttpServletResponseImpl spyInclude = spy(include);

        spyInclude.setContentType("someOtherType");
        verify(orig, times(1)).setContentType(any());
        verify(spyInclude, times(1)).checkContentTypeOverride(any());
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
        verifyNoMoreInteractions(originalResponse);

        when(originalResponse.isCommitted()).thenReturn(true);
        includeResponse.reset();
        verify(originalResponse, times(2)).isCommitted();
        verify(originalResponse, times(1)).reset();
        verifyNoMoreInteractions(originalResponse);
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

        verifyNoMoreInteractions(originalResponse);
    }

    private String callTesteeAndGetRequestProgressTrackerMessage(String[] logMessages) {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
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

        verify(orig, never()).setContentLength(54);
        verify(orig, never()).setContentLengthLong(33L);
        verify(orig, never()).setContentType("text/plain");
        verify(orig, never()).setLocale(null);
        verify(orig, times(1)).setBufferSize(4500);

        verify(requestProcessor, atMostOnce()).setContentTypeHeaderState(any());

        ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestProgressTracker, times(1)).log(logCaptor.capture());
        return logCaptor.getValue();
    }

    @Test
    public void testRecursiveCalls() {

        // build a string array which resembles the log of recursive includes (50 levels
        // deep)
        String[] recursivePartStrings = Arrays.copyOfRange(logMessages, 14, logMessages.length - 2);
        String[] concatenatedArray = Stream.concat(
                        Arrays.stream(expectedMessagesLogged), Arrays.stream(recursivePartStrings))
                .toArray(String[]::new);
        for (int i = 0; i < 50; i++) {
            concatenatedArray = Stream.concat(Arrays.stream(concatenatedArray), Arrays.stream(recursivePartStrings))
                    .toArray(String[]::new);
        }

        String logMessage = callTesteeAndGetRequestProgressTrackerMessage(concatenatedArray);

        // validate that the log message is cut off and only the last MAX_NR_OF_MESSAGES
        // remain in the log message, check for the cut message
        assertTrue(logMessage.contains("... cut 399 messages ..."));
    }

    @Test
    public void testContentMethods() {
        String logMessage = callTesteeAndGetRequestProgressTrackerMessage(logMessages);
        assertEquals(
                String.format(
                        "ERROR: Servlet %s tried to override the 'Content-Type' header from 'null' to 'text/plain'. This is a violation of the RequestDispatcher.include() contract - https://jakarta.ee/specifications/servlet/4.0/apidocs/javax/servlet/requestdispatcher#include-javax.servlet.ServletRequest-javax.servlet.ServletResponse-. , Include stack: /libs/slingshot/Component/head.html.jsp#1 -> /libs/slingshot/Home/html.jsp#0. All RequestProgressTracker messages: %s",
                        ACTIVE_SERVLET_NAME,
                        Arrays.asList(expectedMessagesLogged).stream()
                                .collect(Collectors.joining(System.lineSeparator()))),
                logMessage);
    }

    @Test
    public void testContentMethodsOnForward() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
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

        verify(orig, times(1)).setContentLength(54);
        verify(orig, times(1)).setContentLengthLong(33L);
        verify(orig, times(1)).setContentType("text/plain");
        verify(orig, times(1)).setLocale(null);
        verify(orig, times(1)).setBufferSize(4500);

        verifyNoInteractions(requestProgressTracker);
    }

    @Test
    public void testContentTypeOverrideEnabled() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
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
        verify(orig, never()).setContentType("application/json");
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
                Arrays.asList(expectedMessagesLogged).stream().collect(Collectors.joining(System.lineSeparator())))));
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
                        Arrays.asList(expectedMessagesLogged).stream()
                                .collect(Collectors.joining(System.lineSeparator())))));
    }

    @Test
    public void testContentTypeOverrideEnforcedForSetHeader() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(orig.getContentType()).thenReturn("text/plain");
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        ArrayList<String> logMessagesList = new ArrayList<>(Arrays.asList(logMessages));
        when(requestProgressTracker.getMessages()).thenAnswer(invocation -> logMessagesList.iterator());
        info.setCheckContentTypeOnInclude(true);

        final SlingRequestProcessorImpl requestProcessor = mock(SlingRequestProcessorImpl.class);
        when(requestData.getSlingRequestProcessor()).thenReturn(requestProcessor);
        when(requestData.getActiveServletName()).thenReturn(ACTIVE_SERVLET_NAME);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        Throwable setHeaderThrowable = null;
        try {
            include.setHeader("Content-Type", "text/html");
        } catch (RuntimeException e) {
            setHeaderThrowable = e;
        }
        assertNotNull("Expected setHeader(\"Content-Type\", ...) to be blocked.", setHeaderThrowable);

        Throwable addHeaderThrowable = null;
        try {
            include.addHeader("content-type", "text/html");
        } catch (RuntimeException e) {
            addHeaderThrowable = e;
        }
        assertNotNull("Expected addHeader(\"content-type\", ...) to be blocked.", addHeaderThrowable);

        verify(orig, never()).setHeader(anyString(), anyString());
        verify(orig, never()).addHeader(anyString(), anyString());
        verify(orig, never()).setContentType(anyString());
    }

    @Test
    public void testUnrelatedHeadersNotRoutedThroughContentTypeCheck() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(requestData.getRequestProgressTracker()).thenReturn(mock(RequestProgressTracker.class));
        info.setCheckContentTypeOnInclude(true);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        include.setHeader("X-Custom", "value");
        include.addHeader("X-Custom", "another");

        verify(orig, times(1)).setHeader("X-Custom", "value");
        verify(orig, times(1)).addHeader("X-Custom", "another");
    }

    @Test
    public void testContentTypeOverrideDisabled() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
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
        verify(orig, times(1)).setContentType("application/json");

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
                        Arrays.asList(expectedMessagesLogged).stream()
                                .collect(Collectors.joining(System.lineSeparator()))),
                logMessage);
    }

    @Test
    public void testContentTypeOverrideStillEnforcedAfterPreviousViolation() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(orig.getContentType()).thenReturn("text/plain");
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        info.setCheckContentTypeOnInclude(true);

        final SlingRequestProcessorImpl requestProcessor = mock(SlingRequestProcessorImpl.class);
        // a violation has already been detected earlier within this request
        when(requestProcessor.getContentTypeHeaderState()).thenReturn(ContentTypeHeaderState.VIOLATED);
        when(requestData.getSlingRequestProcessor()).thenReturn(requestProcessor);
        when(requestData.getActiveServletName()).thenReturn(ACTIVE_SERVLET_NAME);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        Throwable throwable = null;
        try {
            include.setContentType("text/html");
        } catch (RuntimeException e) {
            throwable = e;
        }
        assertNotNull("Expected the repeated override attempt to still be blocked.", throwable);
        verify(orig, never()).setContentType("text/html");
    }

    @Test
    public void testContentTypeOverrideStillIgnoredAfterPreviousViolationWithProtectHeaders() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(orig.getContentType()).thenReturn("text/plain");
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        info.setProtectHeadersOnInclude(true);

        final SlingRequestProcessorImpl requestProcessor = mock(SlingRequestProcessorImpl.class);
        // a violation has already been detected earlier within this request
        when(requestProcessor.getContentTypeHeaderState()).thenReturn(ContentTypeHeaderState.VIOLATED);
        when(requestData.getSlingRequestProcessor()).thenReturn(requestProcessor);
        when(requestData.getActiveServletName()).thenReturn(ACTIVE_SERVLET_NAME);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        include.setContentType("text/html");

        verify(orig, never()).setContentType("text/html");
    }

    @Test
    public void testNoOverrideProtectHeadersContentTypeOverride() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
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
        verify(orig, times(1)).setContentType("application/json");
        verifyNoInteractions(requestProgressTracker);
    }

    @Test
    public void testCharacterEncodingProtectedOnInclude() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        when(requestData.getActiveServletName()).thenReturn(ACTIVE_SERVLET_NAME);
        when(orig.getContentType()).thenReturn("text/html;charset=UTF-8");
        when(orig.getCharacterEncoding()).thenReturn("UTF-8");
        ArrayList<String> logMessagesList = new ArrayList<>(Arrays.asList(logMessages));
        when(requestProgressTracker.getMessages()).thenAnswer(invocation -> logMessagesList.iterator());
        info.setProtectHeadersOnInclude(true);

        final SlingRequestProcessorImpl requestProcessor = mock(SlingRequestProcessorImpl.class);
        when(requestData.getSlingRequestProcessor()).thenReturn(requestProcessor);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        include.setCharacterEncoding("ISO-2022-JP");

        verify(orig, never()).setCharacterEncoding(anyString());
        ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestProgressTracker, times(1)).log(logCaptor.capture());
        assertTrue(logCaptor.getValue().startsWith("ERROR: "));
    }

    @Test
    public void testCharacterEncodingCharsetVariantProtectedOnInclude() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        when(requestData.getActiveServletName()).thenReturn(ACTIVE_SERVLET_NAME);
        when(orig.getCharacterEncoding()).thenReturn("UTF-8");
        ArrayList<String> logMessagesList = new ArrayList<>(Arrays.asList(logMessages));
        when(requestProgressTracker.getMessages()).thenAnswer(invocation -> logMessagesList.iterator());
        info.setProtectHeadersOnInclude(true);

        final SlingRequestProcessorImpl requestProcessor = mock(SlingRequestProcessorImpl.class);
        when(requestData.getSlingRequestProcessor()).thenReturn(requestProcessor);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        include.setCharacterEncoding(StandardCharsets.UTF_16);

        verify(orig, never()).setCharacterEncoding(anyString());
        verify(orig, never()).setCharacterEncoding(any(Charset.class));
    }

    @Test
    public void testCharacterEncodingUnchangedNotFlaggedOnInclude() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        when(orig.getCharacterEncoding()).thenReturn("UTF-8");
        info.setProtectHeadersOnInclude(true);

        final SlingRequestProcessorImpl requestProcessor = mock(SlingRequestProcessorImpl.class);
        when(requestData.getSlingRequestProcessor()).thenReturn(requestProcessor);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        include.setCharacterEncoding("utf-8");

        verify(orig, times(1)).setCharacterEncoding("utf-8");
        verifyNoInteractions(requestProgressTracker);
    }

    @Test
    public void testCharacterEncodingDelegatedOutsideInclude() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.FORWARD);
        when(requestData.getDispatchingInfo()).thenReturn(info);

        final HttpServletResponse response = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        response.setCharacterEncoding("ISO-2022-JP");

        verify(orig, times(1)).setCharacterEncoding("ISO-2022-JP");
    }

    @Test
    public void testContentTypeCharsetChangeDetectedOnInclude() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(orig.getContentType()).thenReturn("text/html;charset=UTF-8");
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        when(requestData.getActiveServletName()).thenReturn(ACTIVE_SERVLET_NAME);
        ArrayList<String> logMessagesList = new ArrayList<>(Arrays.asList(logMessages));
        when(requestProgressTracker.getMessages()).thenAnswer(invocation -> logMessagesList.iterator());
        info.setCheckContentTypeOnInclude(true);

        final SlingRequestProcessorImpl requestProcessor = mock(SlingRequestProcessorImpl.class);
        when(requestData.getSlingRequestProcessor()).thenReturn(requestProcessor);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        Throwable throwable = null;
        try {
            include.setContentType("text/html;charset=UTF-7");
        } catch (RuntimeException e) {
            throwable = e;
        }
        assertNotNull("Expected a RuntimeException for the charset change.", throwable);
        verify(orig, never()).setContentType(anyString());
    }

    @Test
    public void testContentTypeSameMimeAndCharsetCaseInsensitiveNotFlagged() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(orig.getContentType()).thenReturn("text/html; charset=UTF-8");
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        info.setProtectHeadersOnInclude(true);
        info.setCheckContentTypeOnInclude(true);

        final SlingRequestProcessorImpl requestProcessor = mock(SlingRequestProcessorImpl.class);
        when(requestData.getSlingRequestProcessor()).thenReturn(requestProcessor);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        include.setContentType("TEXT/HTML;charset=utf-8");

        verify(orig, times(1)).setContentType("TEXT/HTML;charset=utf-8");
        verifyNoInteractions(requestProgressTracker);
    }

    @Test
    public void testCharacterEncodingCheckContentTypeOnIncludeThrows() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        when(requestData.getActiveServletName()).thenReturn(ACTIVE_SERVLET_NAME);
        when(orig.getContentType()).thenReturn("text/html;charset=UTF-8");
        when(orig.getCharacterEncoding()).thenReturn("UTF-8");
        ArrayList<String> logMessagesList = new ArrayList<>(Arrays.asList(logMessages));
        when(requestProgressTracker.getMessages()).thenAnswer(invocation -> logMessagesList.iterator());
        info.setCheckContentTypeOnInclude(true);

        final SlingRequestProcessorImpl requestProcessor = mock(SlingRequestProcessorImpl.class);
        when(requestData.getSlingRequestProcessor()).thenReturn(requestProcessor);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        Throwable throwable = null;
        try {
            include.setCharacterEncoding("ISO-2022-JP");
        } catch (RuntimeException e) {
            throwable = e;
        }
        assertNotNull("Expected a RuntimeException for the character encoding change.", throwable);
        verify(orig, never()).setCharacterEncoding(anyString());
        verify(requestProgressTracker, times(1)).log(startsWith("ERROR: "));
    }

    @Test
    public void testCharacterEncodingStillEnforcedAfterPreviousViolation() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(orig.getContentType()).thenReturn("text/html;charset=UTF-8");
        when(orig.getCharacterEncoding()).thenReturn("UTF-8");
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        when(requestData.getActiveServletName()).thenReturn(ACTIVE_SERVLET_NAME);
        info.setCheckContentTypeOnInclude(true);

        final SlingRequestProcessorImpl requestProcessor = mock(SlingRequestProcessorImpl.class);
        // a violation has already been detected earlier within this request
        when(requestProcessor.getContentTypeHeaderState()).thenReturn(ContentTypeHeaderState.VIOLATED);
        when(requestData.getSlingRequestProcessor()).thenReturn(requestProcessor);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        Throwable throwable = null;
        try {
            include.setCharacterEncoding("ISO-2022-JP");
        } catch (RuntimeException e) {
            throwable = e;
        }
        assertNotNull("Expected the repeated override attempt to still be blocked.", throwable);
        verify(orig, never()).setCharacterEncoding(anyString());
        // the short message must not require the RequestProgressTracker messages again
        verify(requestProgressTracker, never()).getMessages();
    }

    @Test
    public void testCharacterEncodingNoViolationChecksOnCommittedResponseWhenSendRedirect() throws IOException {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        when(orig.isCommitted()).thenReturn(true);

        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(requestData.getRequestProgressTracker()).thenReturn(mock(RequestProgressTracker.class));

        final SlingJakartaHttpServletResponseImpl include = new SlingJakartaHttpServletResponseImpl(requestData, orig);
        SlingJakartaHttpServletResponseImpl spyInclude = spy(include);

        spyInclude.sendRedirect("somewhere");

        spyInclude.setCharacterEncoding("ISO-2022-JP");
        verify(orig, times(1)).setCharacterEncoding("ISO-2022-JP");
        verify(spyInclude, never()).checkCharacterEncodingOverride(any());
    }

    @Test
    public void testCharacterEncodingNoViolationChecksOnCommittedResponseWhenSendError() throws IOException {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);

        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(requestData.getSlingRequestProcessor()).thenReturn(mock(SlingRequestProcessorImpl.class));
        when(requestData.getRequestProgressTracker()).thenReturn(mock(RequestProgressTracker.class));

        final SlingJakartaHttpServletResponseImpl include = new SlingJakartaHttpServletResponseImpl(requestData, orig);
        SlingJakartaHttpServletResponseImpl spyInclude = spy(include);

        spyInclude.sendError(501);
        // send error will eventually commit the response, let's mock this
        when(orig.isCommitted()).thenReturn(true);

        spyInclude.setCharacterEncoding("ISO-2022-JP");
        verify(orig, times(1)).setCharacterEncoding("ISO-2022-JP");
        verify(spyInclude, never()).checkCharacterEncodingOverride(any());
    }

    @Test
    public void testCharacterEncodingNullCharsetFallsBackToDelegate() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        when(requestData.getActiveServletName()).thenReturn(ACTIVE_SERVLET_NAME);
        when(orig.getContentType()).thenReturn("text/html;charset=UTF-8");
        when(orig.getCharacterEncoding()).thenReturn("UTF-8");
        ArrayList<String> logMessagesList = new ArrayList<>(Arrays.asList(logMessages));
        when(requestProgressTracker.getMessages()).thenAnswer(invocation -> logMessagesList.iterator());
        info.setProtectHeadersOnInclude(true);

        final SlingRequestProcessorImpl requestProcessor = mock(SlingRequestProcessorImpl.class);
        when(requestData.getSlingRequestProcessor()).thenReturn(requestProcessor);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        include.setCharacterEncoding((Charset) null);

        verify(orig, never()).setCharacterEncoding(anyString());
        verify(requestProgressTracker, times(1)).log(startsWith("ERROR: "));
    }

    @Test
    public void testContentTypeCharsetFallsBackToCurrentCharacterEncodingWhenNotInContentType() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        // current 'Content-Type' has no charset parameter, but the response
        // already has a character encoding assigned
        when(orig.getContentType()).thenReturn("text/html");
        when(orig.getCharacterEncoding()).thenReturn("UTF-8");
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        when(requestData.getActiveServletName()).thenReturn(ACTIVE_SERVLET_NAME);
        ArrayList<String> logMessagesList = new ArrayList<>(Arrays.asList(logMessages));
        when(requestProgressTracker.getMessages()).thenAnswer(invocation -> logMessagesList.iterator());
        info.setCheckContentTypeOnInclude(true);

        final SlingRequestProcessorImpl requestProcessor = mock(SlingRequestProcessorImpl.class);
        when(requestData.getSlingRequestProcessor()).thenReturn(requestProcessor);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        Throwable throwable = null;
        try {
            include.setContentType("text/html;charset=UTF-7");
        } catch (RuntimeException e) {
            throwable = e;
        }
        assertNotNull(
                "Expected a RuntimeException since the charset differs from the current character encoding.",
                throwable);
        verify(orig, never()).setContentType(anyString());
    }

    @Test
    public void testContentTypeQuotedCharsetParsedCorrectly() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(orig.getContentType()).thenReturn("text/html; charset=\"UTF-8\"");
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        info.setProtectHeadersOnInclude(true);
        info.setCheckContentTypeOnInclude(true);

        final SlingRequestProcessorImpl requestProcessor = mock(SlingRequestProcessorImpl.class);
        when(requestData.getSlingRequestProcessor()).thenReturn(requestProcessor);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        // same mime type and same (quoted vs. unquoted) charset must not be flagged
        include.setContentType("text/html; charset=UTF-8");

        verify(orig, times(1)).setContentType("text/html; charset=UTF-8");
        verifyNoInteractions(requestProgressTracker);
    }

    @Test
    public void testCookies() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        info.setProtectHeadersOnInclude(true);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        include.addCookie(new Cookie("foo", "bar"));

        verifyNoInteractions(orig);
    }

    @Test
    public void testSendError() throws IOException {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        info.setProtectHeadersOnInclude(true);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        include.sendError(500);
        include.sendError(500, "Error");

        verifyNoInteractions(orig);
    }

    @Test
    public void testSetStatus() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        info.setProtectHeadersOnInclude(true);

        final HttpServletResponse include = new SlingJakartaHttpServletResponseImpl(requestData, orig);

        include.setStatus(500);

        verifyNoInteractions(orig);
    }

    @Test
    public void testHeaders() {
        final SlingJakartaHttpServletResponse orig = mock(SlingJakartaHttpServletResponse.class);
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

        verifyNoInteractions(orig);
    }

    @Test
    public void testCharsetsEqualIdenticalNames() {
        assertTrue(SlingJakartaHttpServletResponseImpl.charsetsEqual("UTF-8", "UTF-8"));
    }

    @Test
    public void testCharsetsEqualDifferingOnlyByCase() {
        assertTrue(SlingJakartaHttpServletResponseImpl.charsetsEqual("utf-8", "UTF-8"));
    }

    @Test
    public void testCharsetsEqualAliasNames() {
        // "UTF8" and "UTF-8" differ by more than case, but are the same
        // charset (UTF8 is a registered alias)
        assertTrue(SlingJakartaHttpServletResponseImpl.charsetsEqual("UTF8", "UTF-8"));
        // "Cp1252" and "windows-1252" are aliases for the same charset
        assertTrue(SlingJakartaHttpServletResponseImpl.charsetsEqual("Cp1252", "windows-1252"));
    }

    @Test
    public void testCharsetsEqualDifferentCharsets() {
        assertFalse(SlingJakartaHttpServletResponseImpl.charsetsEqual("UTF-8", "ISO-8859-1"));
    }

    @Test
    public void testCharsetsEqualBothNull() {
        assertTrue(SlingJakartaHttpServletResponseImpl.charsetsEqual(null, null));
    }

    @Test
    public void testCharsetsEqualOneNull() {
        assertFalse(SlingJakartaHttpServletResponseImpl.charsetsEqual(null, "UTF-8"));
        assertFalse(SlingJakartaHttpServletResponseImpl.charsetsEqual("UTF-8", null));
    }

    @Test
    public void testCharsetsEqualIdenticalUnsupportedNamesFallsBackToTextualMatch() {
        // neither name resolves to a known Charset, but they are textually
        // equal (ignoring case), so the fast path short-circuits before any
        // Charset.forName lookup is attempted
        assertTrue(SlingJakartaHttpServletResponseImpl.charsetsEqual(
                "bogus-unknown-charset-xyz", "BOGUS-UNKNOWN-CHARSET-XYZ"));
    }

    @Test
    public void testCharsetsEqualUnsupportedNameFallsBackWithoutThrowing() {
        // "bogus-unknown-charset-xyz" is a syntactically valid charset name
        // that is simply not registered/supported (UnsupportedCharsetException)
        assertFalse(SlingJakartaHttpServletResponseImpl.charsetsEqual("bogus-unknown-charset-xyz", "UTF-8"));
    }

    @Test
    public void testCharsetsEqualIllegalCharsetNameFallsBackWithoutThrowing() {
        // this name is not even syntactically valid (IllegalCharsetNameException)
        assertFalse(SlingJakartaHttpServletResponseImpl.charsetsEqual("not a real charset!!", "UTF-8"));
    }
}
