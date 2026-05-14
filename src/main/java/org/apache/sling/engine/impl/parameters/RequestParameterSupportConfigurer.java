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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.engine.impl.parameters.RequestParameterConfig.Config;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.osgi.service.component.propertytypes.ServiceRanking;
import org.osgi.service.component.propertytypes.ServiceVendor;
import org.osgi.service.servlet.whiteboard.propertytypes.HttpWhiteboardContextSelect;
import org.osgi.service.servlet.whiteboard.propertytypes.HttpWhiteboardFilterPattern;

@Component(service = Filter.class)
@HttpWhiteboardContextSelect("(osgi.http.whiteboard.context.name=org.apache.sling)")
@HttpWhiteboardFilterPattern("/")
@ServiceDescription("Filter for request parameter support")
@ServiceVendor("The Apache Software Foundation")
@ServiceRanking(Integer.MAX_VALUE)
public class RequestParameterSupportConfigurer implements Filter {

    /** extra config properties for multipart file upload support */
    @Reference
    private RequestParameterConfig reqParamConfig;

    @Activate
    private void configure() {
        final Config config = reqParamConfig.getConfig();
        final String fixEncoding = config.sling_default_parameter_encoding();
        final int maxParams = config.sling_default_max_parameters();
        final boolean checkAddParameters = config.sling_default_parameter_checkForAdditionalContainerParameters();

        Util.setDefaultFixEncoding(fixEncoding);
        ParameterMap.setMaxParameters(maxParams);
        ParameterSupport.configure(checkAddParameters);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpReq
                && !(request instanceof ParameterSupportHttpServletRequestWrapper)
                && !(request instanceof SlingJakartaHttpServletRequest)) {
            chain.doFilter(ParameterSupport.getParameterSupportRequestWrapper(httpReq), response);
        } else {
            chain.doFilter(request, response);
        }
    }
}
