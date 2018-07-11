/* Copyright 2017 Telstra Open Source
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

package org.openkilda.messaging.payload.flow;

import static com.google.common.base.MoreObjects.toStringHelper;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.event.PathInfoData;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.Objects;

/**
 * Flow reroute representation class.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        Utils.FLOW_ID,
        Utils.FLOW_PATH})
public class FlowReroutePayload implements Serializable {

    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The id of the flow.
     */
    @JsonProperty(Utils.FLOW_ID)
    protected String id;

    /**
     * The path of the flow.
     */
    @JsonProperty(Utils.FLOW_PATH)
    protected PathInfoData path;

    @JsonProperty(Utils.REROUTED)
    private boolean rerouted;

    public FlowReroutePayload(String id, PathInfoData path, boolean rerouted) {
        setId(id);
        setPath(path);
        this.rerouted = rerouted;
    }

    /**
     * Returns id of the flow.
     *
     * @return id of the flow
     */
    public String getId() {
        return id;
    }

    /**
     * Sets id of the flow.
     *
     * @param id id of the flow
     */
    public void setId(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("need to set id");
        }
        this.id = id;
    }

    /**
     * Returns path of the flow.
     *
     * @return path of the flow
     */
    public PathInfoData getPath() {
        return path;
    }

    /**
     * Sets path of the flow.
     *
     * @param path path of the flow
     */
    public void setPath(PathInfoData path) {
        if (path == null || path.getPath() == null) {
            throw new IllegalArgumentException("need to set path");
        }
        this.path = path;
    }

    public boolean isRerouted() {
        return rerouted;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add(Utils.FLOW_ID, id)
                .add(Utils.FLOW_PATH, path)
                .add(Utils.REROUTED, rerouted)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, path);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        FlowReroutePayload that = (FlowReroutePayload) object;
        return Objects.equals(getId(), that.getId())
                && Objects.equals(getPath(), that.getPath());
    }
}
