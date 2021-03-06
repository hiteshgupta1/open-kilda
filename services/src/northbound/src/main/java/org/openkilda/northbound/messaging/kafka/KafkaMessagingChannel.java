/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.northbound.messaging.kafka;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorMessage;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.messaging.info.ChunkedInfoMessage;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.northbound.messaging.MessageProducer;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.messaging.exception.MessageNotSentException;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;

/**
 * Implementation of {@link MessagingChannel} for kafka.
 */
@Component
public class KafkaMessagingChannel implements MessagingChannel {

    private static final Logger logger = LoggerFactory.getLogger(KafkaMessagingChannel.class);

    /**
     * Requests that are in progress of processing.
     */
    private Map<String, CompletableFuture<InfoData>> pendingRequests = new ConcurrentHashMap<>();
    private Map<String, CompletableFuture<List<InfoData>>> pendingChunkedRequests = new ConcurrentHashMap<>();

    /**
     * Chains of chunked messages, it is filling by messages one by one as soon as the next linked message is received.
     */
    private Map<String, List<ChunkedInfoMessage>> messagesChains;

    /**
     * The storage for received chunked message ids. It is needed to identify whether we have already received specific
     * chunked message or not in order to do not have duplicates, because current version of kafka do not guarantee
     * exactly once delivery.
     */
    private Map<String, Set<String>> chunkedMessageIdsPerRequest = new HashMap<>();

    @Value("${northbound.messages.expiration.minutes}")
    private int expiredTime;

    @Autowired
    private MessageProducer messageProducer;

    /**
     * Creates storage for chains of messages.
     */
    @PostConstruct
    public void setUp() {
        messagesChains = new PassiveExpiringMap<>(expiredTime, TimeUnit.MINUTES, new HashMap<>());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<InfoData> sendAndGet(String topic, Message message) {
        CompletableFuture<InfoData> future = new CompletableFuture<>();
        pendingRequests.put(message.getCorrelationId(), future);

        ListenableFuture<SendResult<String, Message>> futureResult = messageProducer.send(topic, message);
        futureResult.addCallback(
                success -> { },
                error -> future.completeExceptionally(new MessageNotSentException(error.getMessage()))
        );

        return future.whenComplete((response, error) -> pendingRequests.remove(message.getCorrelationId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<List<InfoData>> sendAndGetChunked(String topic, Message message) {
        CompletableFuture<List<InfoData>> future = new CompletableFuture<>();
        pendingChunkedRequests.put(message.getCorrelationId(), future);

        messagesChains.put(message.getCorrelationId(), new ArrayList<>());
        chunkedMessageIdsPerRequest.put(message.getCorrelationId(), new HashSet<>());

        ListenableFuture<SendResult<String, Message>> futureResult = messageProducer.send(topic, message);
        futureResult.addCallback(
                sentResult -> { },
                error -> future.completeExceptionally(new MessageNotSentException(error.getMessage()))
        );

        return future.whenComplete((response, error) -> {
            pendingChunkedRequests.remove(message.getCorrelationId());

            messagesChains.remove(message.getCorrelationId());
            chunkedMessageIdsPerRequest.remove(message.getCorrelationId());
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void send(String topic, Message message) {
        messageProducer.send(topic, message);
    }

    /**
     * Processes messages that come back to NB topics, usually messages come as a response to some request.
     * If this response is for pended request then such request will be marked as completed.
     * Messages might be chunked and not chunked. If chunked we need to wait until we receive the last one
     * and only then collect all responses and complete the request.
     *
     * @param message received message.
     */
    void onResponse(Message message) {
        if (!isValid(message)) {
            logger.warn("Skipping invalid message: {}", message);
            return;
        }

        if (message instanceof ErrorMessage) {
            ErrorMessage error = (ErrorMessage) message;
            logger.error("Response message is error: {}", error);

            completeWithError(error);
        } else if (message instanceof InfoMessage) {
            if (isChunkedPendingResponse(message)) {
                processChunkedMessage((ChunkedInfoMessage) message);
            } else if (pendingRequests.containsKey(message.getCorrelationId())) {
                InfoMessage infoMessage = (InfoMessage) message;
                CompletableFuture<InfoData> request = pendingRequests.remove(message.getCorrelationId());
                if (request != null) {
                    request.complete(infoMessage.getData());
                }
            } else {
                logger.trace("Received non-pending message");
            }
        }
    }

    /**
     * Performs searching and collecting all chunked messages into one chain if possible.
     */
    private synchronized void processChunkedMessage(ChunkedInfoMessage received) {
        String requestId = received.getCorrelationId();
        Set<String> associatedMessages = chunkedMessageIdsPerRequest.get(requestId);
        if (!associatedMessages.add(received.getMessageId())) {
            logger.debug("Skipping chunked message, it is already received: {}", received);
            return;
        }

        List<ChunkedInfoMessage> chain = messagesChains.get(requestId);
        if (received.getTotalMessages() != 0) {
            chain.add(received);
        }

        if (chain.size() == received.getTotalMessages()) {
            completeRequest(requestId, chain);
        }
    }

    /**
     * Completes pending request with received responses.
     */
    private void completeRequest(String requestId, List<ChunkedInfoMessage> chain) {
        List<InfoData> response = chain.stream()
                .map(ChunkedInfoMessage::getData)
                .collect(Collectors.toList());

        pendingChunkedRequests
                .get(requestId)
                .complete(response);
    }

    /**
     * Completes a request with an error response.
     */
    private synchronized void completeWithError(ErrorMessage error) {
        String correlationId = error.getCorrelationId();

        CompletableFuture request = null;
        if (pendingRequests.containsKey(correlationId)) {
            request = pendingRequests.remove(correlationId);
        } else if (pendingChunkedRequests.containsKey(correlationId)) {
            request = pendingChunkedRequests.remove(correlationId);
        }

        if (request != null) {
            request.completeExceptionally(new MessageException(error));
        }
    }

    /**
     * Checks whether a message has correlationId and has known type or not.
     */
    private boolean isValid(Message message) {
        if (StringUtils.isEmpty(message.getCorrelationId())) {
            logger.warn("Received message without correlation id: {}", message);
            return false;
        }

        if (!(message instanceof InfoMessage) && !(message instanceof ErrorMessage)) {
            logger.warn("Received message has unsupported format: {}", message);
            return false;
        }

        return true;
    }

    private boolean isChunkedPendingResponse(Message message) {
        return message instanceof ChunkedInfoMessage && pendingChunkedRequests.containsKey(message.getCorrelationId());
    }

    @VisibleForTesting
    Map<String, CompletableFuture<InfoData>> getPendingRequests() {
        return new HashMap<>(pendingRequests);
    }

    @VisibleForTesting
    Map<String, CompletableFuture<List<InfoData>>> getPendingChunkedRequests() {
        return new HashMap<>(pendingChunkedRequests);
    }
}
