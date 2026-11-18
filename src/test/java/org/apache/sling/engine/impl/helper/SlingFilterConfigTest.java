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
package org.apache.sling.engine.impl.helper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import jakarta.servlet.Filter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.osgi.framework.ServiceReference;

import static org.apache.sling.engine.impl.testutil.MockServiceReference.serviceReference;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.osgi.framework.Constants.SERVICE_ID;
import static org.osgi.framework.Constants.SERVICE_PID;
import static org.osgi.service.component.ComponentConstants.COMPONENT_NAME;

public class SlingFilterConfigTest {

    static Stream<Arguments> scenariosForGetName() {
        List<String> propertyNames = List.of("sling.core.servletName", SERVICE_PID, COMPONENT_NAME, SERVICE_ID);
        return propertyNames.stream().map(propertyName -> {
            int idx = propertyNames.indexOf(propertyName);
            Map<String, Object> properties = new HashMap<>();
            String expectedValue = "ServletName from " + propertyName;
            if (Objects.equals(propertyName, SERVICE_ID)) {
                properties.put(propertyName, 5);
                expectedValue = "5";
            } else {
                properties.put(propertyName, expectedValue);
            }

            List<String> ignoredProperties = propertyNames.subList(idx + 1, propertyNames.size());
            ignoredProperties.forEach(p -> properties.put(p, "ServletName from " + p));
            return argumentSet("MBean name from " + propertyName, expectedValue, serviceReference(properties));
        });
    }

    @ParameterizedTest
    @MethodSource("scenariosForGetName")
    void testGetName(String expectedName, ServiceReference<Filter> reference) {
        assertEquals(expectedName, SlingFilterConfig.getName(reference));
    }
}
