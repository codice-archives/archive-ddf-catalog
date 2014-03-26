/**
 * Copyright (c) Codice Foundation
 * 
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 * 
 **/
package ddf.catalog.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ddf.catalog.data.Metacard;
import ddf.catalog.operation.ResourceRequest;
import ddf.catalog.operation.ResourceResponse;
import ddf.catalog.resource.Resource;
import ddf.catalog.resourceretriever.ResourceRetriever;

public class CachedResourceTest {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CachedResourceTest.class);
    
    public static String workingDir;
    public static String productCacheDirectory;
    public static String productInputFilename;
    public static long expectedFileSize;
    public static String expectedFileContents;
    public static final int maxRetryAttempts = 3;
    public static final int delayBetweenAttempts = 1;
    public static final int cachingMonitorPeriod = 5;

    public MockInputStream mis;
    
    public ResourceRequest resourceRequest;
    
    public ResourceResponse resourceResponse;
    
    public Resource resource;
    
    public ExecutorService executor;
    

    @BeforeClass
    public static void oneTimeSetup() throws IOException {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory
                .getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.DEBUG);
        
        workingDir = System.getProperty("user.dir");
        productCacheDirectory = workingDir + "/target/tests/product-cache";
        productInputFilename = workingDir + "/src/test/resources/foo_10_lines.txt";
        File productInputFile = new File(productInputFilename);
        expectedFileSize = productInputFile.length();
        expectedFileContents = FileUtils.readFileToString(productInputFile);
    }

    @Test
    //@Ignore
    public void testHasProductWithNullFilepath() {
        assertFalse(new CachedResource("", maxRetryAttempts, delayBetweenAttempts, cachingMonitorPeriod).hasProduct());
    }

    @Test
    //@Ignore
    public void testGetProductWithNullFilepath() throws IOException {
        assertNull(new CachedResource("", maxRetryAttempts, delayBetweenAttempts, cachingMonitorPeriod).getProduct());
    }

    /**
     * Test sunny day scenario where product is cached without any exceptions or timeouts
     * occurring.
     * 
     * @throws Exception
     */
    @Test
    //@Ignore
    public void testStore() throws Exception {
        mis = new MockInputStream(productInputFilename);
        
        Metacard metacard = getMetacardStub("abc123", "ddf-1");       
        resourceResponse = getResourceResponseStub();
        CacheKey keyMaker = new CacheKey(metacard, resourceResponse.getRequest());
        String key = keyMaker.generateKey();
        
        ResourceRetriever retriever = mock(ResourceRetriever.class);
        when(retriever.retrieveResource()).thenReturn(resourceResponse);

        ArgumentCaptor<CachedResource> argument = ArgumentCaptor.forClass(CachedResource.class);
        ResourceCache resourceCache = mock(ResourceCache.class);
        
        CachedResource cachedResource = new CachedResource(productCacheDirectory, maxRetryAttempts,
                delayBetweenAttempts, cachingMonitorPeriod);
        int chunkSize = 50;
        cachedResource.setChunkSize(chunkSize);
        
        Resource newResource = cachedResource.store(key, resourceResponse, resourceCache,
                retriever);
        assertNotNull(newResource);
        ByteArrayOutputStream clientBytesRead = clientRead(chunkSize, newResource.getInputStream());
        
        // Captures the CachedResource object that should have been put in the ResourceCache's map
        verify(resourceCache).put(argument.capture());
        
        verifyCaching(argument.getValue(), "ddf-1-abc123", clientBytesRead);
        
        // Cleanup
//      FileUtils.deleteDirectory(new File(productCacheDirectory));
        executor.shutdown();

    }
   
//////////////////////////////////////////////////////////////////////////////////////////////////
    
    private void verifyCaching(CachedResource cr, String expectedCacheKey,
            ByteArrayOutputStream clientBytesRead) throws IOException {
        assertEquals(expectedCacheKey, cr.getKey());
        byte[] cachedData = cr.getByteArray();
        assertNotNull(cachedData);
        
        // Verifies cached data read in was same contents as product input file
        assertTrue(cachedData.length == expectedFileSize);
        assertEquals(expectedFileContents, new String(cachedData));
        
        // Verifies client read same contents as product input file
        if (clientBytesRead != null) {
            assertTrue(clientBytesRead.size() == expectedFileSize);
            assertEquals(expectedFileContents, new String(clientBytesRead.toByteArray()));
        }
        
        // Verifies cached file on disk has same contents as product input file
        assertEquals(expectedFileContents, IOUtils.toString(cr.getProduct()));
    }
    
    private Metacard getMetacardStub(String id, String source) {

        Metacard metacard = mock(Metacard.class);

        when(metacard.getId()).thenReturn(id);

        when(metacard.getSourceId()).thenReturn(source);

        return metacard;
    }
    
    private ResourceResponse getResourceResponseStub() throws Exception {
        resourceRequest = mock(ResourceRequest.class);
        Map<String, Serializable> requestProperties = new HashMap<String, Serializable>();
        when(resourceRequest.getPropertyNames()).thenReturn(requestProperties.keySet());

        resource = mock(Resource.class);
        when(resource.getInputStream()).thenReturn(mis);
        when(resource.getName()).thenReturn("test-resource");
        when(resource.getMimeType()).thenReturn(new MimeType("text/plain"));

        resourceResponse = mock(ResourceResponse.class);
        when(resourceResponse.getRequest()).thenReturn(resourceRequest);
        when(resourceResponse.getResource()).thenReturn(resource);
        when(resourceResponse.getProperties()).thenReturn(null);
        
        return resourceResponse;
    }
    
    private ResourceRetriever getResourceRetrieverStubWithRetryCapability() throws Exception {
        
        // Mocking to support re-retrieval of product when error encountered
        // during caching.
        ResourceRetriever retriever = mock(ResourceRetriever.class);
        when(retriever.retrieveResource()).thenAnswer(new Answer<Object>() {
            public Object answer(InvocationOnMock invocation) {
                // Create new InputStream for retrieving the same product. This
                // simulates re-retrieving the product from the remote source.
                mis = new MockInputStream(productInputFilename);
                
                // Reset the mock Resource so that it can be reconfigured to return
                // the new InputStream
                reset(resource);
                when(resource.getInputStream()).thenReturn(mis);
                when(resource.getName()).thenReturn("test-resource");
                try {
                    when(resource.getMimeType()).thenReturn(new MimeType("text/plain"));
                } catch (MimeTypeParseException e) {
                }

                // Reset the mock ResourceResponse so that it can be reconfigured to return
                // the new Resource
                reset(resourceResponse);
                when(resourceResponse.getRequest()).thenReturn(resourceRequest);
                when(resourceResponse.getResource()).thenReturn(resource);
                when(resourceResponse.getProperties()).thenReturn(null);
                
                return resourceResponse;
            }
        });
        
        return retriever;
    }

    public ByteArrayOutputStream clientRead(int chunkSize, InputStream is) throws Exception {
        return clientRead(chunkSize, is, -1);
    }
    
    public ByteArrayOutputStream clientRead(int chunkSize, InputStream is, int simulatedCancelChunkCount) throws Exception {
        executor = Executors.newCachedThreadPool();
        CacheClient cacheClient = new CacheClient(is, chunkSize, simulatedCancelChunkCount);
        Future<ByteArrayOutputStream> future = executor.submit(cacheClient);
        ByteArrayOutputStream clientBytesRead = future.get();
        
        return clientBytesRead;
    }

}