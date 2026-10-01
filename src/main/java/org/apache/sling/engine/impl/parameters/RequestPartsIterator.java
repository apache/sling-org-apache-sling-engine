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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import jakarta.servlet.http.Part;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains a Lazy iterator of Parts from the request stream loaded as the request is streamed using the Commons FileUpload API.
 */
public class RequestPartsIterator implements Iterator<Part> {
    private static final Logger LOG = LoggerFactory.getLogger(RequestPartsIterator.class);

    /** The CommonsFile Upload streaming API iterator */
    private final FileItemIterator itemIterator;

    /** The maximum number of parts allowed in the request, -1 for unlimited */
    private final long fileCountMax;

    /** The number of parts returned so far */
    private long partCount;

    /**
     * Create and initialse the iterator using the request. The request must be fresh. Headers can have been read but the stream
     * must not have been parsed.
     * <p>
     * The configured multipart limits are enforced on the streamed request just
     * as they are for the buffered (non-streamed) code path: a client-selected
     * upload mode must not bypass the operator-configured controls.
     *
     * @param context the request context
     * @param sizeMax the maximum allowed size of the complete request (-1 for unlimited)
     * @param fileSizeMax the maximum allowed size of a single file/part (-1 for unlimited)
     * @param fileCountMax the maximum allowed number of files/parts in the request
     * @throws IOException when there is a problem reading the request.
     * @throws FileUploadException when there is a problem parsing the request.
     */
    public RequestPartsIterator(
            final RequestContext context, final long sizeMax, final long fileSizeMax, final long fileCountMax)
            throws FileUploadException, IOException {
        this.fileCountMax = fileCountMax;
        FileUpload upload = new FileUpload();
        upload.setSizeMax(sizeMax);
        upload.setFileSizeMax(fileSizeMax);
        upload.setFileCountMax(fileCountMax);
        itemIterator = upload.getItemIterator(context);
    }

    @Override
    public boolean hasNext() {
        try {
            // enforce the part count limit here as well, as the streaming API of
            // commons-fileupload 1.x does not check fileCountMax itself
            if (fileCountMax >= 0 && partCount >= fileCountMax) {
                if (itemIterator.hasNext()) {
                    LOG.error(
                            "hasNext: the request contains more than the allowed number of {} parts, further parts are not processed",
                            fileCountMax);
                }
                return false;
            }
            return itemIterator.hasNext();
        } catch (final FileUploadException | IOException e) {
            LOG.error("hasNext Item failed cause:" + e.getMessage(), e);
            throw new SlingParameterParseException("Error reading next part from the request stream", e);
        }
    }

    @Override
    public Part next() {
        try {
            partCount++;
            return new StreamedRequestPart(itemIterator.next());
        } catch (final FileUploadException | IOException e) {
            LOG.error("next Item failed cause:" + e.getMessage(), e);
            throw new SlingParameterParseException("Error reading next part from the request stream", e);
        }
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Remove is not supported on a request stream.");
    }

    /**
     * Internal implementation of the Part API from Servlet 3 wrapping the Commons File Upload FIleItemStream object.
     */
    private static class StreamedRequestPart implements Part, javax.servlet.http.Part {
        private final FileItemStream fileItem;
        private final InputStream inputStream;

        public StreamedRequestPart(final FileItemStream fileItem) throws IOException {
            this.fileItem = fileItem;
            inputStream = fileItem.openStream();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return inputStream;
        }

        @Override
        public String getContentType() {
            return fileItem.getContentType();
        }

        @Override
        public String getName() {
            return fileItem.getFieldName();
        }

        @Override
        public long getSize() {
            // The part is streamed, so its size is not known in advance. Return
            // -1 (unknown) instead of 0 so that consumers enforcing size limits
            // via getSize() reject the part instead of accepting arbitrarily
            // large parts as empty.
            return -1;
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
            return fileItem.getHeaders().getHeader(headerName);
        }

        @Override
        public Collection<String> getHeaders(String headerName) {
            return toCollection(fileItem.getHeaders().getHeaders(headerName));
        }

        @Override
        public Collection<String> getHeaderNames() {
            return toCollection(fileItem.getHeaders().getHeaderNames());
        }

        @Override
        public String getSubmittedFileName() {
            return fileItem.getName();
        }

        private <T> Collection<T> toCollection(Iterator<T> i) {
            if (i == null) {
                return Collections.emptyList();
            } else {
                List<T> c = new ArrayList<T>();
                while (i.hasNext()) {
                    c.add(i.next());
                }
                return c;
            }
        }
    }
}
