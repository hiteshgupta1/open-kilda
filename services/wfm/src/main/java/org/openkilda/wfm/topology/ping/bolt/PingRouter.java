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

package org.openkilda.wfm.topology.ping.bolt;

import org.openkilda.messaging.floodlight.response.PingResponse;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.ping.model.PingContext;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class PingRouter extends AbstractBolt {
    public static final String BOLT_ID = ComponentId.PING_ROUTER.toString();

    public static final String FIELD_ID_PING_ID = "ping.id";
    public static final String FIELD_ID_PING_MATCH = "ping.match";
    public static final String FIELD_ID_RESPONSE = "ping.response";

    public static final Fields STREAM_BLACKLIST_FILTER_FIELDS = new Fields(
            FIELD_ID_PING_MATCH, FIELD_ID_PING, FIELD_ID_CONTEXT);
    public static final String STREAM_BLACKLIST_FILTER_ID = "blacklist";

    public static final Fields STREAM_REQUEST_FIELDS = new Fields(
            FIELD_ID_PING_ID, FIELD_ID_PING, FIELD_ID_CONTEXT);
    public static final String STREAM_REQUEST_ID = "request";

    public static final Fields STREAM_RESPONSE_FIELDS = new Fields(
            FIELD_ID_PING_ID, FIELD_ID_RESPONSE, FIELD_ID_CONTEXT);
    public static final String STREAM_RESPONSE_ID = "response";

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        String component = input.getSourceComponent();
        if (PingProducer.BOLT_ID.equals(component)) {
            routePingProducer(input);
        } else if (Blacklist.BOLT_ID.equals(component)) {
            routeBlacklist(input);
        } else if (SpeakerDecoder.BOLT_ID.equals(component)) {
            routeSpeakerDecoder(input);
        } else {
            unhandledInput(input);
        }
    }

    private void routePingProducer(Tuple input) throws PipelineException {
        PingContext pingContext = pullPingContext(input);
        CommandContext commandContext = pullContext(input);

        Values payload = new Values(pingContext.getPing(), pingContext, commandContext);
        getOutput().emit(STREAM_BLACKLIST_FILTER_ID, input, payload);
    }

    private void routeBlacklist(Tuple input) throws PipelineException {
        final PingContext pingContext = pullPingContext(input);
        Values payload = new Values(pingContext.getPingId(), pingContext, pullContext(input));
        getOutput().emit(STREAM_REQUEST_ID, input, payload);
    }

    private void routeSpeakerDecoder(Tuple input) throws PipelineException {
        PingResponse response = pullFlowResponse(input);
        Values payload = new Values(response.getPing().getPingId(), response, pullContext(input));
        getOutput().emit(STREAM_RESPONSE_ID, input, payload);
    }

    private PingResponse pullFlowResponse(Tuple input) throws PipelineException {
        PingResponse value;
        try {
            value = (PingResponse) input.getValueByField(SpeakerDecoder.FIELD_ID_RESPONSE);
        } catch (ClassCastException e) {
            throw new PipelineException(this, input, SpeakerDecoder.FIELD_ID_RESPONSE, e.toString());
        }
        return value;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declareStream(STREAM_BLACKLIST_FILTER_ID, STREAM_BLACKLIST_FILTER_FIELDS);
        outputManager.declareStream(STREAM_REQUEST_ID, STREAM_REQUEST_FIELDS);
        outputManager.declareStream(STREAM_RESPONSE_ID, STREAM_RESPONSE_FIELDS);
    }
}
