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

package org.openkilda.pce.algo;

import org.openkilda.messaging.model.SwitchId;
import org.openkilda.pce.model.AvailableNetwork;
import org.openkilda.pce.model.SimpleIsl;

import org.junit.Test;

import java.util.LinkedList;

public class SimpleGetShortestPathTest {

    /**
     * Build test network.
     */
    public AvailableNetwork buildNetwork1() {
        AvailableNetwork network = new AvailableNetwork(null);
        network.addLink(new SwitchId("00:00:00:22:3d:5a:04:87"), new SwitchId("00:00:b0:d2:f5:00:5a:b8"),
                7, 60, 0, 3);
        network.addLink(new SwitchId("00:00:00:22:3d:5a:04:87"), new SwitchId("00:00:70:72:cf:d2:48:6c"),
                5, 32, 10, 18);
        network.addLink(new SwitchId("00:00:00:22:3d:5a:04:87"), new SwitchId("00:00:00:22:3d:6b:00:04"),
                2, 2, 10, 2);
        network.addLink(new SwitchId("00:00:00:22:3d:5a:04:87"), new SwitchId("00:00:70:72:cf:d2:47:a6"),
                6, 16, 10, 15);
        network.addLink(new SwitchId("00:00:00:22:3d:5a:04:87"), new SwitchId("00:00:00:22:3d:6c:00:b8"),
                1, 3, 40, 4);
        network.addLink(new SwitchId("00:00:00:22:3d:6b:00:04"), new SwitchId("00:00:00:22:3d:6c:00:b8"),
                1, 1, 100, 7);
        network.addLink(new SwitchId("00:00:00:22:3d:6b:00:04"), new SwitchId("00:00:00:22:3d:5a:04:87"),
                2, 2, 10, 1);
        network.addLink(new SwitchId("00:00:00:22:3d:6c:00:b8"), new SwitchId("00:00:b0:d2:f5:00:5a:b8"),
                6, 19, 10, 3);
        network.addLink(new SwitchId("00:00:00:22:3d:6c:00:b8"), new SwitchId("00:00:00:22:3d:6b:00:04"),
                1, 1, 100, 2);
        network.addLink(new SwitchId("00:00:00:22:3d:6c:00:b8"), new SwitchId("00:00:00:22:3d:5a:04:87"),
                3, 1, 100, 2);
        network.addLink(new SwitchId("00:00:70:72:cf:d2:47:a6"), new SwitchId("00:00:70:72:cf:d2:48:6c"),
                52, 52, 10, 381);
        network.addLink(new SwitchId("00:00:70:72:cf:d2:47:a6"), new SwitchId("00:00:00:22:3d:5a:04:87"),
                16, 6, 10, 18);
        network.addLink(new SwitchId("00:00:70:72:cf:d2:48:6c"), new SwitchId("00:00:b0:d2:f5:00:5a:b8"),
                48, 49, 10, 97);
        network.addLink(new SwitchId("00:00:70:72:cf:d2:48:6c"), new SwitchId("00:00:70:72:cf:d2:47:a6"),
                52, 52, 10, 1021);
        network.addLink(new SwitchId("00:00:70:72:cf:d2:48:6c"), new SwitchId("00:00:00:22:3d:5a:04:87"),
                32, 5, 10, 16);
        network.addLink(new SwitchId("00:00:b0:d2:f5:00:5a:b8"), new SwitchId("00:00:70:72:cf:d2:48:6c"),
                49, 48, 10, 0);
        network.addLink(new SwitchId("00:00:b0:d2:f5:00:5a:b8"), new SwitchId("00:00:00:22:3d:6c:00:b8"),
                19, 6, 10, 3);
        network.addLink(new SwitchId("00:00:b0:d2:f5:00:5a:b8"), new SwitchId("00:00:00:22:3d:5a:04:87"),
                50, 7, 0, 3);
        return network;
    }


    @Test
    public void getPath() {

        AvailableNetwork network = buildNetwork1();
        network.removeSelfLoops().reduceByCost();
        SimpleGetShortestPath forward = new SimpleGetShortestPath(network, new SwitchId("00:00:70:72:cf:d2:47:a6"),
                new SwitchId("00:00:b0:d2:f5:00:5a:b8"), 35);

        LinkedList<SimpleIsl> fpath = forward.getPath();
        System.out.println("forward.getPath() = " + fpath);
        SimpleGetShortestPath reverse = new SimpleGetShortestPath(network, new SwitchId("00:00:b0:d2:f5:00:5a:b8"),
                new SwitchId("00:00:70:72:cf:d2:47:a6"), 35);
        LinkedList<SimpleIsl> rpath = reverse.getPath(fpath);
        System.out.println("reverse.getPath() = " + rpath);


    }
}
