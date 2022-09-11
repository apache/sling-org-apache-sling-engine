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
package org.apache.sling.engine.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * The OSGi configuration for the main servlet. This configuration is actually
 * used by various components throughout the bundle.
 */
@ObjectClassDefinition(name ="Apache Sling Main Servlet",
        description="Main processor of the Sling framework controlling all " +
                "aspects of processing requests inside of Sling, namely authentication, " +
                "resource resolution, servlet/script resolution and execution of servlets " +
                "and scripts.")
public @interface Config {

    String PID = "org.apache.sling.engine.impl.SlingMainServlet";

    /**
     * The default value for the number of recursive inclusions for a single
     * instance of this class (value is 50).
     */
    int DEFAULT_MAX_INCLUSION_COUNTER = 50;

    /**
     * The default value for the number of calls to the
     * {@link #service(SlingHttpServletRequest, SlingHttpServletResponse)}
     * method for a single instance of this class (value is 1000).
     */
    int DEFAULT_MAX_CALL_COUNTER = 1000;

    @AttributeDefinition(name = "Number of Calls per Request",
            description = "Defines the maximum number of Servlet and Script " +
                 "calls while processing a single client request. This number should be high " +
                 "enough to not limit request processing artificially. On the other hand it " +
                 "should not be too high to allow the mechanism to limit the resources required " +
                 "to process a request in case of errors. The default value is 1000.")
    int sling_max_calls() default DEFAULT_MAX_CALL_COUNTER;

    @AttributeDefinition(name = "Recursion Depth",
            description = "The maximum number of recursive Servlet and " +
                 "Script calls while processing a single client request. This number should not " +
                 "be too high, otherwise StackOverflowErrors may occurr in case of erroneous " +
                 "scripts and servlets. The default value is 50. ")
    int sling_max_inclusions() default DEFAULT_MAX_INCLUSION_COUNTER;

    @AttributeDefinition(name = "Allow the HTTP TRACE method",
            description = "If set to true, the HTTP TRACE method will be " +
                 "enabled. By default the HTTP TRACE methods is disabled as it can be used in " +
                 "Cross Site Scripting attacks on HTTP servers.")
    boolean sling_trace_allow() default false;

    @AttributeDefinition(name = "Number of Requests to Record",
            description = "Defines the number of requests that " +
                 "internally recorded for display on the \"Recent Requests\" Web Console page. If " +
                 "this value is less than or equal to zero, no requests are internally kept. The " +
                 "default value is 20. ")
    int sling_max_record_requests() default 20;

    @AttributeDefinition(name = "Recorded Request Path Patterns",
            description = "One or more regular expressions which " +
                        "limit the requests which are stored by the \"Recent Requests\" Web Console page.")
    String[] sling_store_pattern_requests();

    @AttributeDefinition(name = "Server Info",
            description = "The server info returned by Sling. If this field is left empty, Sling generates a default into.")
    String sling_serverinfo();

    @AttributeDefinition(name = "Additional response headers",
            description = "Provides mappings for additional response headers "
                + "Each entry is of the form 'bundleId [ \":\" responseHeaderName ] \"=\" responseHeaderValue'")
    String[] sling_additional_response_headers() default {"X-Content-Type-Options=nosniff", "X-Frame-Options=SAMEORIGIN"};

    @AttributeDefinition(name = "Servlet Name", description = "Optional name for the Sling main servlet registered by this component")
    String servlet_name();
}