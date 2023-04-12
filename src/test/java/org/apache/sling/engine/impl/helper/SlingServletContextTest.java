/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.sling.engine.impl.helper;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;

import java.util.Dictionary;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import org.apache.sling.engine.impl.Config;
import org.apache.sling.engine.impl.ProductInfoProvider;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.osgi.framework.BundleContext;

public class SlingServletContextTest {

    @Test public void testServletContextListener() {
        final Config cfg = Mockito.mock(Config.class);
        final ProductInfoProvider pip = Mockito.mock(ProductInfoProvider.class);
        final BundleContext bundleContext = Mockito.mock(BundleContext.class);
        final CountDownLatch initLatch = new CountDownLatch(2);
        final AtomicReference<Object> registration = new AtomicReference<>();
        Mockito.doAnswer(new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) {
                registration.set(invocation.getArguments()[1]);
                initLatch.countDown();
                return null;
            }
        }).when(bundleContext).registerService(Mockito.eq(ServletContext.class), any(SlingServletContext.class), any(Dictionary.class));
        final SlingServletContext ctx = new SlingServletContext(cfg, bundleContext, pip);
        final ServletContext source = Mockito.mock(ServletContext.class);
        final ServletContextEvent event = new ServletContextEvent(source);
        ctx.contextInitialized(event);
        assertSame(source, ctx.getServletContext());
        initLatch.countDown();
        try {
            initLatch.await();
        } catch (InterruptedException e) {
            // ignore
        }
        assertSame(ctx, registration.get());
        ctx.contextDestroyed(event);
        assertNull(ctx.getServletContext());
    }
}
