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

import javax.servlet.DispatcherType;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.engine.impl.request.DispatchingInfo;
import org.apache.sling.engine.impl.request.RequestData;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
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
    public void testRecursiveCalls() {
        String[] recursivePartStrings = {
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
            "4673 LOG Applying Includefilters"
        };

// build a string array which resembles the log of recursive includes (50 levels deep)
        String[] concatenatedArray = Stream.concat(Arrays.stream(logMessages), Arrays.stream(recursivePartStrings))
                .toArray(String[]::new);
        for (int i = 0; i < 50; i++) {
            concatenatedArray = Stream.concat(Arrays.stream(concatenatedArray), Arrays.stream(recursivePartStrings))
                    .toArray(String[]::new);
        }

        final SlingHttpServletResponse orig = Mockito.mock(SlingHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        when(requestData.getActiveServletName()).thenReturn(ACTIVE_SERVLET_NAME);

        ArrayList<String> logMessagesList = new ArrayList<>(Arrays.asList(concatenatedArray));
        when(requestProgressTracker.getMessages()).thenAnswer(invocation -> logMessagesList.iterator());
        info.setProtectHeadersOnInclude(true);

        final HttpServletResponse include = new SlingHttpServletResponseImpl(requestData, orig);

        include.setContentLength(54);
        include.setContentLengthLong(33L);
        include.setContentType("text/plain");
        include.setLocale(null);
        include.setBufferSize(4500);

        Mockito.verify(orig, never()).setContentLength(54);
        Mockito.verify(orig, never()).setContentLengthLong(33L);
        Mockito.verify(orig, never()).setContentType("text/plain");
        Mockito.verify(orig, never()).setLocale(null);
        Mockito.verify(orig, never()).setBufferSize(4500);

        ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestProgressTracker, times(1)).log(logCaptor.capture());
        String logMessage = logCaptor.getValue();

        // validate that the log message is cut off and only the last MAX_NR_OF_MESSAGES
        // remain in the log message, check for the cut message
        assertTrue(logMessage.contains("... cut"));
    }

    @Test
    public void testReset() {
        final SlingHttpServletResponse orig = mock(SlingHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        info.setProtectHeadersOnInclude(true);

        final HttpServletResponse include = new SlingHttpServletResponseImpl(requestData, orig);

        when(orig.isCommitted()).thenReturn(false);
        include.reset();
        verify(orig, times(1)).isCommitted();
        Mockito.verifyNoMoreInteractions(orig);

        when(orig.isCommitted()).thenReturn(true);
        include.reset();
        verify(orig, times(2)).isCommitted();
        verify(orig, times(1)).reset();
        Mockito.verifyNoMoreInteractions(orig);
    }

    @Test
    public void testContentMethods() {
        final SlingHttpServletResponse orig = Mockito.mock(SlingHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        when(requestData.getActiveServletName()).thenReturn(ACTIVE_SERVLET_NAME);

        ArrayList<String> logMessagesList = new ArrayList<>(Arrays.asList(logMessages));
        when(requestProgressTracker.getMessages()).thenAnswer(invocation -> logMessagesList.iterator());
        info.setProtectHeadersOnInclude(true);

        final HttpServletResponse include = new SlingHttpServletResponseImpl(requestData, orig);

        include.setContentLength(54);
        include.setContentLengthLong(33L);
        include.setContentType("text/plain");
        include.setLocale(null);
        include.setBufferSize(4500);

        Mockito.verify(orig, never()).setContentLength(54);
        Mockito.verify(orig, never()).setContentLengthLong(33L);
        Mockito.verify(orig, never()).setContentType("text/plain");
        Mockito.verify(orig, never()).setLocale(null);
        Mockito.verify(orig, never()).setBufferSize(4500);

        ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestProgressTracker, times(1)).log(logCaptor.capture());
        String logMessage = logCaptor.getValue();
        assertEquals(
                String.format(
                        "ERROR: Servlet %s tried to override the 'Content-Type' header from 'null' to 'text/plain'. This is a violation of the RequestDispatcher.include() contract - https://jakarta.ee/specifications/servlet/4.0/apidocs/javax/servlet/requestdispatcher#include-javax.servlet.ServletRequest-javax.servlet.ServletResponse-. , Include stack: /libs/slingshot/Component/head.html.jsp#1 -> /libs/slingshot/Home/html.jsp#0. All RequestProgressTracker messages: %s",
                        ACTIVE_SERVLET_NAME,
                        Arrays.asList(logMessages).stream().collect(Collectors.joining(System.lineSeparator()))),
                logMessage);
    }

    @Test
    public void testContentMethodsOnForward() {
        final SlingHttpServletResponse orig = Mockito.mock(SlingHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.FORWARD);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        when(requestData.getActiveServletName()).thenReturn(ACTIVE_SERVLET_NAME);

        final HttpServletResponse include = new SlingHttpServletResponseImpl(requestData, orig);

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
        final SlingHttpServletResponse orig = Mockito.mock(SlingHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(orig.getContentType()).thenReturn("text/html");
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        ArrayList<String> logMessagesList = new ArrayList<>(Arrays.asList(logMessages));
        when(requestProgressTracker.getMessages()).thenAnswer(invocation -> logMessagesList.iterator());
        info.setCheckContentTypeOnInclude(true);

        final HttpServletResponse include = new SlingHttpServletResponseImpl(requestData, orig);
        when(requestData.getActiveServletName()).thenReturn(ACTIVE_SERVLET_NAME);

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
        final SlingHttpServletResponse orig = Mockito.mock(SlingHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        when(orig.getContentType()).thenReturn("text/html");
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);
        ArrayList<String> logMessagesList = new ArrayList<>(Arrays.asList(logMessages));
        when(requestProgressTracker.getMessages()).thenAnswer(invocation -> logMessagesList.iterator());

        final HttpServletResponse include = new SlingHttpServletResponseImpl(requestData, orig);
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
        final SlingHttpServletResponse orig = Mockito.mock(SlingHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        final RequestProgressTracker requestProgressTracker = mock(RequestProgressTracker.class);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        info.setProtectHeadersOnInclude(true);
        info.setCheckContentTypeOnInclude(true);
        when(orig.getContentType()).thenReturn("application/json");
        when(requestData.getRequestProgressTracker()).thenReturn(requestProgressTracker);

        final HttpServletResponse include = new SlingHttpServletResponseImpl(requestData, orig);
        when(requestData.getActiveServletName()).thenReturn(ACTIVE_SERVLET_NAME);
        include.setContentType("application/json");
        Mockito.verify(orig, times(1)).setContentType("application/json");
        Mockito.verifyNoInteractions(requestProgressTracker);
    }

    @Test
    public void testCookies() {
        final SlingHttpServletResponse orig = Mockito.mock(SlingHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        info.setProtectHeadersOnInclude(true);

        final HttpServletResponse include = new SlingHttpServletResponseImpl(requestData, orig);

        include.addCookie(new Cookie("foo", "bar"));

        Mockito.verifyNoInteractions(orig);
    }

    @Test
    public void testSendError() throws IOException {
        final SlingHttpServletResponse orig = Mockito.mock(SlingHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        info.setProtectHeadersOnInclude(true);

        final HttpServletResponse include = new SlingHttpServletResponseImpl(requestData, orig);

        include.sendError(500);
        include.sendError(500, "Error");

        Mockito.verifyNoInteractions(orig);
    }

    @Deprecated
    @Test
    public void testSetStatus() {
        final SlingHttpServletResponse orig = Mockito.mock(SlingHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        info.setProtectHeadersOnInclude(true);

        final HttpServletResponse include = new SlingHttpServletResponseImpl(requestData, orig);

        include.setStatus(500);
        include.setStatus(500, "Error");

        Mockito.verifyNoInteractions(orig);
    }

    @Test
    public void testHeaders() {
        final SlingHttpServletResponse orig = Mockito.mock(SlingHttpServletResponse.class);
        final RequestData requestData = mock(RequestData.class);
        final DispatchingInfo info = new DispatchingInfo(DispatcherType.INCLUDE);
        when(requestData.getDispatchingInfo()).thenReturn(info);
        info.setProtectHeadersOnInclude(true);

        final HttpServletResponse include = new SlingHttpServletResponseImpl(requestData, orig);

        include.setDateHeader("foo-d", 2000L);
        include.addDateHeader("bar-d", 3000L);
        include.setIntHeader("foo-i", 1);
        include.addIntHeader("bar-i", 2);
        include.setHeader("foo", "value");
        include.addHeader("bar", "another");

        Mockito.verifyNoInteractions(orig);
    }
}
