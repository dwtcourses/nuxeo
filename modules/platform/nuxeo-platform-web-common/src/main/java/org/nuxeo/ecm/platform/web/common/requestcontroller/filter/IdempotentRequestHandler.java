/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Anahide Tchertchian
 */
package org.nuxeo.ecm.platform.web.common.requestcontroller.filter;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStore;

/**
 * Handles idempotency key in POST requests.
 * <p>
 * If {@link #HEADER_KEY} is found in the request header, will intercept request handling to:
 * <ul>
 * <li>mark the request as being processed
 * <li>capture the response when request was processed without any error and store it
 * <li>return the stored response if a subsequent request with the same key is processed again
 * <li>return a conflict response if a request with the same key is processed while the first request is still in
 * progress.
 * </ul>
 *
 * @since 11.5
 */
public class IdempotentRequestHandler {

    private static final Logger log = LogManager.getLogger(IdempotentRequestHandler.class);

    /** @since 11.5 */
    public static final String HEADER_KEY = "NuxeoIdempotencyKey";

    /** @since 11.5 */
    public static final String STORE_PROPERTY = "idempotency_keyvaluestore";

    /** @since 11.5 */
    public static final String TTL_SECONDS_PROPERTY = "idempotency_ttl_seconds";

    /** @since 11.5 */
    public static final String INPROGRESS_MARKER = "IDEMPOTENCY_INPROGRESS_MARKER";

    /** @since 11.5 */
    public static final String STATUS_PREFIX = "s_";

    /** @since 11.5 */
    public static final String CONTENT_PREFIX = "c_";

    protected final String store;

    protected final int ttl;

    public IdempotentRequestHandler(FilterConfig filterConfig) {
        // default values
        int ttl = 330;
        String store = "requestcontroller";
        // retrieve potential configuration from filter
        String ttlConfig = filterConfig.getInitParameter(IdempotentRequestHandler.TTL_SECONDS_PROPERTY);
        if (ttlConfig != null) {
            try {
                ttl = Integer.valueOf(ttlConfig);
            } catch (NumberFormatException e) {
                log.error(e, e);
            }
        }
        String storeConfig = filterConfig.getInitParameter(IdempotentRequestHandler.STORE_PROPERTY);
        if (storeConfig != null) {
            store = storeConfig;
        }
        this.store = store;
        this.ttl = ttl;
    }

    public IdempotentRequestHandler(String storename, int ttl) {
        this.store = storename;
        this.ttl = ttl;
    }

    public void handle(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!"POST".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String key = request.getHeader(IdempotentRequestHandler.HEADER_KEY);
        if (key == null) {
            chain.doFilter(request, response);
            return;
        }
        KeyValueStore store = Framework.getService(KeyValueService.class).getKeyValueStore(this.store);
        String storedContent = store.getString(IdempotentRequestHandler.CONTENT_PREFIX + key);
        if (storedContent == null) {
            // new request
            boolean cleanup = false;
            store.put(IdempotentRequestHandler.CONTENT_PREFIX + key, IdempotentRequestHandler.INPROGRESS_MARKER, ttl);
            try {
                CopyingResponseWrapper wrapper = new CopyingResponseWrapper(response);
                chain.doFilter(request, wrapper);
                store.put(IdempotentRequestHandler.CONTENT_PREFIX + key, wrapper.getCaptureAsString(), ttl);
                store.put(IdempotentRequestHandler.STATUS_PREFIX + key, String.valueOf(wrapper.getStatus()), ttl);
            } catch (IOException | ServletException e) {
                cleanup = true;
                throw e;
            } finally {
                if (cleanup || response.getStatus() >= 400) {
                    // error request: cleanup store
                    store.put(IdempotentRequestHandler.CONTENT_PREFIX + key, (String) null, ttl);
                    store.put(IdempotentRequestHandler.STATUS_PREFIX + key, (String) null, ttl);
                }
            }
        } else if (IdempotentRequestHandler.INPROGRESS_MARKER.equals(storedContent)) {
            // request already in progress -> conflict
            response.sendError(HttpServletResponse.SC_CONFLICT,
                    String.format("Idempotent request already in progress for key '%s'", key));
        } else {
            // request already done: return stored result
            response.getWriter().write(storedContent);
            response.setStatus(Integer.valueOf(store.getString(IdempotentRequestHandler.STATUS_PREFIX + key)));
        }
    }

}
