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

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import org.apache.sling.api.SlingException;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.engine.impl.request.RequestData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlingHttpServletResponseImpl extends HttpServletResponseWrapper implements SlingHttpServletResponse {

    private static final Logger LOG = LoggerFactory.getLogger(SlingHttpServletResponseImpl.class);

    public static class WriterAlreadyClosedException extends IllegalStateException {
        // just a marker class.
    }

    private static final Exception FLUSHER_STACK_DUMMY = new Exception();

    private Exception flusherStacktrace;

    private final RequestData requestData;

    private final boolean firstSlingResponse;

    public SlingHttpServletResponseImpl(RequestData requestData, HttpServletResponse response) {
        super(response);
        this.requestData = requestData;
        this.firstSlingResponse = !(response instanceof SlingHttpServletResponse);

        if (firstSlingResponse) {
            for (final StaticResponseHeader mapping :
                    requestData.getSlingRequestProcessor().getAdditionalResponseHeaders()) {
                response.addHeader(mapping.getResponseHeaderName(), mapping.getResponseHeaderValue());
            }
        }
    }

    protected final RequestData getRequestData() {
        return requestData;
    }

    // ---------- Adaptable interface

    public <AdapterType> AdapterType adaptTo(Class<AdapterType> type) {
        return getRequestData().getSlingRequestProcessor().adaptTo(this, type);
    }

    // ---------- Redirection support through PathResolver --------------------

    @Override
    public String encodeURL(final String url) {
        // remove context path
        String path = removeContextPath(url);

        // make the path absolute
        path = makeAbsolutePath(path);

        // resolve the url to as if it would be a resource path
        path = map(path);

        // have the servlet container to further encodings
        return super.encodeURL(path);
    }

    @Override
    public String encodeRedirectURL(final String url) {
        // remove context path
        String path = removeContextPath(url);

        // make the path absolute
        path = makeAbsolutePath(path);

        // resolve the url to as if it would be a resource path
        path = map(path);

        // have the servlet container to further encodings
        return super.encodeRedirectURL(path);
    }

    @Override
    @Deprecated
    public String encodeUrl(final String url) {
        return encodeURL(url);
    }

    @Override
    @Deprecated
    public String encodeRedirectUrl(final String url) {
        return encodeRedirectURL(url);
    }

    @Override
    public void flushBuffer() throws IOException {
        initFlusherStacktrace();
        super.flushBuffer();
    }

    private void initFlusherStacktrace() {
        if (flusherStacktrace == null) {
            if (LOG.isDebugEnabled()) {
                flusherStacktrace = new Exception("stacktrace where response was flushed");
            } else {
                // avoid creating exceptions if debug logging is not enabled
                flusherStacktrace = FLUSHER_STACK_DUMMY;
            }
        }
    }

    private boolean isProtectHeadersOnInclude() {
        return this.requestData.getDispatchingInfo() != null
                && this.requestData.getDispatchingInfo().isProtectHeadersOnInclude();
    }

    @Override
    public void setStatus(final int sc) {
        if (this.isProtectHeadersOnInclude()) {
            // ignore
            return;
        }
        setStatus(sc, null);
    }

    @Override
    public void setStatus(final int sc, final String msg) {
        if (this.isProtectHeadersOnInclude()) {
            // ignore
            return;
        }

        if (isCommitted()) {
            if (flusherStacktrace != null && flusherStacktrace != FLUSHER_STACK_DUMMY) {
                LOG.warn(
                        "Response already committed. Failed to set status code from {} to {}.",
                        getStatus(),
                        sc,
                        flusherStacktrace);
            } else {
                String explanation = flusherStacktrace != null
                        ? "Enable debug logging to find out where the response was committed."
                        : "The response was auto-committed due to the number of bytes written.";
                LOG.warn(
                        "Response already committed. Failed to set status code from {} to {}. {}",
                        getStatus(),
                        sc,
                        explanation);
            }
        } else { // response is not yet committed, so the statuscode can be changed
            if (msg == null) {
                super.setStatus(sc);
            } else {
                super.setStatus(sc, msg);
            }
        }
    }

    @Override
    public void reset() {
        if (!this.isProtectHeadersOnInclude()) {
            super.reset();
        } else {
            // ignore if not committed
            if (getResponse().isCommitted()) {
                getResponse().reset();
            }
        }
    }

    @Override
    public void setContentLength(final int len) {
        if (!this.isProtectHeadersOnInclude()) {
            super.setContentLength(len);
        }
    }

    @Override
    public void setContentLengthLong(final long len) {
        if (!this.isProtectHeadersOnInclude()) {
            super.setContentLengthLong(len);
        }
    }

    @Override
    public void setLocale(final Locale loc) {
        if (!this.isProtectHeadersOnInclude()) {
            super.setLocale(loc);
        }
    }

    @Override
    public void setBufferSize(final int size) {
        if (!this.isProtectHeadersOnInclude()) {
            super.setBufferSize(size);
        }
    }

    @Override
    public void addCookie(final Cookie cookie) {
        if (!this.isProtectHeadersOnInclude()) {
            super.addCookie(cookie);
        }
    }

    @Override
    public void addDateHeader(final String name, final long value) {
        if (!this.isProtectHeadersOnInclude()) {
            super.addDateHeader(name, value);
        }
    }

    @Override
    public void addHeader(final String name, final String value) {
        if (!this.isProtectHeadersOnInclude()) {
            super.addHeader(name, value);
        }
    }

    @Override
    public void addIntHeader(final String name, final int value) {
        if (!this.isProtectHeadersOnInclude()) {
            super.addIntHeader(name, value);
        }
    }

    @Override
    public void sendRedirect(final String location) throws IOException {
        if (!this.isProtectHeadersOnInclude()) {
            super.sendRedirect(location);
        }
    }

    @Override
    public void setDateHeader(final String name, final long value) {
        if (!this.isProtectHeadersOnInclude()) {
            super.setDateHeader(name, value);
        }
    }

    @Override
    public void setHeader(final String name, final String value) {
        if (!this.isProtectHeadersOnInclude()) {
            super.setHeader(name, value);
        }
    }

    @Override
    public void setIntHeader(final String name, final int value) {
        if (!this.isProtectHeadersOnInclude()) {
            super.setIntHeader(name, value);
        }
    }

    @Override
    public void setContentType(final String type) {
        if (this.requestData.getDispatchingInfo() != null
                && this.requestData.getDispatchingInfo().isCheckContentTypeOnInclude()) {
            String contentTypeString = getContentType();
            if (contentTypeString != null) {
                if (type == null) {
                    String message = getMessage(contentTypeString, "null");
                    requestData.getRequestProgressTracker().log("ERROR: " + message);
                    throw new ContentTypeChangeException(message);
                }
                Optional<String> currentMime =
                        Arrays.stream(contentTypeString.split(";")).findFirst();
                Optional<String> setMime = Arrays.stream(type.split(";")).findFirst();
                if (currentMime.isPresent()
                        && setMime.isPresent()
                        && !currentMime.get().equals(setMime.get())) {
                    String message = getMessage(contentTypeString, type);
                    requestData.getRequestProgressTracker().log("ERROR: " + message);
                    throw new ContentTypeChangeException(message);
                }
                if (!this.isProtectHeadersOnInclude()) {
                    getResponse().setContentType(type);
                }
            }
            return;
        }
        if (!this.isProtectHeadersOnInclude()) {
            super.setContentType(type);
        }
    }

    private String getMessage(String currentContentType, String setContentType) {
        return String.format(
                "Servlet %s tried to override the 'Content-Type' header from '%s' to '%s', however the"
                        + " %s forbids this via the %s configuration property.",
                requestData.getActiveServletName(),
                currentContentType,
                setContentType,
                Config.PID,
                "sling.includes.checkcontenttype");
    }

    private static class ContentTypeChangeException extends SlingException {
        protected ContentTypeChangeException(String text) {
            super(text);
        }
    }

    // ---------- Error handling through Sling Error Resolver -----------------

    @Override
    public void sendError(int status) throws IOException {
        if (!this.isProtectHeadersOnInclude()) {
            sendError(status, null);
        }
    }

    @Override
    public void sendError(int status, String message) throws IOException {
        if (!this.isProtectHeadersOnInclude()) {
            checkCommitted();

            final SlingRequestProcessorImpl eh = getRequestData().getSlingRequestProcessor();
            eh.handleError(status, message, requestData.getSlingRequest(), this);
        }
    }

    // ---------- Internal helper ---------------------------------------------

    @Override
    public PrintWriter getWriter() throws IOException {
        PrintWriter result = super.getWriter();
        if (firstSlingResponse) {
            final PrintWriter delegatee = result;
            result = new PrintWriter(result) {

                private boolean isClosed = false;

                private void checkClosed() {
                    if (this.isClosed) {
                        throw new WriterAlreadyClosedException();
                    }
                }

                @Override
                public PrintWriter append(final char arg0) {
                    this.checkClosed();
                    return delegatee.append(arg0);
                }

                @Override
                public PrintWriter append(final CharSequence arg0, final int arg1, final int arg2) {
                    this.checkClosed();
                    return delegatee.append(arg0, arg1, arg2);
                }

                @Override
                public PrintWriter append(final CharSequence arg0) {
                    this.checkClosed();
                    return delegatee.append(arg0);
                }

                @Override
                public boolean checkError() {
                    this.checkClosed();
                    return delegatee.checkError();
                }

                @Override
                public void close() {
                    this.checkClosed();
                    this.isClosed = true;
                    delegatee.close();
                }

                @Override
                public void flush() {
                    this.checkClosed();
                    initFlusherStacktrace();
                    delegatee.flush();
                }

                @Override
                public PrintWriter format(final Locale arg0, final String arg1, final Object... arg2) {
                    this.checkClosed();
                    return delegatee.format(arg0, arg1, arg2);
                }

                @Override
                public PrintWriter format(final String arg0, final Object... arg1) {
                    this.checkClosed();
                    return delegatee.format(arg0, arg1);
                }

                @Override
                public void print(final boolean arg0) {
                    this.checkClosed();
                    delegatee.print(arg0);
                }

                @Override
                public void print(final char arg0) {
                    this.checkClosed();
                    delegatee.print(arg0);
                }

                @Override
                public void print(final char[] arg0) {
                    this.checkClosed();
                    delegatee.print(arg0);
                }

                @Override
                public void print(final double arg0) {
                    this.checkClosed();
                    delegatee.print(arg0);
                }

                @Override
                public void print(final float arg0) {
                    this.checkClosed();
                    delegatee.print(arg0);
                }

                @Override
                public void print(final int arg0) {
                    this.checkClosed();
                    delegatee.print(arg0);
                }

                @Override
                public void print(final long arg0) {
                    this.checkClosed();
                    delegatee.print(arg0);
                }

                @Override
                public void print(final Object arg0) {
                    this.checkClosed();
                    delegatee.print(arg0);
                }

                @Override
                public void print(final String arg0) {
                    this.checkClosed();
                    delegatee.print(arg0);
                }

                @Override
                public PrintWriter printf(final Locale arg0, final String arg1, final Object... arg2) {
                    this.checkClosed();
                    return delegatee.printf(arg0, arg1, arg2);
                }

                @Override
                public PrintWriter printf(final String arg0, final Object... arg1) {
                    this.checkClosed();
                    return delegatee.printf(arg0, arg1);
                }

                @Override
                public void println() {
                    this.checkClosed();
                    delegatee.println();
                }

                @Override
                public void println(final boolean arg0) {
                    this.checkClosed();
                    delegatee.println(arg0);
                }

                @Override
                public void println(final char arg0) {
                    this.checkClosed();
                    delegatee.println(arg0);
                }

                @Override
                public void println(final char[] arg0) {
                    this.checkClosed();
                    delegatee.println(arg0);
                }

                @Override
                public void println(final double arg0) {
                    this.checkClosed();
                    delegatee.println(arg0);
                }

                @Override
                public void println(final float arg0) {
                    this.checkClosed();
                    delegatee.println(arg0);
                }

                @Override
                public void println(final int arg0) {
                    this.checkClosed();
                    delegatee.println(arg0);
                }

                @Override
                public void println(final long arg0) {
                    this.checkClosed();
                    delegatee.println(arg0);
                }

                @Override
                public void println(final Object arg0) {
                    this.checkClosed();
                    delegatee.println(arg0);
                }

                @Override
                public void println(final String arg0) {
                    this.checkClosed();
                    delegatee.println(arg0);
                }

                @Override
                public void write(final char[] arg0, final int arg1, final int arg2) {
                    this.checkClosed();
                    delegatee.write(arg0, arg1, arg2);
                }

                @Override
                public void write(final char[] arg0) {
                    this.checkClosed();
                    delegatee.write(arg0);
                }

                @Override
                public void write(final int arg0) {
                    this.checkClosed();
                    delegatee.write(arg0);
                }

                @Override
                public void write(final String arg0, final int arg1, final int arg2) {
                    this.checkClosed();
                    delegatee.write(arg0, arg1, arg2);
                }

                @Override
                public void write(final String arg0) {
                    this.checkClosed();
                    delegatee.write(arg0);
                }
            };
        }
        return result;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        final ServletOutputStream outputStream = super.getOutputStream();
        if (firstSlingResponse) {
            return new DelegatingServletOutputStream(outputStream) {
                @Override
                public void flush() throws IOException {
                    initFlusherStacktrace();
                    super.flush();
                }
            };
        }
        return outputStream;
    }

    private void checkCommitted() {
        if (isCommitted()) {
            throw new IllegalStateException("Response has already been committed");
        }
    }

    private String makeAbsolutePath(String path) {
        if (path.startsWith("/")) {
            return path;
        }

        String base = getRequestData().getContentData().getResource().getPath();
        int lastSlash = base.lastIndexOf('/');
        if (lastSlash >= 0) {
            path = base.substring(0, lastSlash + 1) + path;
        } else {
            path = "/" + path;
        }

        return path;
    }

    private String map(String url) {
        return getRequestData().getResourceResolver().map(getRequestData().getServletRequest(), url);
    }

    private String removeContextPath(final String path) {
        final String contextPath =
                this.getRequestData().getSlingRequest().getContextPath().concat("/");
        if (contextPath.length() > 1 && path.startsWith(contextPath)) {
            return path.substring(contextPath.length() - 1);
        }
        return path;
    }

    /**
     * A simple implementation of ServletOutputStream, that delegates all methods
     * to a delegate instance. It separates the "boring" delegation logic from any
     * added logic in order to (hopefully) make the code more readable.
     */
    private abstract class DelegatingServletOutputStream extends ServletOutputStream {

        final ServletOutputStream delegate;

        DelegatingServletOutputStream(final ServletOutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void print(final String s) throws IOException {
            delegate.print(s);
        }

        @Override
        public void print(final boolean b) throws IOException {
            delegate.print(b);
        }

        @Override
        public void print(final char c) throws IOException {
            delegate.print(c);
        }

        @Override
        public void print(final int i) throws IOException {
            delegate.print(i);
        }

        @Override
        public void print(final long l) throws IOException {
            delegate.print(l);
        }

        @Override
        public void print(final float f) throws IOException {
            delegate.print(f);
        }

        @Override
        public void print(final double d) throws IOException {
            delegate.print(d);
        }

        @Override
        public void println() throws IOException {
            delegate.println();
        }

        @Override
        public void println(final String s) throws IOException {
            delegate.println(s);
        }

        @Override
        public void println(final boolean b) throws IOException {
            delegate.println(b);
        }

        @Override
        public void println(final char c) throws IOException {
            delegate.println(c);
        }

        @Override
        public void println(final int i) throws IOException {
            delegate.println(i);
        }

        @Override
        public void println(final long l) throws IOException {
            delegate.println(l);
        }

        @Override
        public void println(final float f) throws IOException {
            delegate.println(f);
        }

        @Override
        public void println(final double d) throws IOException {
            delegate.println(d);
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setWriteListener(final WriteListener writeListener) {
            delegate.setWriteListener(writeListener);
        }

        @Override
        public void write(final int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(final byte[] b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
