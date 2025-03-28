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
package org.apache.sling.engine.impl.helper;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.ServletRegistration.Dynamic;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletResponseWrapper;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.SessionTrackingMode;
import jakarta.servlet.descriptor.JspConfigDescriptor;
import org.apache.sling.engine.impl.SlingJakartaHttpServletRequestImpl;
import org.apache.sling.engine.impl.SlingJakartaHttpServletResponseImpl;

/**
 * Wrapper around a ServletContext for an external servlet context, i.e. one
 * returned by servletContext.getContext(String)
 */
class ExternalServletContextWrapper implements ServletContext {

    private final ServletContext delegate;

    public ExternalServletContextWrapper(ServletContext sc) {
        this.delegate = sc;
    }

    protected final ServletContext getServletContext() {
        return delegate;
    }

    @Override
    public ServletContext getContext(String s) {
        return getServletContext().getContext(s);
    }

    @Override
    public int getMajorVersion() {
        return getServletContext().getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return getServletContext().getMinorVersion();
    }

    @Override
    public String getMimeType(String s) {
        return getServletContext().getMimeType(s);
    }

    @Override
    public Set<String> getResourcePaths(String s) {
        return getServletContext().getResourcePaths(s);
    }

    @Override
    public URL getResource(String s) throws MalformedURLException {
        return getServletContext().getResource(s);
    }

    @Override
    public InputStream getResourceAsStream(String s) {
        return getServletContext().getResourceAsStream(s);
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String s) {
        return new RequestDispatcherWrapper(getServletContext().getRequestDispatcher(s));
    }

    @Override
    public RequestDispatcher getNamedDispatcher(String s) {
        return new RequestDispatcherWrapper(getServletContext().getNamedDispatcher(s));
    }

    @Override
    public void log(String s) {
        getServletContext().log(s);
    }

    @Override
    public void log(String s, Throwable throwable) {
        getServletContext().log(s, throwable);
    }

    @Override
    public String getRealPath(String s) {
        return getServletContext().getRealPath(s);
    }

    @Override
    public String getServerInfo() {
        return getServletContext().getServerInfo();
    }

    @Override
    public String getInitParameter(String s) {
        return getServletContext().getInitParameter(s);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return getServletContext().getInitParameterNames();
    }

    @Override
    public Object getAttribute(String s) {
        return getServletContext().getAttribute(s);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return getServletContext().getAttributeNames();
    }

    @Override
    public void setAttribute(String s, Object obj) {
        getServletContext().setAttribute(s, obj);
    }

    @Override
    public void removeAttribute(String s) {
        getServletContext().removeAttribute(s);
    }

    @Override
    public String getServletContextName() {
        return getServletContext().getServletContextName();
    }

    @Override
    public String getContextPath() {
        return getServletContext().getContextPath();
    }

    static class RequestDispatcherWrapper implements RequestDispatcher {

        private final RequestDispatcher delegate;

        public RequestDispatcherWrapper(final RequestDispatcher rd) {
            this.delegate = rd;
        }

        @Override
        public void forward(final ServletRequest request, final ServletResponse response)
                throws ServletException, IOException {
            delegate.forward(unwrapServletRequest(request), unwrapServletResponse(response));
        }

        @Override
        public void include(final ServletRequest request, final ServletResponse response)
                throws ServletException, IOException {
            delegate.include(unwrapServletRequest(request), unwrapServletResponse(response));
        }

        RequestDispatcher getDelegate() {
            return delegate;
        }

        static ServletRequest unwrapServletRequest(ServletRequest request) {
            ServletRequest lastRequest = request;
            while (request != null) {
                if (request instanceof SlingJakartaHttpServletRequestImpl) {
                    return ((SlingJakartaHttpServletRequestImpl) request).getRequest();
                } else if (request instanceof ServletRequestWrapper) {
                    lastRequest = request;
                    request = ((ServletRequestWrapper) request).getRequest();
                } else {
                    return request;
                }
            }
            return lastRequest;
        }

        static ServletResponse unwrapServletResponse(ServletResponse response) {
            ServletResponse lastResponse = response;
            while (response != null) {
                if (response instanceof SlingJakartaHttpServletResponseImpl) {
                    return ((SlingJakartaHttpServletResponseImpl) response).getResponse();
                } else if (response instanceof ServletResponseWrapper) {
                    lastResponse = response;
                    response = ((ServletResponseWrapper) response).getResponse();
                } else {
                    return response;
                }
            }
            return lastResponse;
        }
    }

    @Override
    public int getEffectiveMajorVersion() {
        return getServletContext().getEffectiveMajorVersion();
    }

    @Override
    public int getEffectiveMinorVersion() {
        return getServletContext().getEffectiveMinorVersion();
    }

    @Override
    public boolean setInitParameter(String name, String value) {
        return getServletContext().setInitParameter(name, value);
    }

    @Override
    public Dynamic addServlet(String servletName, String className) {
        return getServletContext().addServlet(servletName, className);
    }

    @Override
    public Dynamic addServlet(String servletName, Servlet servlet) {
        return getServletContext().addServlet(servletName, servlet);
    }

    @Override
    public Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {
        return getServletContext().addServlet(servletName, servletClass);
    }

    @Override
    public <T extends Servlet> T createServlet(Class<T> clazz) throws ServletException {
        return getServletContext().createServlet(clazz);
    }

    @Override
    public ServletRegistration getServletRegistration(String servletName) {
        return getServletContext().getServletRegistration(servletName);
    }

    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations() {
        return getServletContext().getServletRegistrations();
    }

    @Override
    public jakarta.servlet.FilterRegistration.Dynamic addFilter(String filterName, String className) {
        return getServletContext().addFilter(filterName, className);
    }

    @Override
    public jakarta.servlet.FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
        return getServletContext().addFilter(filterName, filter);
    }

    @Override
    public jakarta.servlet.FilterRegistration.Dynamic addFilter(
            String filterName, Class<? extends Filter> filterClass) {
        return getServletContext().addFilter(filterName, filterClass);
    }

    @Override
    public <T extends Filter> T createFilter(Class<T> clazz) throws ServletException {
        return getServletContext().createFilter(clazz);
    }

    @Override
    public FilterRegistration getFilterRegistration(String filterName) {
        return getServletContext().getFilterRegistration(filterName);
    }

    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
        return getServletContext().getFilterRegistrations();
    }

    @Override
    public SessionCookieConfig getSessionCookieConfig() {
        return getServletContext().getSessionCookieConfig();
    }

    @Override
    public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
        getServletContext().setSessionTrackingModes(sessionTrackingModes);
    }

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        return getServletContext().getDefaultSessionTrackingModes();
    }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        return getServletContext().getEffectiveSessionTrackingModes();
    }

    @Override
    public void addListener(String className) {
        getServletContext().addListener(className);
    }

    @Override
    public <T extends EventListener> void addListener(T t) {
        getServletContext().addListener(t);
    }

    @Override
    public void addListener(Class<? extends EventListener> listenerClass) {
        getServletContext().addListener(listenerClass);
    }

    @Override
    public <T extends EventListener> T createListener(Class<T> clazz) throws ServletException {
        return getServletContext().createListener(clazz);
    }

    @Override
    public JspConfigDescriptor getJspConfigDescriptor() {
        return getServletContext().getJspConfigDescriptor();
    }

    @Override
    public ClassLoader getClassLoader() {
        return getServletContext().getClassLoader();
    }

    @Override
    public void declareRoles(String... roleNames) {
        getServletContext().declareRoles(roleNames);
    }

    @Override
    public String getVirtualServerName() {
        return getServletContext().getVirtualServerName();
    }

    @Override
    public Dynamic addJspFile(String servletName, String jspFile) {
        return getServletContext().addJspFile(servletName, jspFile);
    }

    @Override
    public String getRequestCharacterEncoding() {
        return getServletContext().getRequestCharacterEncoding();
    }

    @Override
    public String getResponseCharacterEncoding() {
        return getServletContext().getResponseCharacterEncoding();
    }

    @Override
    public int getSessionTimeout() {
        return getServletContext().getSessionTimeout();
    }

    @Override
    public void setRequestCharacterEncoding(String encoding) {
        getServletContext().setRequestCharacterEncoding(encoding);
    }

    @Override
    public void setResponseCharacterEncoding(String encoding) {
        getServletContext().setResponseCharacterEncoding(encoding);
    }

    @Override
    public void setSessionTimeout(int sessionTimeout) {
        getServletContext().setSessionTimeout(sessionTimeout);
    }
}
