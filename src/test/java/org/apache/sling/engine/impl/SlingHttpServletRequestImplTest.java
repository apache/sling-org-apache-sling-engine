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

import java.security.Principal;
import java.util.Enumeration;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.engine.impl.request.ContentData;
import org.apache.sling.engine.impl.request.RequestData;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.imposters.ByteBuddyClassImposteriser;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SlingHttpServletRequestImplTest {

    SlingJakartaHttpServletRequestImpl slingHttpServletRequestImpl;

    private Mockery context = new JUnit4Mockery() {
        {
            setImposteriser(ByteBuddyClassImposteriser.INSTANCE);
        }
    };

    @Test
    public void getUserPrincipal_testWithRemoteUserFallback() {
        final HttpServletRequest servletRequest = context.mock(HttpServletRequest.class);

        context.checking(new Expectations() {
            {
                oneOf(servletRequest).getServletPath();
                will(returnValue("/path"));
                allowing(servletRequest).getPathInfo();
                will(returnValue("/path"));
                allowing(servletRequest).getRemoteUser();
                will(returnValue("remoteUser"));
            }
        });

        final RequestData requestData = context.mock(RequestData.class, "requestData");
        final ResourceResolver resourceResolver = context.mock(ResourceResolver.class);

        context.checking(new Expectations() {
            {
                allowing(requestData).getResourceResolver();
                will(returnValue(resourceResolver));
                allowing(resourceResolver).adaptTo(Principal.class);
                will(returnValue(null));
                allowing(requestData).isDisableCheckCompliantGetUserPrincipal();
                will(returnValue(false));
            }
        });

        slingHttpServletRequestImpl = new SlingJakartaHttpServletRequestImpl(requestData, servletRequest);
        assertEquals(
                "UserPrincipal: remoteUser",
                slingHttpServletRequestImpl.getUserPrincipal().toString());
    }

    @Test
    public void getUserPrincipal_testWithRemoteUserFallback_nonspeccompliant() {
        final HttpServletRequest servletRequest = context.mock(HttpServletRequest.class);

        context.checking(new Expectations() {
            {
                oneOf(servletRequest).getServletPath();
                will(returnValue("/path"));
                allowing(servletRequest).getPathInfo();
                will(returnValue("/path"));
                allowing(servletRequest).getRemoteUser();
                will(returnValue("remoteUser"));
            }
        });

        final RequestData requestData = context.mock(RequestData.class, "requestData");
        final ResourceResolver resourceResolver = context.mock(ResourceResolver.class);

        context.checking(new Expectations() {
            {
                allowing(requestData).getResourceResolver();
                will(returnValue(resourceResolver));
                allowing(resourceResolver).adaptTo(Principal.class);
                will(returnValue(null));
                allowing(requestData).isDisableCheckCompliantGetUserPrincipal();
                will(returnValue(true));
                allowing(requestData).logNonCompliantGetUserPrincipalWarning();
            }
        });

        slingHttpServletRequestImpl = new SlingJakartaHttpServletRequestImpl(requestData, servletRequest);
        assertEquals(
                "UserPrincipal: remoteUser",
                slingHttpServletRequestImpl.getUserPrincipal().toString());
    }

    @Test
    public void getUserPrincipal_testUnauthenticated() {
        final HttpServletRequest servletRequest = context.mock(HttpServletRequest.class);

        context.checking(new Expectations() {
            {
                oneOf(servletRequest).getServletPath();
                will(returnValue("/path"));
                allowing(servletRequest).getPathInfo();
                will(returnValue("/path"));
                allowing(servletRequest).getRemoteUser();
                will(returnValue(null));
            }
        });

        final RequestData requestData = context.mock(RequestData.class, "requestData");
        final ResourceResolver resourceResolver = context.mock(ResourceResolver.class);
        final Principal principal = context.mock(Principal.class);

        context.checking(new Expectations() {
            {
                allowing(requestData).getResourceResolver();
                will(returnValue(resourceResolver));
                allowing(resourceResolver).adaptTo(Principal.class);
                will(returnValue(principal));
                allowing(requestData).isDisableCheckCompliantGetUserPrincipal();
                will(returnValue(false));
            }
        });

        slingHttpServletRequestImpl = new SlingJakartaHttpServletRequestImpl(requestData, servletRequest);
        assertNull(slingHttpServletRequestImpl.getUserPrincipal());
    }

    @Test
    public void getUserPrincipal_testUnauthenticated_nonspeccompliant() {
        final HttpServletRequest servletRequest = context.mock(HttpServletRequest.class);

        context.checking(new Expectations() {
            {
                oneOf(servletRequest).getServletPath();
                will(returnValue("/path"));
                allowing(servletRequest).getPathInfo();
                will(returnValue("/path"));
                allowing(servletRequest).getRemoteUser();
                will(returnValue(null));
            }
        });

        final RequestData requestData = context.mock(RequestData.class, "requestData");
        final ResourceResolver resourceResolver = context.mock(ResourceResolver.class);
        final Principal principal = context.mock(Principal.class);

        context.checking(new Expectations() {
            {
                allowing(requestData).getResourceResolver();
                will(returnValue(resourceResolver));
                allowing(resourceResolver).adaptTo(Principal.class);
                will(returnValue(principal));
                allowing(requestData).isDisableCheckCompliantGetUserPrincipal();
                will(returnValue(true));
                allowing(requestData).logNonCompliantGetUserPrincipalWarning();
            }
        });

        slingHttpServletRequestImpl = new SlingJakartaHttpServletRequestImpl(requestData, servletRequest);
        assertSame(principal, slingHttpServletRequestImpl.getUserPrincipal());
    }

    @Test
    public void getUserPrincipal_testWithPrincipal() {
        final HttpServletRequest servletRequest = context.mock(HttpServletRequest.class);

        context.checking(new Expectations() {
            {
                oneOf(servletRequest).getServletPath();
                will(returnValue("/path"));
                allowing(servletRequest).getPathInfo();
                will(returnValue("/path"));
                allowing(servletRequest).getRemoteUser();
                will(returnValue("remoteUser"));
            }
        });

        final RequestData requestData = context.mock(RequestData.class, "requestData");
        final ResourceResolver resourceResolver = context.mock(ResourceResolver.class);
        final Principal principal = context.mock(Principal.class);

        context.checking(new Expectations() {
            {
                allowing(requestData).getResourceResolver();
                will(returnValue(resourceResolver));
                allowing(resourceResolver).adaptTo(Principal.class);
                will(returnValue(principal));
                allowing(requestData).isDisableCheckCompliantGetUserPrincipal();
                will(returnValue(false));
            }
        });

        slingHttpServletRequestImpl = new SlingJakartaHttpServletRequestImpl(requestData, servletRequest);
        assertEquals(principal, slingHttpServletRequestImpl.getUserPrincipal());
    }

    @Test
    public void getUserPrincipal_testWithPrincipal_nonspeccompliant() {
        final HttpServletRequest servletRequest = context.mock(HttpServletRequest.class);

        context.checking(new Expectations() {
            {
                oneOf(servletRequest).getServletPath();
                will(returnValue("/path"));
                allowing(servletRequest).getPathInfo();
                will(returnValue("/path"));
                allowing(servletRequest).getRemoteUser();
                will(returnValue("remoteUser"));
            }
        });

        final RequestData requestData = context.mock(RequestData.class, "requestData");
        final ResourceResolver resourceResolver = context.mock(ResourceResolver.class);
        final Principal principal = context.mock(Principal.class);

        context.checking(new Expectations() {
            {
                allowing(requestData).getResourceResolver();
                will(returnValue(resourceResolver));
                allowing(resourceResolver).adaptTo(Principal.class);
                will(returnValue(principal));
                allowing(requestData).isDisableCheckCompliantGetUserPrincipal();
                will(returnValue(true));
            }
        });

        slingHttpServletRequestImpl = new SlingJakartaHttpServletRequestImpl(requestData, servletRequest);
        assertEquals(principal, slingHttpServletRequestImpl.getUserPrincipal());
    }

    private void assertEmptyEnumerator(final Enumeration<String> e) {
        assertFalse(e.hasMoreElements());
    }

    private void assertSingletonEnumerator(final Enumeration<String> e, final String value) {
        assertTrue(e.hasMoreElements());
        assertEquals(value, e.nextElement());
        assertFalse(e.hasMoreElements());
    }

    @Test
    public void testGetResponseContentType() {
        final HttpServletRequest baseRequest = Mockito.mock(HttpServletRequest.class);
        Mockito.when(baseRequest.getServletPath()).thenReturn("/");

        final RequestPathInfo rpi = Mockito.mock(RequestPathInfo.class);
        final ContentData cd = new ContentData(null, rpi);
        final RequestData rd = Mockito.mock(RequestData.class);
        final SlingRequestProcessorImpl processor = Mockito.mock(SlingRequestProcessorImpl.class);
        Mockito.when(rd.getSlingRequestProcessor()).thenReturn(processor);
        Mockito.when(rd.getContentData()).thenReturn(cd);

        // first tests - processor returning null
        Mockito.when(processor.getMimeType(Mockito.anyString())).thenReturn(null);

        Mockito.when(rpi.getExtension()).thenReturn(null);
        SlingJakartaHttpServletRequest request = new SlingJakartaHttpServletRequestImpl(rd, baseRequest);
        assertNull(request.getResponseContentType());
        assertEmptyEnumerator(request.getResponseContentTypes());

        Mockito.when(rpi.getExtension()).thenReturn("jpg");
        request = new SlingJakartaHttpServletRequestImpl(rd, baseRequest);
        assertNull(request.getResponseContentType());
        assertEmptyEnumerator(request.getResponseContentTypes());

        // second tests - processor returning footype for jpg
        Mockito.when(processor.getMimeType("dummy.jpg")).thenReturn("footype");
        Mockito.when(rpi.getExtension()).thenReturn(null);
        request = new SlingJakartaHttpServletRequestImpl(rd, baseRequest);
        assertNull(request.getResponseContentType());
        assertEmptyEnumerator(request.getResponseContentTypes());

        Mockito.when(rpi.getExtension()).thenReturn("jpg");
        request = new SlingJakartaHttpServletRequestImpl(rd, baseRequest);
        assertEquals("footype", request.getResponseContentType());
        assertSingletonEnumerator(request.getResponseContentTypes(), "footype");

        Mockito.when(rpi.getExtension()).thenReturn("pdf");
        request = new SlingJakartaHttpServletRequestImpl(rd, baseRequest);
        assertNull(request.getResponseContentType());
        assertEmptyEnumerator(request.getResponseContentTypes());
    }
}
