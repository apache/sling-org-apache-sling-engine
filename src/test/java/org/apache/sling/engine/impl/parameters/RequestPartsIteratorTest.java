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

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.RequestContext;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression tests for SLING-13364: a stream turning malformed
 * mid-body must surface as an error from {@link RequestPartsIterator}
 * instead of silently truncating the part sequence.
 */
public class RequestPartsIteratorTest {

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
        return new RequestPartsIterator(context);
    }

    private static void injectDelegate(final RequestPartsIterator iterator, final FileItemIterator delegate)
            throws Exception {
        final Field field = RequestPartsIterator.class.getDeclaredField("itemIterator");
        field.setAccessible(true);
        field.set(iterator, delegate);
    }
}
