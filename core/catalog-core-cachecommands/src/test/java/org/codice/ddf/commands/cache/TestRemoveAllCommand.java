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
package org.codice.ddf.commands.cache;

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.Test;

import ddf.catalog.cache.solr.impl.SolrCacheMBean;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.Result;
import ddf.catalog.data.impl.MetacardImpl;
import ddf.catalog.data.impl.ResultImpl;
import ddf.catalog.filter.FilterBuilder;
import ddf.catalog.filter.proxy.builder.GeotoolsFilterBuilder;
import ddf.catalog.operation.DeleteRequest;
import ddf.catalog.operation.DeleteResponse;
import ddf.catalog.operation.QueryRequest;
import ddf.catalog.operation.QueryResponse;
import ddf.catalog.operation.SourceResponse;

public class TestRemoveAllCommand {

    @Test
    public void testDoExecute() throws Exception {

        final SolrCacheMBean mbean = mock(SolrCacheMBean.class);

        SourceResponse sourceResponse = mock(SourceResponse.class);

        when(sourceResponse.getResults()).thenReturn(getResultList(10));

        when(mbean.query(isA(QueryRequest.class))).thenReturn(sourceResponse);

        RemoveAllCommand removeAllCommand = new RemoveAllCommand() {
            @Override
            protected FilterBuilder getFilterBuilder() {
                return new GeotoolsFilterBuilder();
            }

            @Override
            protected SolrCacheMBean getCacheProxy() {
                return mbean;
            }
        };

        removeAllCommand.doExecute();

        verify(mbean, times(1)).delete(isA(DeleteRequest.class));
    }

    private java.util.List<Result> getResultList(int amount) {

        java.util.List<Result> results = new ArrayList<Result>();

        for (int i = 0; i < amount; i++) {

            String id = UUID.randomUUID().toString();
            MetacardImpl metacard = new MetacardImpl();
            metacard.setId(id);
            Result result = new ResultImpl(metacard);
            results.add(result);

        }

        return results;
    }
}
