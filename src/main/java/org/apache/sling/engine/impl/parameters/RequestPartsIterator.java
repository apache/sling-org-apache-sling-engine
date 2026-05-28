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
package org.apache.sling.engine.impl.parameters;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains a Lazy iterator of Parts from the request stream loaded as the request is streamed
 */
public class RequestPartsIterator implements Iterator<Part> {
    private final Logger log = LoggerFactory.getLogger(getClass());

    /** The Parts iterator */
    private Iterator<Part> itemIterator = null;

    /** supplier to retrieve the parts when needed */
    private final HttpServletRequest request;

    private final long maxFileCount;

    /**
     * Create and initialize the iterator using the request.
     *
     * @param request the current request
     */
    public RequestPartsIterator(final HttpServletRequest request, long maxFileCount) {
        this.request = request;
        this.maxFileCount = maxFileCount;
    }

    @Override
    public boolean hasNext() {
        // Use a lazy data iterator creation the first time it is needed to ensure
        // the container doesn't parse the uploaded files until necessary
        if (itemIterator == null) {
            Collection<Part> parts;
            try {
                parts = getPartsAndCheckFileCount();
            } catch (ServletException | IOException e) {
                log.error("Error parsing multipart streamed request", e);
                parts = Collections.emptyList();
            }

            itemIterator = parts.iterator();
        }

        return itemIterator.hasNext();
    }

    /**
     * Get the parts from the request and check if the number of files exceeds the allowed number
     * @return the collected parts
     */
    private Collection<Part> getPartsAndCheckFileCount() throws IOException, ServletException {
        Collection<Part> parts = request.getParts();

        long filePartCount =
                parts.stream().filter(p -> p.getSubmittedFileName() != null).count();
        if (filePartCount > maxFileCount) {
            throw new FileCountLimitExceededException("Request exceeds maximum file count", maxFileCount);
        }
        return parts;
    }

    @Override
    public Part next() {
        if (!hasNext()) {
            throw new java.util.NoSuchElementException();
        }
        return new StreamedRequestPart(itemIterator.next());
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Remove is not supported on a request stream.");
    }

    /**
     * Internal implementation of the Part API from Servlet 3 wrapping the jakarta Part object.
     */
    private static class StreamedRequestPart implements Part, javax.servlet.http.Part {
        private final Part part;

        public StreamedRequestPart(final Part filePart) {
            this.part = filePart;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return part.getInputStream();
        }

        @Override
        public String getContentType() {
            return part.getContentType();
        }

        @Override
        public String getName() {
            return part.getName();
        }

        @Override
        public long getSize() {
            return part.getSize();
        }

        @Override
        public void write(String s) throws IOException {
            throw new UnsupportedOperationException(
                    "Writing parts directly to disk is not supported by this implementation, use getInputStream instead");
        }

        @Override
        public void delete() throws IOException {
            part.delete();
        }

        @Override
        public String getHeader(String headerName) {
            return part.getHeader(headerName);
        }

        @Override
        public Collection<String> getHeaders(String headerName) {
            return part.getHeaders(headerName);
        }

        @Override
        public Collection<String> getHeaderNames() {
            return part.getHeaderNames();
        }

        @Override
        public String getSubmittedFileName() {
            return part.getSubmittedFileName();
        }
    }
}
