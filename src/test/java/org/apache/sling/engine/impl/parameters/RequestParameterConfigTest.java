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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.sling.settings.SlingSettingsService;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 *
 */
public class RequestParameterConfigTest {
    @Rule
    public final OsgiContext osgiContext = new OsgiContext();

    private RequestParameterConfig config;

    private String tmpDir;

    @Before
    public void before() {
        // Satisfy mandatory reference to SlingSettingsService
        SlingSettingsService mockSettingSvc =
                osgiContext.registerService(SlingSettingsService.class, Mockito.mock(SlingSettingsService.class));
        tmpDir = System.getProperty("java.io.tmpdir");
        Mockito.doReturn(tmpDir).when(mockSettingSvc).getSlingHomePath();

        config = osgiContext.registerInjectActivateService(
                RequestParameterConfig.class, Map.of("sling.default.max.parameters", -1L, "request.max", 1000L));
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.RequestParameterConfig#getConfig()}.
     */
    @Test
    public void testGetConfig() {
        assertNotNull(config.getConfig());
    }

    /**
     * Test method for {@link org.apache.sling.engine.impl.parameters.RequestParameterConfig#resolveLocation()}.
     */
    @Test
    public void testResolveLocationWithNull() {
        assertNull(config.resolveLocation());
    }

    @Test
    public void testResolveLocationWithRelativePath() {
        config = osgiContext.registerInjectActivateService(
                RequestParameterConfig.class, Map.of("file.location", "temp"));
        assertNotNull(config.resolveLocation());
    }

    @Test
    public void testResolveLocationWithAbsoluteFolderPath() throws IOException {
        final Path tempFile = Files.createTempDirectory("test");
        config = osgiContext.registerInjectActivateService(
                RequestParameterConfig.class,
                Map.of("file.location", tempFile.toFile().getAbsolutePath()));
        assertNotNull(config.resolveLocation());
    }

    @Test
    public void testResolveLocationWithAbsoluteFilePath() throws IOException {
        final Path tempFile = Files.createTempFile("test", "test");
        config = osgiContext.registerInjectActivateService(
                RequestParameterConfig.class,
                Map.of("file.location", tempFile.toFile().getAbsolutePath()));
        assertNull(config.resolveLocation());
    }

    @Test
    public void testResolveLocationWithNotExistingPath() throws IOException {
        Path tempFile = Files.createTempFile("test", "test");
        tempFile = tempFile.resolve("child1");
        config = osgiContext.registerInjectActivateService(
                RequestParameterConfig.class,
                Map.of("file.location", tempFile.toFile().getAbsolutePath()));
        assertNull(config.resolveLocation());
    }
}
