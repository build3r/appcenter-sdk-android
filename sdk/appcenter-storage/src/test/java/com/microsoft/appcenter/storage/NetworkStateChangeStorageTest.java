/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 */

package com.microsoft.appcenter.storage;

import com.microsoft.appcenter.http.HttpException;
import com.microsoft.appcenter.storage.models.BaseOptions;
import com.microsoft.appcenter.storage.models.DataStoreEventListener;
import com.microsoft.appcenter.storage.models.DocumentError;
import com.microsoft.appcenter.storage.models.DocumentMetadata;
import com.microsoft.appcenter.storage.models.PendingOperation;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.ArrayList;

import static com.microsoft.appcenter.http.DefaultHttpClient.METHOD_DELETE;
import static com.microsoft.appcenter.http.DefaultHttpClient.METHOD_POST;
import static com.microsoft.appcenter.storage.Constants.PENDING_OPERATION_CREATE_VALUE;
import static com.microsoft.appcenter.storage.Constants.PENDING_OPERATION_DELETE_VALUE;
import static com.microsoft.appcenter.storage.Constants.PENDING_OPERATION_REPLACE_VALUE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.verifyNoMoreInteractions;

public class NetworkStateChangeStorageTest extends AbstractStorageTest {

    @Before
    public void setUpAuth() {
        setUpAuthContext();
    }

    @Mock
    private DataStoreEventListener mDataStoreEventListener;

    @Test
    public void pendingCreateOperationSuccess() throws JSONException {
        final PendingOperation pendingOperation = new PendingOperation(
                USER_TABLE_NAME,
                PENDING_OPERATION_CREATE_VALUE,
                RESOLVED_USER_PARTITION,
                DOCUMENT_ID,
                "document",
                BaseOptions.DEFAULT_EXPIRATION_IN_SECONDS);
        when(mLocalDocumentStorage.getPendingOperations(USER_TABLE_NAME)).thenReturn(
                new ArrayList<PendingOperation>() {{
                    add(pendingOperation);
                }});
        ArgumentCaptor<DocumentMetadata> documentMetadataArgumentCaptor = ArgumentCaptor.forClass(DocumentMetadata.class);

        Storage.setDataStoreRemoteOperationListener(mDataStoreEventListener);
        mStorage.onNetworkStateUpdated(true);

        verifyTokenExchangeToCosmosDbFlow(null, TOKEN_EXCHANGE_USER_PAYLOAD, METHOD_POST, COSMOS_DB_DOCUMENT_RESPONSE_PAYLOAD, null);

        verify(mDataStoreEventListener).onDataStoreOperationResult(
                eq(PENDING_OPERATION_CREATE_VALUE),
                documentMetadataArgumentCaptor.capture(),
                isNull(DocumentError.class));
        DocumentMetadata documentMetadata = documentMetadataArgumentCaptor.getValue();
        assertNotNull(documentMetadata);
        verifyNoMoreInteractions(mDataStoreEventListener);

        assertEquals(DOCUMENT_ID, documentMetadata.getDocumentId());
        assertEquals(RESOLVED_USER_PARTITION, documentMetadata.getPartition());
        assertEquals(ETAG, documentMetadata.getEtag());

        ArgumentCaptor<PendingOperation> pendingOperationCaptor = ArgumentCaptor.forClass(PendingOperation.class);
        verify(mLocalDocumentStorage).resetPendingOperationColumnToNull(pendingOperationCaptor.capture());
        PendingOperation capturedOperation = pendingOperationCaptor.getValue();
        assertNotNull(capturedOperation);
        assertEquals(pendingOperation, capturedOperation);
        assertEquals(ETAG, capturedOperation.getEtag());
        assertEquals(COSMOS_DB_DOCUMENT_RESPONSE_PAYLOAD, capturedOperation.getDocument());

        verifyNoMoreInteractions(mHttpClient);
    }

    @Test
    public void pendingCreateOperationSuccessWithNoListener() throws JSONException {
        final PendingOperation pendingOperation = new PendingOperation(
                USER_TABLE_NAME,
                PENDING_OPERATION_CREATE_VALUE,
                RESOLVED_USER_PARTITION,
                DOCUMENT_ID,
                "document",
                BaseOptions.DEFAULT_EXPIRATION_IN_SECONDS);
        when(mLocalDocumentStorage.getPendingOperations(USER_TABLE_NAME)).thenReturn(
                new ArrayList<PendingOperation>() {{
                    add(pendingOperation);
                }});

        mStorage.onNetworkStateUpdated(true);
        verifyTokenExchangeToCosmosDbFlow(null, TOKEN_EXCHANGE_USER_PAYLOAD, METHOD_POST, COSMOS_DB_DOCUMENT_RESPONSE_PAYLOAD, null);

        ArgumentCaptor<PendingOperation> pendingOperationCaptor = ArgumentCaptor.forClass(PendingOperation.class);
        verify(mLocalDocumentStorage).resetPendingOperationColumnToNull(pendingOperationCaptor.capture());
        PendingOperation capturedOperation = pendingOperationCaptor.getValue();
        assertNotNull(capturedOperation);
        assertEquals(pendingOperation, capturedOperation);
        assertEquals(ETAG, capturedOperation.getEtag());
        assertEquals(COSMOS_DB_DOCUMENT_RESPONSE_PAYLOAD, capturedOperation.getDocument());

        verifyNoMoreInteractions(mHttpClient);
        verifyZeroInteractions(mDataStoreEventListener);
    }

    @Test
    public void pendingReplaceOperationFailure() throws JSONException {
        verifyPendingOperationFailure(PENDING_OPERATION_REPLACE_VALUE, METHOD_POST, null);
    }

    @Test
    public void pendingDeleteOperationFailure() throws JSONException {
        verifyPendingOperationFailure(PENDING_OPERATION_DELETE_VALUE, METHOD_DELETE, DOCUMENT_ID);
    }

    private void verifyPendingOperationFailure(String operation, String cosmosDbMethod, String documentId) throws JSONException {
        final String document = "document";
        final PendingOperation pendingOperation =
                new PendingOperation(
                        USER_TABLE_NAME,
                        operation,
                        RESOLVED_USER_PARTITION,
                        DOCUMENT_ID,
                        document,
                        BaseOptions.DEFAULT_EXPIRATION_IN_SECONDS);
        when(mLocalDocumentStorage.getPendingOperations(USER_TABLE_NAME)).thenReturn(
                new ArrayList<PendingOperation>() {{
                    add(pendingOperation);
                }});
        ArgumentCaptor<DocumentError> documentErrorArgumentCaptor = ArgumentCaptor.forClass(DocumentError.class);

        Storage.setDataStoreRemoteOperationListener(mDataStoreEventListener);
        mStorage.onNetworkStateUpdated(true);

        HttpException cosmosFailureException = new HttpException(500, "You failed!");
        verifyTokenExchangeToCosmosDbFlow(documentId, TOKEN_EXCHANGE_USER_PAYLOAD, cosmosDbMethod, null, cosmosFailureException);

        verify(mDataStoreEventListener).onDataStoreOperationResult(
                eq(operation),
                isNull(DocumentMetadata.class),
                documentErrorArgumentCaptor.capture());
        DocumentError documentError = documentErrorArgumentCaptor.getValue();
        assertNotNull(documentError);
        verifyNoMoreInteractions(mDataStoreEventListener);

        assertEquals(cosmosFailureException, documentError.getError().getCause());

        ArgumentCaptor<PendingOperation> pendingOperationCaptor = ArgumentCaptor.forClass(PendingOperation.class);
        verify(mLocalDocumentStorage).resetPendingOperationColumnToNull(pendingOperationCaptor.capture());
        PendingOperation capturedOperation = pendingOperationCaptor.getValue();
        assertNotNull(capturedOperation);
        assertEquals(pendingOperation, capturedOperation);
        assertNull(capturedOperation.getEtag());
        assertEquals(document, capturedOperation.getDocument());

        verifyNoMoreInteractions(mHttpClient);
    }

    @Test
    public void unsupportedPendingOperation() {
        when(mLocalDocumentStorage.getPendingOperations(USER_TABLE_NAME)).thenReturn(
                new ArrayList<PendingOperation>() {{
                    add(new PendingOperation(
                            USER_TABLE_NAME,
                            "Order a coffee",
                            RESOLVED_USER_PARTITION,
                            DOCUMENT_ID,
                            "document",
                            BaseOptions.DEFAULT_EXPIRATION_IN_SECONDS));
                }});
        mStorage.onNetworkStateUpdated(true);
        verifyZeroInteractions(mHttpClient);
        verifyZeroInteractions(mDataStoreEventListener);
    }

    @Test
    public void networkGoesOfflineDoesNothing() {
        mStorage.onNetworkStateUpdated(false);
        verifyZeroInteractions(mHttpClient);
        verifyZeroInteractions(mLocalDocumentStorage);
        verifyZeroInteractions(mDataStoreEventListener);
    }

    @Test
    public void tokenExchangeCallFailsOnDelete() throws JSONException {
        verifyTokenExchangeCallFails(PENDING_OPERATION_DELETE_VALUE);
    }

    @Test
    public void tokenExchangeCallFailsOnCreateOrUpdate() throws JSONException {
        verifyTokenExchangeCallFails(PENDING_OPERATION_CREATE_VALUE);
    }

    private void verifyTokenExchangeCallFails(String operation) throws JSONException {
        final PendingOperation pendingOperation = new PendingOperation(
                USER_TABLE_NAME,
                operation,
                RESOLVED_USER_PARTITION,
                DOCUMENT_ID,
                "document",
                BaseOptions.DEFAULT_EXPIRATION_IN_SECONDS);
        when(mLocalDocumentStorage.getPendingOperations(USER_TABLE_NAME)).thenReturn(
                new ArrayList<PendingOperation>() {{
                    add(pendingOperation);
                }});

        Storage.setDataStoreRemoteOperationListener(mDataStoreEventListener);
        mStorage.onNetworkStateUpdated(true);
        verifyTokenExchangeFlow(null, new Exception("Yeah, it failed."));

        ArgumentCaptor<DocumentError> documentErrorArgumentCaptor = ArgumentCaptor.forClass(DocumentError.class);
        verify(mDataStoreEventListener).onDataStoreOperationResult(
                eq(operation),
                isNull(DocumentMetadata.class),
                documentErrorArgumentCaptor.capture());
        DocumentError documentError = documentErrorArgumentCaptor.getValue();
        assertNotNull(documentError);
        verifyNoMoreInteractions(mDataStoreEventListener);
    }

    @Test
    public void pendingDeleteOperationSuccess() throws JSONException {
        final PendingOperation pendingOperation = new PendingOperation(
                USER_TABLE_NAME,
                PENDING_OPERATION_DELETE_VALUE,
                RESOLVED_USER_PARTITION,
                DOCUMENT_ID,
                "document",
                BaseOptions.DEFAULT_EXPIRATION_IN_SECONDS);
        when(mLocalDocumentStorage.getPendingOperations(USER_TABLE_NAME)).thenReturn(
                new ArrayList<PendingOperation>() {{
                    add(pendingOperation);
                }});
        ArgumentCaptor<DocumentMetadata> documentMetadataArgumentCaptor = ArgumentCaptor.forClass(DocumentMetadata.class);

        Storage.setDataStoreRemoteOperationListener(mDataStoreEventListener);
        mStorage.onNetworkStateUpdated(true);

        verifyTokenExchangeToCosmosDbFlow(DOCUMENT_ID, TOKEN_EXCHANGE_USER_PAYLOAD, METHOD_DELETE, "", null);

        verify(mDataStoreEventListener).onDataStoreOperationResult(
                eq(PENDING_OPERATION_DELETE_VALUE),
                documentMetadataArgumentCaptor.capture(),
                isNull(DocumentError.class));
        DocumentMetadata documentMetadata = documentMetadataArgumentCaptor.getValue();
        assertNotNull(documentMetadata);
        verifyNoMoreInteractions(mDataStoreEventListener);

        assertEquals(DOCUMENT_ID, documentMetadata.getDocumentId());
        assertEquals(RESOLVED_USER_PARTITION, documentMetadata.getPartition());
        assertNull(documentMetadata.getEtag());

        verify(mLocalDocumentStorage).resetPendingOperationColumnToNull(eq(pendingOperation));
    }

    @Test
    public void pendingDeleteOperationWithConflict() throws JSONException {
        final PendingOperation pendingOperation = new PendingOperation(
                USER_TABLE_NAME,
                PENDING_OPERATION_DELETE_VALUE,
                RESOLVED_USER_PARTITION,
                DOCUMENT_ID,
                "document",
                BaseOptions.DEFAULT_EXPIRATION_IN_SECONDS);
        when(mLocalDocumentStorage.getPendingOperations(USER_TABLE_NAME)).thenReturn(
                new ArrayList<PendingOperation>() {{
                    add(pendingOperation);
                }});
        ArgumentCaptor<DocumentError> documentErrorArgumentCaptor = ArgumentCaptor.forClass(DocumentError.class);

        Storage.setDataStoreRemoteOperationListener(mDataStoreEventListener);
        mStorage.onNetworkStateUpdated(true);

        HttpException cosmosFailureException = new HttpException(409, "Conflict");
        verifyTokenExchangeToCosmosDbFlow(DOCUMENT_ID, TOKEN_EXCHANGE_USER_PAYLOAD, METHOD_DELETE, null, cosmosFailureException);

        verify(mDataStoreEventListener).onDataStoreOperationResult(
                eq(pendingOperation.getOperation()),
                isNull(DocumentMetadata.class),
                documentErrorArgumentCaptor.capture());
        DocumentError documentError = documentErrorArgumentCaptor.getValue();
        assertNotNull(documentError);
        verifyNoMoreInteractions(mDataStoreEventListener);

        assertEquals(cosmosFailureException, documentError.getError().getCause());

        verify(mLocalDocumentStorage).deletePendingOperation(eq(pendingOperation));
    }

    @Test
    public void pendingDeleteOperationWithConflictNoListener() throws JSONException {
        final PendingOperation pendingOperation = new PendingOperation(
                USER_TABLE_NAME,
                PENDING_OPERATION_DELETE_VALUE,
                RESOLVED_USER_PARTITION,
                DOCUMENT_ID,
                "document",
                BaseOptions.DEFAULT_EXPIRATION_IN_SECONDS);
        when(mLocalDocumentStorage.getPendingOperations(USER_TABLE_NAME)).thenReturn(
                new ArrayList<PendingOperation>() {{
                    add(pendingOperation);
                }});

        mStorage.onNetworkStateUpdated(true);

        HttpException cosmosFailureException = new HttpException(409, "Conflict");
        verifyTokenExchangeToCosmosDbFlow(DOCUMENT_ID, TOKEN_EXCHANGE_USER_PAYLOAD, METHOD_DELETE, null, cosmosFailureException);

        verify(mLocalDocumentStorage).deletePendingOperation(eq(pendingOperation));
    }
}
