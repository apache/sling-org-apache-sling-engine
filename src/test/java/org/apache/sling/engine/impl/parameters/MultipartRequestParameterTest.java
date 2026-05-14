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
import java.io.UncheckedIOException;

import jakarta.servlet.http.Part;
import org.junit.Before;
import org.junit.Test;
import org.junit.Test.None;
import org.mockito.Mockito;

import static org.junit.Assert.*;

/**
 *
 */
public class MultipartRequestParameterTest {

    private MultipartRequestParameter param;
    private Part part1;

    @Before
    public void before() {
        part1 = Mockito.mock(Part.class);
        param = new MultipartRequestParameter(part1);
    }
    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.MultipartRequestParameter#setEncoding(java.lang.String)}.
     */
    @Test
    public void testSetEncoding() {
        param.setEncoding("my/encoding1");
        assertEquals("my/encoding1", param.getEncoding());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.MultipartRequestParameter#dispose()}.
     */
    @Test(expected = None.class)
    public void testDispose() throws IOException {
        param.dispose();
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.MultipartRequestParameter#getPart()}.
     */
    @Test
    public void testGetPart() {
        assertEquals(part1, param.getPart());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.MultipartRequestParameter#get()}.
     */
    @Test
    public void testGet() throws IOException {
        mockPartInputStream();

        assertArrayEquals("hi".getBytes(), param.get());
    }

    @Test
    public void testGetWithIOException() throws IOException {
        InputStream inputStream = mockPartInputStream();
        Mockito.doThrow(IOException.class).when(inputStream).readAllBytes();

        assertThrows(UncheckedIOException.class, param::get);
    }

    private InputStream mockPartInputStream() throws IOException {
        InputStream mockInputStream = Mockito.mock(InputStream.class);
        Mockito.doReturn("hi".getBytes()).when(mockInputStream).readAllBytes();
        Mockito.doReturn(mockInputStream).when(part1).getInputStream();
        return mockInputStream;
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.MultipartRequestParameter#getContentType()}.
     */
    @Test
    public void testGetContentType() {
        assertNull(param.getContentType());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.MultipartRequestParameter#getInputStream()}.
     */
    @Test
    public void testGetInputStream() throws IOException {
        InputStream mockInputStream = mockPartInputStream();

        assertEquals(mockInputStream, param.getInputStream());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.MultipartRequestParameter#getFileName()}.
     */
    @Test
    public void testGetFileName() {
        assertNull(param.getFileName());

        // simulate an uploaded file field
        Mockito.doReturn("file1.txt").when(part1).getSubmittedFileName();

        assertEquals("file1.txt", param.getFileName());
        // again for the cached value
        assertEquals("file1.txt", param.getFileName());

        // with some invalid encoding
        param.setEncoding("invalid1");
        assertEquals("file1.txt", param.getFileName());

        // with some valid encoding
        param.setEncoding("UTF-8");
        assertEquals("file1.txt", param.getFileName());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.MultipartRequestParameter#getSize()}.
     */
    @Test
    public void testGetSize() {
        assertEquals(0L, param.getSize());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.MultipartRequestParameter#getString()}.
     */
    @Test
    public void testGetString() throws IOException {
        mockPartInputStream();
        assertNotNull(param.getString());
        // one more the for the cached value
        assertNotNull(param.getString());

        // with some invalid encoding
        param.setEncoding("invalid1");
        assertNotNull(param.getString());

        // with some valid encoding
        param.setEncoding("UTF-8");
        assertNotNull(param.getString());

        // simulate an uploaded file field
        Mockito.doReturn("file1.txt").when(part1).getSubmittedFileName();
        assertNotNull(param.getString());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.MultipartRequestParameter#getString(java.lang.String)}.
     */
    @Test
    public void testGetStringString() throws IOException {
        mockPartInputStream();
        assertNotNull(param.getString("UTF-8"));
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.MultipartRequestParameter#isFormField()}.
     */
    @Test
    public void testIsFormField() {
        assertTrue(param.isFormField());

        // simulate an uploaded file field
        Mockito.doReturn("file1.txt").when(part1).getSubmittedFileName();
        assertFalse(param.isFormField());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.MultipartRequestParameter#toString()}.
     */
    @Test
    public void testToString() throws IOException {
        mockPartInputStream();
        assertNotNull(param.toString());

        // simulate an uploaded file field
        Mockito.doReturn("file1.txt").when(part1).getSubmittedFileName();
        assertNotNull(param.toString());
    }
}
