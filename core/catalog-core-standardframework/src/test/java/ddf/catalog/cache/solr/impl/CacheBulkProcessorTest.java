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
package ddf.catalog.cache.solr.impl;

import static org.mockito.Matchers.anyCollectionOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import ddf.catalog.data.Metacard;
import ddf.catalog.data.Result;

public class CacheBulkProcessorTest extends TestCase {

    private CacheBulkProcessor cacheBulkProcessor;

    private SolrCache mockSolrCache;

    @Override
    public void setUp() throws Exception {
        mockSolrCache = mock(SolrCache.class);
        cacheBulkProcessor = new CacheBulkProcessor(mockSolrCache, 1, TimeUnit.MILLISECONDS);
        cacheBulkProcessor.setBatchSize(10);
    }

    public void testBulkAdd() throws Exception {
        cacheBulkProcessor.setFlushInterval(TimeUnit.MINUTES.toMillis(1));
        cacheBulkProcessor.add(getMockResults(10));

        while (cacheBulkProcessor.pendingMetacards() > 0) {
            Thread.sleep(2);
        }

        verify(mockSolrCache, times(1)).create(anyCollectionOf(Metacard.class));
    }

    public void testFlush() throws Exception {
        cacheBulkProcessor.setFlushInterval(1);
        cacheBulkProcessor.add(getMockResults(1));

        while (cacheBulkProcessor.pendingMetacards() > 0) {
            Thread.sleep(1);
        }

        verify(mockSolrCache, times(1)).create(anyCollectionOf(Metacard.class));
    }

    @Override
    public void tearDown() throws Exception {
        cacheBulkProcessor.shutdown();
    }

    private List<Result> getMockResults(int size) {
        List<Result> results = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            Metacard mockMetacard = mock(Metacard.class);
            when(mockMetacard.getId()).thenReturn(Integer.toString(i));

            Result mockResult = mock(Result.class);
            when(mockResult.getMetacard()).thenReturn(mockMetacard);

            results.add(mockResult);
        }

        return results;
    }

}