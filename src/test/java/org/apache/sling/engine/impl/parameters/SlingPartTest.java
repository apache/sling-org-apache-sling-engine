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
import java.util.List;

import jakarta.servlet.http.Part;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;
import static org.mockito.Mockito.times;

/**
 *
 */
public class SlingPartTest {

    private Part part;
    private SlingPart slingPart;
    private MultipartRequestParameter mpr;

    @Before
    public void before() {
        part = Mockito.mock(Part.class);
        mpr = new MultipartRequestParameter(part, null);
        slingPart = new SlingPart(mpr);
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.SlingPart#getInputStream()}.
     */
    @Test
    public void testGetInputStream() throws IOException {
        InputStream mockInputStream1 = Mockito.mock(InputStream.class);
        Mockito.doReturn(mockInputStream1).when(part).getInputStream();
        assertEquals(mockInputStream1, slingPart.getInputStream());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.SlingPart#getContentType()}.
     */
    @Test
    public void testGetContentType() {
        String mockContentType = "text/plain";
        Mockito.doReturn(mockContentType).when(part).getContentType();
        assertEquals(mockContentType, slingPart.getContentType());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.SlingPart#getName()}.
     */
    @Test
    public void testGetName() {
        String mockName = "name1";
        Mockito.doReturn(mockName).when(part).getName();
        assertEquals(mockName, slingPart.getName());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.SlingPart#getSize()}.
     */
    @Test
    public void testGetSize() {
        long mockSize = 11L;
        Mockito.doReturn(mockSize).when(part).getSize();
        assertEquals(mockSize, slingPart.getSize());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.SlingPart#write(java.lang.String)}.
     */
    @Test
    public void testWrite() {
        assertThrows(IOException.class, () -> slingPart.write("file.txt"));
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.SlingPart#delete()}.
     */
    @Test
    public void testDelete() throws IOException {
        slingPart.delete();
        Mockito.verify(part, times(1)).delete();
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.SlingPart#getHeader(java.lang.String)}.
     */
    @Test
    public void testGetHeader() {
        String mockHeaderValue = "value1";
        Mockito.doReturn(mockHeaderValue).when(part).getHeader("header1");
        assertEquals(mockHeaderValue, slingPart.getHeader("header1"));
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.SlingPart#getHeaders(java.lang.String)}.
     */
    @Test
    public void testGetHeaders() {
        List<String> mockHeaderValues = List.of("value1", "value2");
        Mockito.doReturn(mockHeaderValues).when(part).getHeaders("header1");
        assertEquals(mockHeaderValues, slingPart.getHeaders("header1"));
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.SlingPart#getHeaderNames()}.
     */
    @Test
    public void testGetHeaderNames() {
        List<String> mockHeaderNames = List.of("header1", "header2");
        Mockito.doReturn(mockHeaderNames).when(part).getHeaderNames();
        assertEquals(mockHeaderNames, slingPart.getHeaderNames());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.SlingPart#getSubmittedFileName()}.
     */
    @Test
    public void testGetSubmittedFileName() {
        String mockFileName = "filename1.txt";
        Mockito.doReturn(mockFileName).when(part).getSubmittedFileName();
        assertEquals(mockFileName, slingPart.getSubmittedFileName());
    }
}
