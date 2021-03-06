/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.directvm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;

/**
 * Represents the component that manages {@link DirectVmEndpoint}. It holds the
 * list of named direct-vm endpoints.
 */
public class DirectVmComponent extends DefaultComponent {

    private static final AtomicInteger START_COUNTER = new AtomicInteger();

    // must keep a map of consumers on the component to ensure endpoints can lookup old consumers
    // later in case the DirectVmEndpoint was re-created due the old was evicted from the endpoints LRUCache
    // on DefaultCamelContext
    private static final ConcurrentMap<String, DirectVmConsumer> CONSUMERS = new ConcurrentHashMap<String, DirectVmConsumer>();

    /**
     * Gets all the consumer endpoints.
     *
     * @return consumer endpoints
     */
    public static Collection<Endpoint> getConsumerEndpoints() {
        Collection<Endpoint> endpoints = new ArrayList<Endpoint>(CONSUMERS.size());
        for (DirectVmConsumer consumer : CONSUMERS.values()) {
            endpoints.add(consumer.getEndpoint());
        }
        return endpoints;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        DirectVmEndpoint answer = new DirectVmEndpoint(uri, this);
        answer.configureProperties(parameters);
        return answer;
    }

    public DirectVmConsumer getConsumer(DirectVmEndpoint endpoint) {
        String key = getConsumerKey(endpoint.getEndpointUri());
        return CONSUMERS.get(key);
    }

    public void addConsumer(DirectVmEndpoint endpoint, DirectVmConsumer consumer) {
        String key = getConsumerKey(endpoint.getEndpointUri());
        DirectVmConsumer existing = CONSUMERS.putIfAbsent(key, consumer);
        if (existing != null) {
            String contextId = existing.getEndpoint().getCamelContext().getName();
            throw new IllegalStateException("A consumer " + existing + " already exists from CamelContext: " + contextId + ". Multiple consumers not supported");
        }
    }

    public void removeConsumer(DirectVmEndpoint endpoint, DirectVmConsumer consumer) {
        String key = getConsumerKey(endpoint.getEndpointUri());
        CONSUMERS.remove(key);
    }

    private static String getConsumerKey(String uri) {
        if (uri.contains("?")) {
            // strip parameters
            uri = uri.substring(0, uri.indexOf('?'));
        }
        return uri;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        START_COUNTER.incrementAndGet();
    }

    @Override
    protected void doStop() throws Exception {
        if (START_COUNTER.decrementAndGet() <= 0) {
            // clear queues when no more direct-vm components in use
            CONSUMERS.clear();
        }
        super.doStop();
    }

}
