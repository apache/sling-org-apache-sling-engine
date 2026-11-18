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
import java.util.List;
import java.util.NoSuchElementException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class RequestPartsIteratorTest {

    private RequestPartsIterator iterator;
    private HttpServletRequest mockRequest;

    @Before
    public void before() {
        mockRequest = Mockito.mock(HttpServletRequest.class);
        iterator = new RequestPartsIterator(mockRequest, 5);
    }

    private Part mockParts() throws IOException, ServletException {
        Part part1 = Mockito.mock(Part.class);
        Mockito.doReturn(List.of(part1)).when(mockRequest).getParts();
        return part1;
    }

    @Test
    public void testHasNextWithNoElements() {
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testHasNextWithElements() throws IOException, ServletException {
        mockParts();

        assertTrue(iterator.hasNext());
        iterator.next();
        assertFalse(iterator.hasNext());
        Mockito.verify(mockRequest, Mockito.times(1)).getParts();
    }

    @Test
    public void testHasNextIsRejectedOnServletException() throws IOException, ServletException {
        assertParseFailure(new ServletException("Missing multipart boundary"), false);
    }

    @Test
    public void testHasNextIsRejectedOnIOException() throws IOException, ServletException {
        assertParseFailure(new IOException("Connection reset"), false);
    }

    @Test
    public void testNextIsRejectedOnServletException() throws IOException, ServletException {
        assertParseFailure(new ServletException("Missing multipart boundary"), true);
    }

    @Test
    public void testNextIsRejectedOnIOException() throws IOException, ServletException {
        assertParseFailure(new IOException("Connection reset"), true);
    }

    @Test
    public void testContainerSizeLimitIsRejected() throws IOException, ServletException {
        assertParseFailure(new IllegalStateException("Request exceeds configured multipart size"), false);
    }

    private void assertParseFailure(Exception cause, boolean next) throws IOException, ServletException {
        Mockito.doThrow(cause).when(mockRequest).getParts();
        final SlingParameterParseException failure = next
                ? assertThrows(SlingParameterParseException.class, iterator::next)
                : assertThrows(SlingParameterParseException.class, iterator::hasNext);
        assertSame(cause, failure.getCause());
        assertSame(failure, assertThrows(SlingParameterParseException.class, iterator::hasNext));
        assertSame(failure, assertThrows(SlingParameterParseException.class, iterator::next));
        Mockito.verify(mockRequest, Mockito.times(1)).getParts();
    }

    @Test
    public void testHasNextWithTooManyFiles() throws IOException, ServletException {
        mockFiles(6);
        final SlingParameterParseException failure =
                assertThrows(SlingParameterParseException.class, iterator::hasNext);
        assertTrue(failure.getCause() instanceof FileCountLimitExceededException);
    }

    @Test
    public void testFileCountMaxEnforcedWithoutHasNext() throws IOException, ServletException {
        mockFiles(6);
        assertThrows(SlingParameterParseException.class, iterator::next);
    }

    @Test
    public void testFileCountExactlyAtBoundary() throws IOException, ServletException {
        mockFiles(5);
        for (int i = 0; i < 5; i++) {
            assertEquals("file" + i + ".txt", iterator.next().getSubmittedFileName());
        }
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    public void testZeroFileCountMaxRejectsNext() throws IOException, ServletException {
        mockFiles(1);
        iterator = new RequestPartsIterator(mockRequest, 0);
        assertThrows(SlingParameterParseException.class, iterator::next);
    }

    @Test
    public void testUnlimitedFileCountAllowsNextWithoutHasNext() throws IOException, ServletException {
        mockFiles(6);
        iterator = new RequestPartsIterator(mockRequest, -1);
        for (int i = 0; i < 6; i++) {
            assertEquals("file" + i + ".txt", iterator.next().getSubmittedFileName());
        }
        assertThrows(NoSuchElementException.class, iterator::next);
        assertFalse(iterator.hasNext());
    }

    private void mockFiles(int count) throws IOException, ServletException {
        List<Part> parts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Part mockPart = Mockito.mock(Part.class);
            Mockito.doReturn(String.format("file%d.txt", i)).when(mockPart).getSubmittedFileName();
            parts.add(mockPart);
        }
        Mockito.doReturn(parts).when(mockRequest).getParts();
    }

    @Test
    public void testNextWithNoElements() {
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    public void testNextWithElements() throws IOException, ServletException {
        mockParts();
        final Part next1 = iterator.next();
        assertTrue(next1 instanceof javax.servlet.http.Part);
        assertTrue(next1 instanceof jakarta.servlet.http.Part);
    }

    @Test
    public void testRemove() {
        assertThrows(UnsupportedOperationException.class, iterator::remove);
    }

    @Test
    public void testStreamedRequestPartWrapper() throws IOException, ServletException {
        final Part part1 = mockParts();

        InputStream mockInputStream1 = Mockito.mock(InputStream.class);
        Mockito.doReturn(mockInputStream1).when(part1).getInputStream();
        Mockito.doReturn("text/test1").when(part1).getContentType();
        Mockito.doReturn("name1").when(part1).getName();
        Mockito.doReturn(11L).when(part1).getSize();
        Mockito.doReturn("value1").when(part1).getHeader("header1");
        Mockito.doReturn(List.of("value1")).when(part1).getHeaders("header1");
        Mockito.doReturn(List.of("header1")).when(part1).getHeaderNames();
        Mockito.doReturn("file1.txt").when(part1).getSubmittedFileName();

        final Part next1 = iterator.next();

        assertEquals(part1.getInputStream(), next1.getInputStream());
        assertEquals(part1.getContentType(), next1.getContentType());
        assertEquals(part1.getName(), next1.getName());
        assertEquals(11L, next1.getSize());
        assertEquals(part1.getHeader("header1"), next1.getHeader("header1"));
        assertEquals(part1.getHeaders("header1"), next1.getHeaders("header1"));
        assertEquals(part1.getHeaderNames(), next1.getHeaderNames());
        assertEquals(part1.getSubmittedFileName(), next1.getSubmittedFileName());

        assertThrows(UnsupportedOperationException.class, () -> next1.write("output1"));
        next1.delete();
        Mockito.verify(part1).delete();
    }
}
