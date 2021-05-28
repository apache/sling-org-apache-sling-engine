/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.engine.impl.request;


import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestParameterMap;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.api.request.TooManyCallsException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.engine.impl.SlingHttpServletRequestImpl;
import org.apache.sling.engine.impl.SlingHttpServletResponseImpl;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import static org.junit.Assert.*;

public class RequestDataTest {

    private Mockery context;
    private RequestData requestData;
    private HttpServletRequest req;
    private HttpServletResponse resp;
    private SlingHttpServletRequest slingRequest;
    private SlingHttpServletResponse slingResponse;

    @Before
    public void setup() throws ServletException, IOException {
        context = new Mockery() {{
            setImposteriser(ClassImposteriser.INSTANCE);
        }};

        req = context.mock(HttpServletRequest.class);
        resp = context.mock(HttpServletResponse.class);

        final ContentData contentData = context.mock(ContentData.class);
        final Servlet servlet = context.mock(Servlet.class);
        final ServletConfig servletConfig = context.mock(ServletConfig.class);

        context.checking(new Expectations() {{
            allowing(req).getServletPath();
            will(returnValue("/"));

            allowing(req).getPathInfo();
            will(returnValue(""));

            allowing(req).getMethod();
            will(returnValue("GET"));

            allowing(req).setAttribute(with(any(String.class)), with(any(Object.class)));
            allowing(req).setAttribute(with(any(String.class)), with(aNull(Object.class)));

            allowing(contentData).getServlet();
            will(returnValue(servlet));

            allowing(servlet).getServletConfig();
            will(returnValue(servletConfig));

            allowing(contentData).getRequestPathInfo();

            allowing(servlet).service(with(any(ServletRequest.class)), with(any(ServletResponse.class)));

            allowing(servletConfig).getServletName();
            will(returnValue("SERVLET_NAME"));

            allowing(req).getAttribute(RequestProgressTracker.class.getName());
            will(returnValue(null));
        }});

        requestData = new RequestData(null, req, resp) {
            @Override
            public ContentData getContentData() {
                return contentData;
            }
        };

        slingRequest = new SlingHttpServletRequestImpl(requestData, req);
        slingResponse = new SlingHttpServletResponseImpl(requestData, resp);

        RequestData.setMaxCallCounter(2);
    }

    private void assertTooManyCallsException(int failAtCall) throws Exception {
        for(int i=0; i  < failAtCall - 1; i++) {
            RequestData.service(slingRequest, slingResponse);
        }
        try {
            RequestData.service(slingRequest, slingResponse);
            fail("Expected RequestData.service to fail when called " + failAtCall + " times");
        } catch(TooManyCallsException tme) {
            // as expected
        }
    }

    @Test
    public void testTooManyCallsDefault() throws Exception {
        context.checking(new Expectations() {{
            allowing(req).getAttribute(with(any(String.class)));
            will(returnValue(null));
        }});
        assertTooManyCallsException(3);
    }

    @Test
    public void testTooManyCallsOverride() throws Exception {
        context.checking(new Expectations() {{
            allowing(req).getAttribute(with(any(String.class)));
            will(returnValue(1));
        }});
        assertTooManyCallsException(2);
    }

    @Test
    public void testConsecutiveDots() {
        assertValidRequest(true, "/path/content../test");
        assertValidRequest(false, "/../path/content../test");
        assertValidRequest(false, "/../path/content/.../test");
        assertValidRequest(false, "../path/content/.../test");
        assertValidRequest(false, "/content/.../test");
    }

    @Test
    public void testConsecutiveDotsAfterPathSeparator() {
        assertValidRequest(false, "/path/....");
        assertValidRequest(false, "/path/..");
        assertValidRequest(true, "/path/foo..");
        assertValidRequest(true, "/path/..foo..");
    }

    @Test
    public void testDots() {
        assertValidRequest(false, "/a/.../b");
        assertValidRequest(false, "/a/............../b");
        assertValidRequest(true, "/a/..........helloo......./b");
        assertValidRequest(true, "/path/content./test");
        assertValidRequest(true, "/./path/content./test");
        assertValidRequest(true, "/./path/content/./test");
        assertValidRequest(true, "./path/content/./test");
        assertValidRequest(true, "/content/./test");
    }

    @Test
    public void testDotsAnd5B() {
        assertValidRequest(false, "/a/..[[./b");
        assertValidRequest(false, "/a/[............../b");
        assertValidRequest(true, "/a/..........helloo......./b");
        assertValidRequest(false, "/a/[..");
    }

    @Test
    public void testValidRequest() {
        //HttpRequest with valid path
        assertValidRequest(true, "/path");
    }

    private static void assertValidRequest(boolean expected, final String path) {
        assertEquals(
                "Expected " + expected + " for " + path,
                expected,
                RequestData.isValidRequest(new SlingHttpServletRequest(){

                    @Override
                    public <AdapterType> AdapterType adaptTo(Class<AdapterType> aClass) {
                        return null;
                    }

                    @Override
                    public Object getAttribute(String s) {
                        return null;
                    }

                    @Override
                    public Enumeration<String> getAttributeNames() {
                        return null;
                    }

                    @Override
                    public String getCharacterEncoding() {
                        return null;
                    }

                    @Override
                    public void setCharacterEncoding(String s) throws UnsupportedEncodingException {

                    }

                    @Override
                    public int getContentLength() {
                        return 0;
                    }

                    @Override
                    public long getContentLengthLong() {
                        return 0;
                    }

                    @Override
                    public String getContentType() {
                        return null;
                    }

                    @Override
                    public ServletInputStream getInputStream() throws IOException {
                        return null;
                    }

                    @Override
                    public String getParameter(String s) {
                        return null;
                    }

                    @Override
                    public Enumeration<String> getParameterNames() {
                        return null;
                    }

                    @Override
                    public String[] getParameterValues(String s) {
                        return new String[0];
                    }

                    @Override
                    public Map<String, String[]> getParameterMap() {
                        return null;
                    }

                    @Override
                    public String getProtocol() {
                        return null;
                    }

                    @Override
                    public String getScheme() {
                        return null;
                    }

                    @Override
                    public String getServerName() {
                        return null;
                    }

                    @Override
                    public int getServerPort() {
                        return 0;
                    }

                    @Override
                    public BufferedReader getReader() throws IOException {
                        return null;
                    }

                    @Override
                    public String getRemoteAddr() {
                        return null;
                    }

                    @Override
                    public String getRemoteHost() {
                        return null;
                    }

                    @Override
                    public void setAttribute(String s, Object o) {

                    }

                    @Override
                    public void removeAttribute(String s) {

                    }

                    @Override
                    public Locale getLocale() {
                        return null;
                    }

                    @Override
                    public Enumeration<Locale> getLocales() {
                        return null;
                    }

                    @Override
                    public boolean isSecure() {
                        return false;
                    }

                    @Override
                    public RequestDispatcher getRequestDispatcher(String s) {
                        return null;
                    }

                    @Override
                    public String getRealPath(String s) {
                        return null;
                    }

                    @Override
                    public int getRemotePort() {
                        return 0;
                    }

                    @Override
                    public String getLocalName() {
                        return null;
                    }

                    @Override
                    public String getLocalAddr() {
                        return null;
                    }

                    @Override
                    public int getLocalPort() {
                        return 0;
                    }

                    @Override
                    public ServletContext getServletContext() {
                        return null;
                    }

                    @Override
                    public AsyncContext startAsync() throws IllegalStateException {
                        return null;
                    }

                    @Override
                    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
                        return null;
                    }

                    @Override
                    public boolean isAsyncStarted() {
                        return false;
                    }

                    @Override
                    public boolean isAsyncSupported() {
                        return false;
                    }

                    @Override
                    public AsyncContext getAsyncContext() {
                        return null;
                    }

                    @Override
                    public DispatcherType getDispatcherType() {
                        return null;
                    }

                    @Override
                    public String getAuthType() {
                        return null;
                    }

                    @Override
                    public Cookie[] getCookies() {
                        return new Cookie[0];
                    }

                    @Override
                    public long getDateHeader(String s) {
                        return 0;
                    }

                    @Override
                    public String getHeader(String s) {
                        return null;
                    }

                    @Override
                    public Enumeration<String> getHeaders(String s) {
                        return null;
                    }

                    @Override
                    public Enumeration<String> getHeaderNames() {
                        return null;
                    }

                    @Override
                    public int getIntHeader(String s) {
                        return 0;
                    }

                    @Override
                    public String getMethod() {
                        return null;
                    }

                    @Override
                    public String getPathInfo() {
                        return path;
                    }

                    @Override
                    public String getPathTranslated() {
                        return null;
                    }

                    @Override
                    public String getContextPath() {
                        return null;
                    }

                    @Override
                    public String getQueryString() {
                        return null;
                    }

                    @Override
                    public String getRemoteUser() {
                        return null;
                    }

                    @Override
                    public boolean isUserInRole(String s) {
                        return false;
                    }

                    @Override
                    public Principal getUserPrincipal() {
                        return null;
                    }

                    @Override
                    public String getRequestedSessionId() {
                        return null;
                    }

                    @Override
                    public String getRequestURI() {
                        return null;
                    }

                    @Override
                    public StringBuffer getRequestURL() {
                        return null;
                    }

                    @Override
                    public String getServletPath() {
                        return null;
                    }

                    @Override
                    public HttpSession getSession(boolean b) {
                        return null;
                    }

                    @Override
                    public HttpSession getSession() {
                        return null;
                    }

                    @Override
                    public String changeSessionId() {
                        return null;
                    }

                    @Override
                    public boolean isRequestedSessionIdValid() {
                        return false;
                    }

                    @Override
                    public boolean isRequestedSessionIdFromCookie() {
                        return false;
                    }

                    @Override
                    public boolean isRequestedSessionIdFromURL() {
                        return false;
                    }

                    @Override
                    public boolean isRequestedSessionIdFromUrl() {
                        return false;
                    }

                    @Override
                    public boolean authenticate(HttpServletResponse httpServletResponse) throws IOException, ServletException {
                        return false;
                    }

                    @Override
                    public void login(String s, String s1) throws ServletException {

                    }

                    @Override
                    public void logout() throws ServletException {

                    }

                    @Override
                    public Collection<Part> getParts() throws IOException, ServletException {
                        return null;
                    }

                    @Override
                    public Part getPart(String s) throws IOException, ServletException {
                        return null;
                    }

                    @Override
                    public <T extends HttpUpgradeHandler> T upgrade(Class<T> aClass) throws IOException, ServletException {
                        return null;
                    }

                    @Override
                    public Resource getResource() {
                        return null;
                    }

                    @Override
                    public ResourceResolver getResourceResolver() {
                        return null;
                    }

                    @Override
                    public RequestPathInfo getRequestPathInfo() {
                        Resource r = new Resource() {
                            @Override
                            public String getPath() {
                                return path;
                            }

                            @Override
                            public String getName() {
                                return null;
                            }

                            @Override
                            public Resource getParent() {
                                return null;
                            }

                            @Override
                            public Iterator<Resource> listChildren() {
                                return null;
                            }

                            @Override
                            public Iterable<Resource> getChildren() {
                                return null;
                            }

                            @Override
                            public Resource getChild(String s) {
                                return null;
                            }

                            @Override
                            public String getResourceType() {
                                return null;
                            }

                            @Override
                            public String getResourceSuperType() {
                                return null;
                            }

                            @Override
                            public boolean hasChildren() {
                                return false;
                            }

                            @Override
                            public boolean isResourceType(String s) {
                                return false;
                            }

                            @Override
                            public ResourceMetadata getResourceMetadata() {
                                ResourceMetadata metadata = new ResourceMetadata(){
                                    @Override
                                    public String getResolutionPath() {
                                        return path;
                                    }

                                    @Override
                                    public String getResolutionPathInfo() {
                                        return path;
                                    }
                                };

                                return metadata;
                            }

                            @Override
                            public ResourceResolver getResourceResolver() {
                                return null;
                            }

                            @Override
                            public <AdapterType> AdapterType adaptTo(Class<AdapterType> aClass) {
                                return null;
                            }
                        };
                        return new SlingRequestPathInfo(r);
                    }

                    @Override
                    public RequestParameter getRequestParameter(String s) {
                        return null;
                    }

                    @Override
                    public RequestParameter[] getRequestParameters(String s) {
                        return new RequestParameter[0];
                    }

                    @Override
                    public RequestParameterMap getRequestParameterMap() {
                        return null;
                    }

                    @Override
                    public List<RequestParameter> getRequestParameterList() {
                        return null;
                    }

                    @Override
                    public RequestDispatcher getRequestDispatcher(String s, RequestDispatcherOptions requestDispatcherOptions) {
                        return null;
                    }

                    @Override
                    public RequestDispatcher getRequestDispatcher(Resource resource, RequestDispatcherOptions requestDispatcherOptions) {
                        return null;
                    }

                    @Override
                    public RequestDispatcher getRequestDispatcher(Resource resource) {
                        return null;
                    }

                    @Override
                    public Cookie getCookie(String s) {
                        return null;
                    }

                    @Override
                    public String getResponseContentType() {
                        return null;
                    }

                    @Override
                    public Enumeration<String> getResponseContentTypes() {
                        return null;
                    }

                    @Override
                    public ResourceBundle getResourceBundle(Locale locale) {
                        return null;
                    }

                    @Override
                    public ResourceBundle getResourceBundle(String s, Locale locale) {
                        return null;
                    }

                    @Override
                    public RequestProgressTracker getRequestProgressTracker() {
                        return null;
                    }
                })
        );
    }
}
