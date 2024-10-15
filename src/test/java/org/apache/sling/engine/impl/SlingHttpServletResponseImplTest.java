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

import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.engine.impl.request.DispatchingInfo;
import org.apache.sling.engine.impl.request.RequestData;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SlingHttpServletResponseImplTest {

    private static final String ACTIVE_SERVLET_NAME = "activeServlet";

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
        when(requestData.getDispatchingInfo()).thenReturn(info);
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
        info.setCheckContentTypeOnInclude(true);

        final HttpServletResponse include = new SlingHttpServletResponseImpl(requestData, orig);
        when(requestData.getActiveServletName()).thenReturn("IncludedServlet");

        Throwable throwable = null;
        try {
            include.setContentType("application/json");
        } catch (RuntimeException e) {
            throwable = e;
        }
        verify(requestProgressTracker, times(1))
                .log(
                        "ERROR: Servlet IncludedServlet tried to override the 'Content-Type' header from 'text/html'"
                                + " to 'application/json', however the org.apache.sling.engine.impl.SlingMainServlet"
                                + " forbids this via the sling.includes.checkcontenttype configuration property."
                                + " This is a violation of the RequestDispatcher.include() contract -"
                                + " https://javadoc.io/static/javax.servlet/javax.servlet-api/4.0.1/javax/servlet/RequestDispatcher.html#include-javax.servlet.ServletRequest-javax.servlet.ServletResponse-.");
        assertNotNull("Expected a RuntimeException.", throwable);
        assertEquals(
                "Servlet"
                        + " IncludedServlet tried to override the 'Content-Type' header from 'text/html' to"
                        + " 'application/json', however the org.apache.sling.engine.impl.SlingMainServlet forbids this"
                        + " via the sling.includes.checkcontenttype configuration property."
                        + " This is a violation of the RequestDispatcher.include() contract -"
                        + " https://javadoc.io/static/javax.servlet/javax.servlet-api/4.0.1/javax/servlet/RequestDispatcher.html#include-javax.servlet.ServletRequest-javax.servlet.ServletResponse-.",
                throwable.getMessage());
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

        final HttpServletResponse include = new SlingHttpServletResponseImpl(requestData, orig);
        when(requestData.getActiveServletName()).thenReturn("IncludedServlet");
        include.setContentType("application/json");

        verify(requestProgressTracker, times(1))
                .log(
                        "WARN: Servlet IncludedServlet tried to override the 'Content-Type' header from 'text/html' "
                                + "to 'application/json'. This is a violation of the RequestDispatcher.include() "
                                + "contract -"
                                + " https://javadoc.io/static/javax.servlet/javax.servlet-api/4.0.1/javax/servlet/RequestDispatcher.html#include-javax.servlet.ServletRequest-javax.servlet.ServletResponse-.");
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
