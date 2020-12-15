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
package org.nuxeo.ecm.platform.web.requestcontroller.filter;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import javax.inject.Inject;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.nuxeo.ecm.platform.web.common.requestcontroller.filter.IdempotentRequestHandler;
import org.nuxeo.ecm.platform.web.common.requestcontroller.filter.NuxeoRequestControllerFilter;
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStoreProvider;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

/**
 * Checks idempotent requests management.
 *
 * @since 11.5
 */
@RunWith(FeaturesRunner.class)
@Features(RuntimeFeature.class)
@Deploy("org.nuxeo.runtime.kv")
@Deploy("org.nuxeo.ecm.platform.web.common:OSGI-INF/web-request-controller-framework.xml")
public class TestIdempotentRequestFilter {

    protected static final int TTL = 30;

    protected static final String KV_STORE_NAME = "requestcontroller";

    protected static final String KEY = "mykey";

    protected static final String CONTENT = "test content";

    protected NuxeoRequestControllerFilter filter;

    protected FilterChain chain;

    protected HttpServletRequest request;

    protected MockResponse mockResponse;

    @Inject
    protected KeyValueService kvs;

    protected KeyValueStoreProvider store;

    protected static class MockResponse {

        protected HttpServletResponse response;

        protected int status;

        protected OutputStream output;

        public MockResponse() throws IOException {
            super();
            response = mock(HttpServletResponse.class);
            output = new ByteArrayOutputStream();
            ServletOutputStream servletOutput = mock(ServletOutputStream.class);
            doAnswer(invocation -> {
                output.write((byte[]) invocation.getArguments()[0]);
                return null;
            }).when(servletOutput).write(any(byte[].class));
            doAnswer(invocation -> {
                output.write((Integer) invocation.getArguments()[0]);
                return null;
            }).when(servletOutput).write(anyInt());
            when(response.getOutputStream()).thenReturn(servletOutput);
            PrintWriter writer = mock(PrintWriter.class);
            doAnswer(invocation -> {
                output.write(((String) invocation.getArguments()[0]).getBytes());
                return null;
            }).when(writer).write(anyString());
            doAnswer(invocation -> {
                return writer;
            }).when(response).getWriter();
            when(response.getCharacterEncoding()).thenReturn(UTF_8.name());
            doAnswer(invocation -> {
                return status;
            }).when(response).getStatus();
            doAnswer(invocation -> {
                status = (Integer) invocation.getArguments()[0];
                return null;
            }).when(response).setStatus(anyInt());
            doAnswer(invocation -> {
                status = (Integer) invocation.getArguments()[0];
                output.write(((String) invocation.getArguments()[1]).getBytes());
                return null;
            }).when(response).sendError(anyInt(), anyString());
        }

        public int getStatus() {
            return status;
        }

        public HttpServletResponse getResponse() {
            return response;
        }

        public OutputStream getOutput() {
            return output;
        }

    }

    @Before
    public void setUp() throws IOException {
        filter = new NuxeoRequestControllerFilter();
        FilterConfig config = mock(FilterConfig.class);
        when(config.getInitParameter(IdempotentRequestHandler.TTL_SECONDS_PROPERTY)).thenReturn(String.valueOf(TTL));
        when(config.getInitParameter(IdempotentRequestHandler.STORE_PROPERTY)).thenReturn(KV_STORE_NAME);
        filter.init(config);
        chain = mock(FilterChain.class);
        request = mock(HttpServletRequest.class);
        mockResponse = new MockResponse();
        // handle store
        store = (KeyValueStoreProvider) kvs.getKeyValueStore(KV_STORE_NAME);
        store.clear();
    }

    @After
    public void tearDown() {
        if (filter != null) {
            filter.destroy();
        }
        if (store != null) {
            store.clear();
        }
    }

    protected void checkResponse(MockResponse mockResponse, Integer status, String content) {
        assertEquals(status, (Integer) mockResponse.getStatus());
        assertEquals(content, mockResponse.getOutput().toString());
    }

    protected void checkStore(Integer status, String content) {
        assertEquals(content, store.getString(IdempotentRequestHandler.CONTENT_PREFIX + KEY));
        String skey = IdempotentRequestHandler.STATUS_PREFIX + KEY;
        if (status == null) {
            assertNull(store.getString(skey));
        } else {
            assertEquals(String.valueOf(status), store.getString(skey));
        }
    }

    @Test
    public void testGetRequestWithoutKey() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        verify(chain, times(0)).doFilter(any(), any());
        filter.doFilter(request, mockResponse.getResponse(), chain);
        verify(chain, times(1)).doFilter(any(), any());
        checkStore(null, null);
    }

    @Test
    public void testPostRequestWithoutKey() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        verify(chain, times(0)).doFilter(any(), any());
        filter.doFilter(request, mockResponse.getResponse(), chain);
        verify(chain, times(1)).doFilter(any(), any());
        checkStore(null, null);
    }

    @Test
    public void testGetRequest() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader(IdempotentRequestHandler.HEADER_KEY)).thenReturn(KEY);
        verify(chain, times(0)).doFilter(any(), any());
        filter.doFilter(request, mockResponse.getResponse(), chain);
        verify(chain, times(1)).doFilter(any(), any());
        checkStore(null, null);
    }

    protected void setResult(HttpServletResponse response, int status, String content) throws IOException {
        response.setStatus(status);
        response.getWriter().write(content);
    }

    @Test
    public void testPostRequest() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader(IdempotentRequestHandler.HEADER_KEY)).thenReturn(KEY);
        // mock final call
        doAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws IOException {
                setResult((HttpServletResponse) invocation.getArguments()[1], SC_OK, CONTENT);
                return null;
            }
        }).when(chain).doFilter(any(), any());

        verify(chain, times(0)).doFilter(any(), any());
        filter.doFilter(request, mockResponse.getResponse(), chain);
        verify(chain, times(1)).doFilter(any(), any());
        checkResponse(mockResponse, SC_OK, CONTENT);
        checkStore(SC_OK, CONTENT);
        // call filter again: stored value will be sent back again
        MockResponse mockResponse2 = new MockResponse();
        filter.doFilter(request, mockResponse2.getResponse(), chain);
        // chain filter not called again
        verify(chain, times(1)).doFilter(any(), any());
        checkResponse(mockResponse2, SC_OK, CONTENT);
        checkStore(SC_OK, CONTENT);
    }

    @Test
    public void testPostRequestInProgress() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader(IdempotentRequestHandler.HEADER_KEY)).thenReturn(KEY);

        doAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws IOException, ServletException {
                // during first execution, execute another request
                MockResponse mockResponse2 = new MockResponse();
                verify(chain, times(1)).doFilter(any(), any());
                filter.doFilter(request, mockResponse2.getResponse(), mock(FilterChain.class));
                verify(chain, times(1)).doFilter(any(), any());
                checkResponse(mockResponse2, SC_CONFLICT,
                        String.format("Idempotent request already in progress for key '%s'", KEY));
                checkStore(null, IdempotentRequestHandler.INPROGRESS_MARKER);

                // finish first call
                setResult((HttpServletResponse) invocation.getArguments()[1], SC_OK, CONTENT);
                return null;
            }
        }).when(chain).doFilter(any(), any());

        verify(chain, times(0)).doFilter(any(), any());
        filter.doFilter(request, mockResponse.getResponse(), chain);
        verify(chain, times(1)).doFilter(any(), any());
        checkResponse(mockResponse, SC_OK, CONTENT);
        checkStore(SC_OK, CONTENT);
    }

    @Test
    public void testPostRequestException() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader(IdempotentRequestHandler.HEADER_KEY)).thenReturn(KEY);

        doAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws IOException, ServletException {
                throw new ServletException("test error");
            }
        }).when(chain).doFilter(any(), any());

        verify(chain, times(0)).doFilter(any(), any());
        try {
            filter.doFilter(request, mockResponse.getResponse(), chain);
            fail("should have thrown Servlet Exception");
        } catch (ServletException e) {
            // ok
        }
        verify(chain, times(1)).doFilter(any(), any());
        checkResponse(mockResponse, SC_INTERNAL_SERVER_ERROR, "");
        checkStore(null, null);

        // try again
        doAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws IOException {
                setResult((HttpServletResponse) invocation.getArguments()[1], SC_OK, CONTENT);
                return null;
            }
        }).when(chain).doFilter(any(), any());
        verify(chain, times(1)).doFilter(any(), any());
        filter.doFilter(request, mockResponse.getResponse(), chain);
        verify(chain, times(2)).doFilter(any(), any());
        checkResponse(mockResponse, SC_OK, CONTENT);
        checkStore(SC_OK, CONTENT);
    }

    @Test
    public void testPostRequestError() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader(IdempotentRequestHandler.HEADER_KEY)).thenReturn(KEY);

        doAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws IOException, ServletException {
                setResult((HttpServletResponse) invocation.getArguments()[1], SC_NOT_FOUND, "not found");
                return null;
            }
        }).when(chain).doFilter(any(), any());

        verify(chain, times(0)).doFilter(any(), any());
        filter.doFilter(request, mockResponse.getResponse(), chain);
        verify(chain, times(1)).doFilter(any(), any());
        checkResponse(mockResponse, SC_NOT_FOUND, "not found");
        checkStore(null, null);
    }

}
