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

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.DispatcherType;

import static javax.servlet.RequestDispatcher.FORWARD_CONTEXT_PATH;
import static javax.servlet.RequestDispatcher.FORWARD_PATH_INFO;
import static javax.servlet.RequestDispatcher.FORWARD_QUERY_STRING;
import static javax.servlet.RequestDispatcher.FORWARD_REQUEST_URI;
import static javax.servlet.RequestDispatcher.FORWARD_SERVLET_PATH;
import static javax.servlet.RequestDispatcher.INCLUDE_CONTEXT_PATH;
import static javax.servlet.RequestDispatcher.INCLUDE_PATH_INFO;
import static javax.servlet.RequestDispatcher.INCLUDE_QUERY_STRING;
import static javax.servlet.RequestDispatcher.INCLUDE_REQUEST_URI;
import static javax.servlet.RequestDispatcher.INCLUDE_SERVLET_PATH;

import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.wrappers.SlingHttpServletRequestWrapper;

public class DispatcherRequestWrapper extends SlingHttpServletRequestWrapper {

    private static final List<String> FORWARD_ATTRIBUTES = Arrays.asList(FORWARD_CONTEXT_PATH,
        FORWARD_PATH_INFO, FORWARD_QUERY_STRING, FORWARD_REQUEST_URI, FORWARD_SERVLET_PATH);

    private static final List<String> INCLUDE_ATTRIBUTES = Arrays.asList(
        SlingConstants.ATTR_REQUEST_CONTENT, SlingConstants.ATTR_REQUEST_SERVLET, SlingConstants.ATTR_REQUEST_PATH_INFO,
        INCLUDE_CONTEXT_PATH, INCLUDE_PATH_INFO, INCLUDE_QUERY_STRING, INCLUDE_REQUEST_URI, INCLUDE_SERVLET_PATH);

    private final DispatchingInfo dispatchingInfo;

    public DispatcherRequestWrapper(final SlingHttpServletRequest request, final DispatchingInfo dispatchingInfo) {
        super(request);
        this.dispatchingInfo = dispatchingInfo;
    }

    @Override
    public Object getAttribute(final String name) {
        if (this.dispatchingInfo.getType() == DispatcherType.INCLUDE) {
            if (SlingConstants.ATTR_REQUEST_CONTENT.equals(name)) {
                return this.dispatchingInfo.getRequestContent();
            } else if (SlingConstants.ATTR_REQUEST_SERVLET.equals(name)) {
                return this.dispatchingInfo.getRequestServlet();
            } else if (SlingConstants.ATTR_REQUEST_PATH_INFO.equals(name)) {
                return this.dispatchingInfo.getRequestPathInfo();
            } else if (INCLUDE_CONTEXT_PATH.equals(name)) {
                return this.dispatchingInfo.getContextPath();
            } else if (INCLUDE_PATH_INFO.equals(name)) {
                return this.dispatchingInfo.getPathInfo();
            } else if (INCLUDE_QUERY_STRING.equals(name)) {
                return this.dispatchingInfo.getQueryString();
            } else if (INCLUDE_REQUEST_URI.equals(name)) {
                return this.dispatchingInfo.getRequestUri();
            } else if (INCLUDE_SERVLET_PATH.equals(name)) {
                return this.dispatchingInfo.getServletPath();
            } else if (FORWARD_ATTRIBUTES.contains(name) ) {
                // include might be contained within a forward, allow forward attributes
                return super.getAttribute(name);
            }
        }
        // block all special attributes
        if (INCLUDE_ATTRIBUTES.contains(name) || FORWARD_ATTRIBUTES.contains(name)) {
            return null;
        }
        return super.getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        if ( this.dispatchingInfo.getType() == DispatcherType.INCLUDE ) {
            final Set<String> allNames = new HashSet<>(Collections.list(super.getAttributeNames()));
            allNames.addAll(INCLUDE_ATTRIBUTES);
            return Collections.enumeration(allNames);
        }
        return super.getAttributeNames();
    }
}
