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
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.request.ResponseUtil;
import org.apache.sling.api.request.header.JakartaMediaRangeList;
import org.apache.sling.api.servlets.ErrorHandler;
import org.apache.sling.api.servlets.JakartaErrorHandler;
import org.apache.sling.api.wrappers.JakartaToJavaxRequestWrapper;
import org.apache.sling.api.wrappers.JakartaToJavaxResponseWrapper;
import org.osgi.framework.ServiceReference;
import org.osgi.util.converter.Converters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <code>DefaultErrorHandler</code> is used by the
 * {@link SlingRequestProcessorImpl} for error handling. It works
 * in combination with the error filter chain. If a {@link ErrorHandler} service
 * is registered, the actual response generated is delegated to that service.
 */
public class DefaultErrorHandler implements JakartaErrorHandler {

    /** default log */
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final String ALL_CONTENT_TYPES = "*/*";

    /** Use this if not null, and if that fails output a report about that failure */
    private volatile JakartaErrorHandler delegate;

    /** Use this if not null for non html responses */
    private volatile JakartaErrorHandler nonHtmlDelegate;

    private volatile JakartaErrorHandler errorHandler;
    private volatile JakartaErrorHandler jakartaErrorHandler;
    private volatile ServiceReference<?> errorHandlerRef;
    private volatile ServiceReference<?> jakartaErrorHandlerRef;

    private final Map<ServiceReference<JakartaErrorHandler>, JakartaErrorHandler> jakartaErrorHandlers =
            new TreeMap<>(Comparator.reverseOrder());

    /** Flag for html accept header check for error handling */
    private volatile boolean enableAcceptHeaderCheck = true;

    @SuppressWarnings("deprecation")
    public synchronized void setDelegate(final ServiceReference<?> ref, final ErrorHandler eh) {
        if (eh != null) {
            this.errorHandler = new JakartaErrorHandler() {
                @Override
                public void handleError(
                        int status,
                        String message,
                        SlingJakartaHttpServletRequest request,
                        SlingJakartaHttpServletResponse response)
                        throws IOException {
                    eh.handleError(
                            status,
                            message,
                            (SlingHttpServletRequest) JakartaToJavaxRequestWrapper.toJavaxRequest(request),
                            (SlingHttpServletResponse) JakartaToJavaxResponseWrapper.toJavaxResponse(response));
                }

                @Override
                public void handleError(
                        Throwable throwable,
                        SlingJakartaHttpServletRequest request,
                        SlingJakartaHttpServletResponse response)
                        throws IOException {
                    eh.handleError(
                            throwable,
                            (SlingHttpServletRequest) JakartaToJavaxRequestWrapper.toJavaxRequest(request),
                            (SlingHttpServletResponse) JakartaToJavaxResponseWrapper.toJavaxResponse(response));
                }
            };
            this.errorHandlerRef = ref;
            if (this.jakartaErrorHandlerRef == null || this.jakartaErrorHandlerRef.compareTo(ref) < 0) {
                this.delegate = this.errorHandler;
            }
        } else {
            this.errorHandler = null;
            this.errorHandlerRef = null;
            this.delegate = this.jakartaErrorHandler;
        }
    }

    public synchronized void setDelegate(final ServiceReference<?> ref, final JakartaErrorHandler eh) {
        if (eh != null) {
            this.jakartaErrorHandler = eh;
            this.jakartaErrorHandlerRef = ref;
            if (this.errorHandlerRef == null || this.errorHandlerRef.compareTo(ref) < 0) {
                this.delegate = this.jakartaErrorHandler;
            }
        } else {
            this.jakartaErrorHandler = null;
            this.jakartaErrorHandlerRef = null;
            this.delegate = this.errorHandler;
        }
    }

    private void delegateFailed(
            int originalStatus,
            String originalMessage,
            Throwable t,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        // don't include Throwable in the response, gives too much information
        final String m = "Error handler failed:" + t.getClass().getName();
        log.error(m, t);

        if (response.isCommitted()) {
            log.warn("handleError: Response already committed; cannot send error " + originalStatus + " : "
                    + originalMessage);
            return;
        }
        // reset the response to clear headers and body
        // the error filters are NOT called in this edge case
        response.reset();
        sendError(originalStatus, originalMessage, null, request, response);
    }

    protected JakartaErrorHandler getErrorHandler(final SlingJakartaHttpServletRequest request) {
        final JakartaErrorHandler nonHtml = this.nonHtmlDelegate;
        if (this.enableAcceptHeaderCheck && nonHtml != null && !acceptsHtml(request)) {
            return nonHtml;
        }
        return this.delegate;
    }

    /**
     * A request accepts html if it explicitly accepts {@code text/html} or {@code *}{@code /*}.
     * A missing accept header and partial wildcards like {@code text/*} are not considered html.
     * Only the accept header is evaluated: request parameters are not read, as parameter
     * parsing might itself be the cause of the error being handled.
     */
    private static boolean acceptsHtml(final SlingJakartaHttpServletRequest request) {
        final String accept = request.getHeader(JakartaMediaRangeList.HEADER_ACCEPT);
        if (accept == null || accept.trim().isEmpty()) {
            return false;
        }
        // TODO - do we want to treat */* as html?
        return new JakartaMediaRangeList(accept).prefer(JakartaMediaRangeList.CONTENT_TYPE_HTML, ALL_CONTENT_TYPES)
                != null;
    }

    // ---------- ErrorHandler interface (default implementation) --------------

    /**
     * Backend implementation of the HttpServletResponse.sendError methods.
     * <p>
     * This implementation resets the response before sending back a
     * standardized response which just conveys the status and the message
     * (either provided or a message derived from the status code).
     * <p>
     * This method logs error and does not write back and response data if the
     * response has already been committed.
     */
    @Override
    public void handleError(
            final int status,
            String message,
            final SlingJakartaHttpServletRequest request,
            final SlingJakartaHttpServletResponse response)
            throws IOException {
        // If we have a delegate let it handle the error
        final JakartaErrorHandler local = this.getErrorHandler(request);
        if (local != null) {
            try {
                local.handleError(status, message, request, response);
            } catch (final Exception e) {
                delegateFailed(status, message, e, request, response);
            }
            return;
        }

        log.warn(
                "handleError: No ErrorHandler service registered; every Sling instance should have one. "
                        + "Falling back to the minimal built-in error response for status {}",
                status);

        if (message == null) {
            message = "HTTP ERROR:" + String.valueOf(status);
        } else {
            message = "HTTP ERROR:" + status + " - " + message;
        }

        sendError(status, message, null, request, response);
    }

    /**
     * Backend implementation of handling uncaught throwables.
     * <p>
     * This implementation resets the response before sending back a
     * standardized response which just conveys the status as 500/INTERNAL
     * SERVER ERROR and the message from the throwable. The exception's
     * stacktrace and the {@code RequestProgressTracker} dump are not sent to
     * the client; they are only available in the server-side log (see the
     * caller of this method).
     * <p>
     * This method logs error and does not write back and response data if the
     * response has already been committed.
     */
    @Override
    public void handleError(
            final Throwable throwable,
            final SlingJakartaHttpServletRequest request,
            final SlingJakartaHttpServletResponse response)
            throws IOException {
        final int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        // If we have a delegate let it handle the error
        final JakartaErrorHandler local = this.getErrorHandler(request);
        if (local != null) {
            try {
                local.handleError(throwable, request, response);
            } catch (final Exception e) {
                delegateFailed(status, throwable.toString(), e, request, response);
            }
            return;
        }

        log.warn("handleError: No ErrorHandler service registered; every Sling instance should have one. "
                + "Falling back to the minimal built-in error response.");

        sendError(status, throwable.getMessage(), throwable, request, response);
    }

    private void sendError(
            final int status,
            final String message,
            final Throwable throwable,
            final HttpServletRequest request,
            final HttpServletResponse response)
            throws IOException {
        // error situation
        final String servletName = (String) request.getAttribute(RequestDispatcher.ERROR_SERVLET_NAME);
        String requestURI = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (requestURI == null) {
            requestURI = request.getRequestURI();
        }

        // set the status, content type and encoding
        response.setStatus(status);
        response.setContentType("text/html; charset=UTF-8");

        final PrintWriter pw = response.getWriter();
        pw.print("<html><head><title>");
        if (message == null) {
            pw.print("Internal error");
        } else {
            pw.print(ResponseUtil.escapeXml(message));
        }
        pw.println("</title></head><body><h1>");
        if (throwable != null) {
            pw.println(ResponseUtil.escapeXml(throwable.toString()));
        } else if (message != null) {
            pw.println(ResponseUtil.escapeXml(message));
        } else {
            pw.println("Internal error (no Exception to report)");
        }
        pw.println("</h1><p>");
        pw.print("RequestURI=");
        pw.println(ResponseUtil.escapeXml(request.getRequestURI()));
        if (servletName != null) {
            pw.println("</p><p>Servlet=");
            pw.println(ResponseUtil.escapeXml(servletName));
        }
        pw.println("</p>");

        pw.println("</body></html>");

        // commit the response
        response.flushBuffer();
        // close the response (SLING-2724)
        pw.close();
    }

    private boolean matchesName(final ServiceReference<JakartaErrorHandler> ref, final String name) {
        final String[] names = Converters.standardConverter()
                .convert(ref.getProperty(JakartaErrorHandler.PROPERTY_NAME))
                .to(String[].class);

        for (final String n : names) {
            if (name.equals(n)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Set the non html error handler
     */
    private void setNonHtmlErrorHandler() {
        final Optional<Map.Entry<ServiceReference<JakartaErrorHandler>, JakartaErrorHandler>> first =
                jakartaErrorHandlers.entrySet().stream()
                        .filter(entry -> matchesName(entry.getKey(), JakartaErrorHandler.NAME_NONHTML))
                        .findFirst();
        if (first.isPresent()) {
            this.nonHtmlDelegate = first.get().getValue();
        } else {
            this.nonHtmlDelegate = null;
        }
    }

    /**
     * Add a named delegate for handling non-HTML errors.
     * @param ref the service reference for the delegate
     * @param handler the error handler instance for the delegate
     */
    public synchronized void addNamedDelegate(
            final ServiceReference<JakartaErrorHandler> ref, final JakartaErrorHandler handler) {
        this.jakartaErrorHandlers.put(ref, handler);
        this.setNonHtmlErrorHandler();
    }

    /**
     * Remove a named delegate for handling non-HTML errors.
     * @param ref the service reference for the delegate
     */
    public synchronized void removeNamedDelegate(final ServiceReference<JakartaErrorHandler> ref) {
        this.jakartaErrorHandlers.remove(ref);
        this.setNonHtmlErrorHandler();
    }

    public void setEnableAcceptHeaderCheck(final boolean enable) {
        this.enableAcceptHeaderCheck = enable;
    }
}
