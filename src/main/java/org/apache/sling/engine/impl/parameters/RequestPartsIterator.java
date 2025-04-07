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
import java.util.Iterator;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains a Lazy iterator of Parts from the request stream loaded as the request is streamed using the Commons FileUpload API.
 */
public class RequestPartsIterator implements Iterator<Part> {
    private static final Logger LOG = LoggerFactory.getLogger(RequestPartsIterator.class);

    /** The CommonsFile Upload streaming API iterator */
    private final Iterator<Part> partsIterator;

    /**
     * Create and initialse the iterator using the request. The request must be fresh. Headers can have been read but the stream
     * must not have been parsed.
     * @param servletRequest the request
     * @throws IOException when there is a problem reading the request.
     * @throws ServletException when there is a problem parsing the request.
     */
    public RequestPartsIterator(HttpServletRequest servletRequest) throws IOException, ServletException {
        Collection<Part> parts = servletRequest.getParts();
        // TODO: needed?
        long fileCount =
                parts.stream().filter(p -> p.getSubmittedFileName() != null).count();
        if (fileCount > 50) {
            throw new ServletException("Too many files uploaded. Limit is 50.");
        }
        this.partsIterator = parts.iterator();
    }

    @Override
    public boolean hasNext() {
        return this.partsIterator.hasNext();
    }

    @Override
    public Part next() {
        try {
            return new StreamedRequestPart(partsIterator.next());
        } catch (IOException e) {
            LOG.error("next Item failed cause:" + e.getMessage(), e);
        }
        return null;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Remove is not supported on a request stream.");
    }

    /**
     * Internal implementation of the Part API from Servlet 3 wrapping the Commons File Upload FIleItemStream object.
     */
    private static class StreamedRequestPart implements Part {
        private final Part part;
        private final InputStream inputStream;

        public StreamedRequestPart(Part part) throws IOException {
            this.part = part;
            inputStream = part.getInputStream();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return inputStream;
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
            return 0;
        }

        @Override
        public void write(String s) throws IOException {
            throw new UnsupportedOperationException(
                    "Writing parts directly to disk is not supported by this implementation, use getInputStream instead");
        }

        @Override
        public void delete() throws IOException {
            // no underlying storage is used, so nothing to delete.
        }

        @Override
        public String getHeader(String headerName) {
            return this.part.getHeader(headerName);
        }

        @Override
        public Collection<String> getHeaders(String headerName) {
            return this.part.getHeaders(headerName);
        }

        @Override
        public Collection<String> getHeaderNames() {
            return this.part.getHeaderNames();
        }

        @Override
        public String getSubmittedFileName() {
            return this.part.getSubmittedFileName();
        }
    }
}
