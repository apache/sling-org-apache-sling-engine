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

import static org.junit.Assert.*;

/**
 *
 */
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

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.RequestPartsIterator#hasNext()}.
     */
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
    }

    @Test
    public void testHasNextWithCaughtException() throws IOException, ServletException {
        Mockito.doThrow(ServletException.class).when(mockRequest).getParts();
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testHasNextWithTooManyFiles() throws IOException, ServletException {
        List<Part> parts = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Part mockPart = Mockito.mock(Part.class);
            Mockito.doReturn(String.format("file%d.txt", i)).when(mockPart).getSubmittedFileName();
            parts.add(mockPart);
        }

        Mockito.doReturn(parts).when(mockRequest).getParts();
        assertFalse(iterator.hasNext());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.RequestPartsIterator#next()}.
     */
    @Test
    public void testNextWithNoElements() {
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    public void testNextWithElements() throws IOException, ServletException {
        mockParts();

        final Part next1 = iterator.next();
        // the part implements both interfaces for backward compatibility
        assertTrue(next1 instanceof javax.servlet.http.Part);
        assertTrue(next1 instanceof jakarta.servlet.http.Part);
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.RequestPartsIterator#remove()}.
     */
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
        assertEquals(part1.getSize(), next1.getSize());
        assertEquals(part1.getHeader("header"), next1.getHeader("header"));
        assertEquals(part1.getHeaders("header"), next1.getHeaders("header"));
        assertEquals(part1.getHeaderNames(), next1.getHeaderNames());
        assertEquals(part1.getSubmittedFileName(), next1.getSubmittedFileName());

        assertThrows(UnsupportedOperationException.class, () -> next1.write("output1"));
        // should do nothing
        next1.delete();
    }
}
