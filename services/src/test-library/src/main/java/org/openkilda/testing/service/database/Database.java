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

package org.openkilda.testing.service.database;

import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.FlowPair;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.testing.model.topology.TopologyDefinition.Isl;

import java.util.List;

public interface Database {
    boolean updateLinkProperty(Isl isl, String property, Object value);

    boolean revertIslBandwidth(Isl isl);

    boolean removeInactiveIsls();

    boolean removeInactiveSwitches();

    boolean resetCosts();

    int getIslCost(Isl isl);

    int countFlows();

    List<PathInfoData> getPaths(SwitchId src, SwitchId dst);

    FlowPair<Flow, Flow> getFlow(String flowId);
}
