/*
 * Copyright 2016-2021 The jetcd authors
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
 */

package io.etcd.jetcd;

import java.util.concurrent.CompletableFuture;

import io.etcd.jetcd.api.CompactionRequest;
import io.etcd.jetcd.api.KVGrpc;
import io.etcd.jetcd.kv.CompactResponse;
import io.etcd.jetcd.kv.DeleteResponse;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.kv.PutResponse;
import io.etcd.jetcd.kv.TxnResponse;
import io.etcd.jetcd.op.TxnImpl;
import io.etcd.jetcd.options.CompactOption;
import io.etcd.jetcd.options.DeleteOption;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Implementation of etcd kv client.
 */
final class KVImpl implements KV {

    private final ClientConnectionManager connectionManager;

    private final KVGrpc.KVFutureStub stub;

    private final ByteSequence namespace;

    KVImpl(ClientConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.stub = connectionManager.newStub(KVGrpc::newFutureStub);
        this.namespace = connectionManager.getNamespace();
    }

    @Override
    public CompletableFuture<PutResponse> put(ByteSequence key, ByteSequence value) {
        return this.put(key, value, PutOption.DEFAULT);
    }

    @Override
    public CompletableFuture<PutResponse> put(ByteSequence key, ByteSequence value, PutOption option) {
        checkNotNull(key, "key should not be null");
        checkNotNull(value, "value should not be null");
        checkNotNull(option, "option should not be null");
        return connectionManager.execute(
            () -> stub.put(ProtoRequestMapper.mapPutRequest(key, value, option, namespace)),
            response -> new PutResponse(response, namespace),
            Util::isRetryable);
    }

    @Override
    public CompletableFuture<GetResponse> get(ByteSequence key) {
        return this.get(key, GetOption.DEFAULT);
    }

    @Override
    public CompletableFuture<GetResponse> get(ByteSequence key, GetOption option) {
        checkNotNull(key, "key should not be null");
        checkNotNull(option, "option should not be null");
        return connectionManager.execute(
            () -> stub.range(ProtoRequestMapper.mapRangeRequest(key, option, namespace)),
            response -> new GetResponse(response, namespace),
            Util::isRetryable);
    }

    @Override
    public CompletableFuture<DeleteResponse> delete(ByteSequence key) {
        return this.delete(key, DeleteOption.DEFAULT);
    }

    @Override
    public CompletableFuture<DeleteResponse> delete(ByteSequence key, DeleteOption option) {
        checkNotNull(key, "key should not be null");
        checkNotNull(option, "option should not be null");
        return connectionManager.execute(
            () -> stub.deleteRange(ProtoRequestMapper.mapDeleteRequest(key, option, namespace)),
            response -> new DeleteResponse(response, namespace),
            Util::isRetryable);
    }

    @Override
    public CompletableFuture<CompactResponse> compact(long rev) {
        return this.compact(rev, CompactOption.DEFAULT);
    }

    @Override
    public CompletableFuture<CompactResponse> compact(long rev, CompactOption option) {
        checkNotNull(option, "option should not be null");

        CompactionRequest request = CompactionRequest.newBuilder()
            .setRevision(rev).setPhysical(option.isPhysical())
            .build();

        return connectionManager.execute(
            () -> stub.compact(request),
            CompactResponse::new,
            Util::isRetryable);
    }

    @Override
    public Txn txn() {
        return TxnImpl.newTxn(
            request -> connectionManager.execute(
                () -> stub.txn(request),
                response -> new TxnResponse(response, namespace), Util::isRetryable),
            namespace);
    }
}
