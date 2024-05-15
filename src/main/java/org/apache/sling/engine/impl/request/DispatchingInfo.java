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
package org.apache.sling.engine.impl.request;

import javax.servlet.DispatcherType;
import javax.servlet.Servlet;

import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;

public class DispatchingInfo {

    private final DispatcherType type;
    private Resource requestContent;
    private Servlet requestServlet;
    private RequestPathInfo requestpathInfo;
    private String contextPath;
    private String pathInfo;
    private String queryString;
    private String requestUri;
    private String servletPath;
    private boolean protectHeadersOnInclude;
    private boolean checkContentTypeOnInclude;

    public DispatchingInfo(final DispatcherType type) {
        this.type = type;
    }

    public DispatcherType getType() {
        return type;
    }

    public Resource getRequestContent() {
        return requestContent;
    }

    public void setRequestContent(final Resource requestContent) {
        this.requestContent = requestContent;
    }

    public Servlet getRequestServlet() {
        return requestServlet;
    }

    public void setRequestServlet(final Servlet requestServlet) {
        this.requestServlet = requestServlet;
    }

    public RequestPathInfo getRequestPathInfo() {
        return requestpathInfo;
    }

    public void setRequestPathInfo(final RequestPathInfo requestpathInfo) {
        this.requestpathInfo = requestpathInfo;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(final String contextPath) {
        this.contextPath = contextPath;
    }

    public String getPathInfo() {
        return pathInfo;
    }

    public void setPathInfo(final String pathInfo) {
        this.pathInfo = pathInfo;
    }

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(final String queryString) {
        this.queryString = queryString;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public void setRequestUri(final String requestUri) {
        this.requestUri = requestUri;
    }

    public String getServletPath() {
        return servletPath;
    }

    public void setServletPath(final String servletPath) {
        this.servletPath = servletPath;
    }

    public boolean isProtectHeadersOnInclude() {
        return protectHeadersOnInclude;
    }

    public void setProtectHeadersOnInclude(final boolean protectHeadersOnInclude) {
        this.protectHeadersOnInclude = protectHeadersOnInclude;
    }

    public boolean isCheckContentTypeOnInclude() {
        return checkContentTypeOnInclude;
    }

    public void setCheckContentTypeOnInclude(final boolean checkContentTypeOnInclude) {
        this.checkContentTypeOnInclude = checkContentTypeOnInclude;
    }
}
