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

package org.openkilda.testing.service.mininet;

import java.util.Map;

public interface Mininet {

    Map createTopology(Object topology);

    void deleteTopology();

    void knockoutSwitch(String switchName);

    void revive(String switchName, String controller);

    void addFlow(String switchName, Integer inPort, Integer outPort);

    void removeFlow(String switchName, Integer inPort);

    void portUp(String switchName, Integer port);

    void portDown(String switchName, Integer port);
}
