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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;

import jakarta.servlet.http.Part;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.RequestContext;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression tests for SLING-13364: a stream turning malformed
 * mid-body must surface as an error from {@link RequestPartsIterator}
 * instead of silently truncating the part sequence.
 * <p>
 * Also covers the fix for the streamed upload mode bypassing the configured
 * multipart limits: the configured {@code sizeMax}/{@code fileSizeMax}/
 * {@code fileCountMax} must be enforced on the streamed path exactly as they
 * are on the buffered path, and {@link Part#getSize()} must report the size
 * as unknown ({@code -1}) rather than falsely claiming an empty part.
 */
public class RequestPartsIteratorTest {

    private static final String BOUNDARY = "AaB03x";

    private static final String MULTI_PART_BODY = "--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=\"file1\"; filename=\"a.txt\"\r\n"
            + "Content-Type: text/plain\r\n"
            + "\r\n"
            + "hello\r\n"
            + "--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=\"file2\"; filename=\"b.txt\"\r\n"
            + "Content-Type: text/plain\r\n"
            + "\r\n"
            + "world\r\n"
            + "--" + BOUNDARY + "--\r\n";

    @Test(expected = SlingParameterParseException.class)
    public void testHasNextIsRejectedOnFileUploadException() throws Exception {
        final RequestPartsIterator iterator = newIterator();
        final FileItemIterator delegate = mock(FileItemIterator.class);
        when(delegate.hasNext()).thenThrow(new FileUploadException("boom"));
        injectDelegate(iterator, delegate);

        iterator.hasNext();
    }

    @Test(expected = SlingParameterParseException.class)
    public void testHasNextIsRejectedOnIOException() throws Exception {
        final RequestPartsIterator iterator = newIterator();
        final FileItemIterator delegate = mock(FileItemIterator.class);
        when(delegate.hasNext()).thenThrow(new IOException("connection reset"));
        injectDelegate(iterator, delegate);

        iterator.hasNext();
    }

    @Test(expected = SlingParameterParseException.class)
    public void testNextIsRejectedOnFileUploadException() throws Exception {
        final RequestPartsIterator iterator = newIterator();
        final FileItemIterator delegate = mock(FileItemIterator.class);
        when(delegate.next()).thenThrow(new FileUploadException("boom"));
        injectDelegate(iterator, delegate);

        iterator.next();
    }

    @Test(expected = SlingParameterParseException.class)
    public void testNextIsRejectedOnIOException() throws Exception {
        final RequestPartsIterator iterator = newIterator();
        final FileItemIterator delegate = mock(FileItemIterator.class);
        when(delegate.next()).thenThrow(new IOException("connection reset"));
        injectDelegate(iterator, delegate);

        iterator.next();
    }

    @Test
    public void testHasNextPassesThroughCleanEndOfStream() throws Exception {
        // sanity check: a well-formed, empty multipart body must not trigger
        // the reject-on-error path at all
        final RequestPartsIterator iterator = newIterator();

        assertFalse(iterator.hasNext());
    }

    /**
     * Builds a real {@link RequestPartsIterator} over a trivial, well-formed,
     * empty multipart body so that construction itself succeeds; the
     * commons-fileupload delegate is then swapped out via reflection to
     * simulate a stream failing mid-read, which is otherwise very hard to
     * trigger deterministically with a hand-crafted byte stream.
     */
    private static RequestPartsIterator newIterator() throws FileUploadException, IOException {
        final String boundary = "X";
        final String body = "--" + boundary + "--\r\n";
        final RequestContext context = mock(RequestContext.class);
        when(context.getContentType()).thenReturn("multipart/form-data; boundary=" + boundary);
        when(context.getCharacterEncoding()).thenReturn("UTF-8");
        when(context.getContentLength()).thenReturn(body.length());
        when(context.getInputStream()).thenReturn(new ByteArrayInputStream(body.getBytes(Util.ENCODING_DIRECT)));
        return new RequestPartsIterator(context, -1, -1, 50);
    }

    private static void injectDelegate(final RequestPartsIterator iterator, final FileItemIterator delegate)
            throws Exception {
        final Field field = RequestPartsIterator.class.getDeclaredField("itemIterator");
        field.setAccessible(true);
        field.set(iterator, delegate);
    }

    private static RequestContext multiPartContext() throws IOException {
        final byte[] body = MULTI_PART_BODY.getBytes(Util.ENCODING_DIRECT);
        final RequestContext context = mock(RequestContext.class);
        when(context.getContentType()).thenReturn("multipart/form-data; boundary=" + BOUNDARY);
        when(context.getCharacterEncoding()).thenReturn("UTF-8");
        when(context.getContentLength()).thenReturn(body.length);
        when(context.getInputStream()).thenReturn(new ByteArrayInputStream(body));
        return context;
    }

    @Test
    public void testAllPartsIteratedWithoutLimits() throws Exception {
        final RequestPartsIterator it = new RequestPartsIterator(multiPartContext(), -1, -1, 50);
        assertTrue(it.hasNext());
        final Part first = it.next();
        assertNotNull(first);
        assertEquals("file1", first.getName());
        assertTrue(it.hasNext());
        final Part second = it.next();
        assertNotNull(second);
        assertEquals("file2", second.getName());
        assertFalse(it.hasNext());
    }

    @Test
    public void testFileCountMaxEnforced() throws Exception {
        // the configured count must be enforced even though commons-fileupload's
        // streaming API does not check fileCountMax itself
        final RequestPartsIterator it = new RequestPartsIterator(multiPartContext(), -1, -1, 1);
        assertTrue(it.hasNext());
        assertNotNull(it.next());
        // the second part exceeds the configured count limit
        assertFalse(it.hasNext());
    }

    @Test
    public void testSizeMaxEnforced() throws Exception {
        try {
            new RequestPartsIterator(multiPartContext(), 10, -1, 50);
            fail("Expected the configured request size limit to be enforced");
        } catch (FileUploadException expected) {
            // the request exceeds the configured maximum request size
        }
    }

    @Test
    public void testGetSizeIsUnknownNotZero() throws Exception {
        final RequestPartsIterator it = new RequestPartsIterator(multiPartContext(), -1, -1, 50);
        assertTrue(it.hasNext());
        final Part part = it.next();
        assertNotNull(part);
        // the size of a streamed part is unknown: it must not read as an empty
        // part to size-limit checks of downstream consumers
        assertEquals(-1, part.getSize());
    }
}
