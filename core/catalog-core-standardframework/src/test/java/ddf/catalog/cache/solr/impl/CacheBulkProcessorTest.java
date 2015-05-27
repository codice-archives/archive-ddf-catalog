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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.runners.MockitoJUnitRunner;

import ddf.catalog.data.Metacard;
import ddf.catalog.data.Result;

@RunWith(MockitoJUnitRunner.class)
public class CacheBulkProcessorTest {

    private CacheBulkProcessor cacheBulkProcessor;

    private SolrCache mockSolrCache;

    @Captor
    ArgumentCaptor<Collection<Metacard>> capturedMetacards;

    @Before
    public void setUp() throws Exception {
        mockSolrCache = mock(SolrCache.class);
        cacheBulkProcessor = new CacheBulkProcessor(mockSolrCache, 1, TimeUnit.MILLISECONDS);
        cacheBulkProcessor.setBatchSize(10);
    }

    @After
    public void tearDown() throws Exception {
        cacheBulkProcessor.shutdown();
    }

    @Test
    public void testBulkAdd() throws Exception {
        cacheBulkProcessor.setFlushInterval(TimeUnit.MINUTES.toMillis(1));
        List<Result> mockResults = getMockResults(10);
        cacheBulkProcessor.add(mockResults);

        while (cacheBulkProcessor.pendingMetacards() > 0) {
            Thread.sleep(2);
        }

        verify(mockSolrCache, times(1)).create(capturedMetacards.capture());
        assertThat(capturedMetacards.getValue()).containsAll(getMetacards(mockResults));
    }

    @Test
    public void testFlush() throws Exception {
        cacheBulkProcessor.setFlushInterval(1);
        List<Result> mockResults = getMockResults(1);
        cacheBulkProcessor.add(mockResults);

        while (cacheBulkProcessor.pendingMetacards() > 0) {
            Thread.sleep(1);
        }

        verify(mockSolrCache, times(1)).create(capturedMetacards.capture());
        assertThat(capturedMetacards.getValue()).containsAll(getMetacards(mockResults));
    }

    private Collection<Metacard> getMetacards(List<Result> results) {
        List<Metacard> metacards = new ArrayList<>(results.size());

        for (Result result : results) {
            metacards.add(result.getMetacard());
        }

        return metacards;
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