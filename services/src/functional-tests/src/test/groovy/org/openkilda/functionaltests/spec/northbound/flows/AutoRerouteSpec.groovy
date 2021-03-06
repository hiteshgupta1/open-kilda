package org.openkilda.functionaltests.spec.northbound.flows

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.tools.IslUtils

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Unroll

import java.util.concurrent.TimeUnit

class AutoRerouteSpec extends BaseSpecification {
    @Autowired
    TopologyDefinition topology
    @Autowired
    FlowHelper flowHelper
    @Autowired
    PathHelper pathHelper
    @Autowired
    NorthboundService northboundService
    @Autowired
    LockKeeperService lockKeeperService
    @Autowired
    Database db
    @Autowired
    IslUtils islUtils

    @Value('${reroute.delay}')
    int rerouteDelay
    @Value('${discovery.interval}')
    int discoveryInterval
    @Value('${discovery.timeout}')
    int discoveryTimeout

    def "Flow goes to 'Down' status when one of the flow ISLs fails and there is no ability to reroute"() {
        given: "A flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.getActiveSwitches()[0..1]
        def allPaths = db.getPaths(srcSwitch.dpId, dstSwitch.dpId)*.path
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        northboundService.addFlow(flow)
        assert Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        def currentPath = PathHelper.convert(northboundService.getFlowPath(flow.id))

        and: "Ports that lead to alternative paths are brought down to deny alternative paths"
        def altPaths = allPaths.findAll { it != currentPath }
        List<PathNode> broughtDownPorts = []
        altPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northboundService.portDown(src.switchId, src.portNo)
        }

        when: "One of the flow's ISLs goes down"
        def isl = pathHelper.getInvolvedIsls(currentPath).first()
        northboundService.portDown(isl.dstSwitch.dpId, isl.dstPort)

        then: "Flow becomes 'Down'"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) { northboundService.getFlowStatus(flow.id).status == FlowState.DOWN }

        when: "ISL goes back up"
        northboundService.portUp(isl.dstSwitch.dpId, isl.dstPort)

        then: "Flow becomes 'Up'"
        Wrappers.wait(rerouteDelay + discoveryInterval + WAIT_OFFSET) {
            northboundService.getFlowStatus(flow.id).status == FlowState.UP
        }

        and: "Restore topology to original state, remove flow"
        broughtDownPorts.every { northboundService.portUp(it.switchId, it.portNo) }
        northboundService.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northboundService.getAllLinks().every { it.state != IslChangeType.FAILED }
        }
    }

    @Unroll
    def "Flow goes to 'Down' status when src or dst switch is disconnected (#description)"() {
        requireProfiles("virtual")

        given: "A flow"
        //TODO(ylobankov): Remove this code once the issue #1464 is resolved.
        assumeTrue("Test is skipped because of the issue #1464", description != "single-switch flow")

        northboundService.addFlow(flow)
        assert Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }

        when: "The source switch is disconnected"
        lockKeeperService.knockoutSwitch(flow.source.datapath)

        then: "Flow becomes 'Down'"
        Wrappers.wait(discoveryTimeout + rerouteDelay + WAIT_OFFSET * 2) {
            northboundService.getFlowStatus(flow.id).status == FlowState.DOWN
        }

        when: "The source switch is connected back"
        lockKeeperService.reviveSwitch(flow.source.datapath)

        then: "Flow becomes 'Up'"
        Wrappers.wait(rerouteDelay + discoveryInterval + WAIT_OFFSET) {
            northboundService.getFlowStatus(flow.id).status == FlowState.UP
        }

        when: "The destination switch is disconnected"
        lockKeeperService.knockoutSwitch(flow.destination.datapath)

        then: "Flow becomes 'Down'"
        Wrappers.wait(discoveryTimeout + rerouteDelay + WAIT_OFFSET * 2) {
            northboundService.getFlowStatus(flow.id).status == FlowState.DOWN
        }

        when: "The destination switch is connected back"
        lockKeeperService.reviveSwitch(flow.destination.datapath)

        then: "Flow becomes 'Up'"
        Wrappers.wait(rerouteDelay + discoveryInterval + WAIT_OFFSET) {
            northboundService.getFlowStatus(flow.id).status == FlowState.UP
        }

        and: "Remove flow"
        northboundService.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northboundService.getAllLinks().every { it.state != IslChangeType.FAILED }
        }

        where:
        description                    | flow
        "single-switch flow"           | singleSwitchFlow()
        "w/o-intermediate-switch flow" | withOutIntermediateSwitchFlow()
        "w/-intermediate-switch flow"  | withIntermediateSwitchFlow()
    }

    def "Flow goes to 'Down' status when an intermediate switch is disconnected and there is no ability to reroute"() {
        requireProfiles("virtual")

        given: "Two active not neighboring switches and a flow without alternative paths"
        def flow = withIntermediateSwitchFlow()
        northboundService.addFlow(flow)
        assert Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        def currentPath = PathHelper.convert(northboundService.getFlowPath(flow.id))

        def allPaths = db.getPaths(flow.source.datapath, flow.destination.datapath)*.path
        def altPaths = allPaths.findAll { it != currentPath && it.first().portNo != currentPath.first().portNo }
        List<PathNode> broughtDownPorts = []
        altPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northboundService.portDown(src.switchId, src.portNo)
        }

        when: "An intermediate switch is disconnected"
        lockKeeperService.knockoutSwitch(currentPath[1].switchId)

        then: "Flow becomes 'Down'"
        Wrappers.wait(discoveryTimeout + rerouteDelay + WAIT_OFFSET * 2) {
            northboundService.getFlowStatus(flow.id).status == FlowState.DOWN
        }

        when: "The intermediate switch is connected back"
        lockKeeperService.reviveSwitch(currentPath[1].switchId)
        assert Wrappers.wait(WAIT_OFFSET) {
            northboundService.activeSwitches*.switchId.contains(currentPath[1].switchId)
        }

        then: "Flow becomes 'Up'"
        Wrappers.wait(rerouteDelay + discoveryInterval + WAIT_OFFSET) {
            northboundService.getFlowStatus(flow.id).status == FlowState.UP
        }

        and: "Restore topology to original state, remove flow"
        broughtDownPorts.every { northboundService.portUp(it.switchId, it.portNo) }
        northboundService.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northboundService.getAllLinks().every { it.state != IslChangeType.FAILED }
        }
    }

    def "Flow in 'Down' status tries to reroute when discovering a new ISL"() {
        given: "Two active switches and a flow with one alternate path at least"
        def switches = topology.getActiveSwitches()
        List<List<PathNode>> possibleFlowPaths = []
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            possibleFlowPaths = db.getPaths(src.dpId, dst.dpId)*.path.sort { it.size() }
            possibleFlowPaths.size() > 1
        } ?: assumeTrue("No suiting switches found", false)

        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = 1000
        northboundService.addFlow(flow)
        assert Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        def flowPath = PathHelper.convert(northboundService.getFlowPath(flow.id))

        when: "Bring all ports down on source switch that are involved in current and alternate paths"
        List<PathNode> broughtDownPorts = []
        possibleFlowPaths.unique { it.first() }.each { path ->
            def src = path.first()
            broughtDownPorts.add(src)
            northboundService.portDown(src.switchId, src.portNo)
        }

        then: "Flow goes to 'Down' status"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) { northboundService.getFlowStatus(flow.id).status == FlowState.DOWN }

        when: "Bring all ports up on source switch that are involved in alternate paths"
        broughtDownPorts.findAll {
            it.portNo != flowPath.first().portNo
        }.each {
            northboundService.portUp(it.switchId, it.portNo)
        }

        then: "Flow goes to 'Up' status"
        Wrappers.wait(rerouteDelay + discoveryInterval + WAIT_OFFSET * 2) {
            northboundService.getFlowStatus(flow.id).status == FlowState.UP
        }

        and: "Flow was rerouted"
        def reroutedFlowPath = PathHelper.convert(northboundService.getFlowPath(flow.id))
        flowPath != reroutedFlowPath
        Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }

        and: "Bring port involved in original path up and delete flow"
        northboundService.portUp(flowPath.first().switchId, flowPath.first().portNo)
        northboundService.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northboundService.getAllLinks().every { it.state != IslChangeType.FAILED }
        }
    }

    def "Flow in 'Up' status doesn't try to reroute even though more preferable path is available"() {
        given: "Two active switches and a flow with one alternate path at least"
        def switches = topology.getActiveSwitches()
        List<List<PathNode>> possibleFlowPaths = []
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            possibleFlowPaths = db.getPaths(src.dpId, dst.dpId)*.path.sort { it.size() }
            possibleFlowPaths.size() > 1
        } ?: assumeTrue("No suiting switches found", false)

        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = 1000
        northboundService.addFlow(flow)
        assert Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }
        def flowPath = PathHelper.convert(northboundService.getFlowPath(flow.id))

        and: "Make current flow path less preferable than others"
        possibleFlowPaths.findAll { it != flowPath }.each { pathHelper.makePathMorePreferable(it, flowPath) }

        when: "One of the links not used by flow goes down"
        def involvedIsls = pathHelper.getInvolvedIsls(flowPath)
        def islToFail = topology.islsForActiveSwitches.find { !involvedIsls.contains(it) }
        northboundService.portDown(islToFail.srcSwitch.dpId, islToFail.srcPort)

        then: "Link status becomes 'FAILED'"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            islUtils.getIslInfo(islToFail).get().state == IslChangeType.FAILED
        }

        when: "Failed link goes up"
        northboundService.portUp(islToFail.srcSwitch.dpId, islToFail.srcPort)

        then: "Link status becomes 'DISCOVERED'"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            islUtils.getIslInfo(islToFail).get().state == IslChangeType.DISCOVERED
        }

        and: "Flow is not rerouted and doesn't use more preferable path"
        TimeUnit.SECONDS.sleep(rerouteDelay + WAIT_OFFSET)
        def flowPathAfter = PathHelper.convert(northboundService.getFlowPath(flow.id))
        flowPath == flowPathAfter
        Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }

        and: "Delete created flow"
        northboundService.deleteFlow(flow.id)
    }

    def singleSwitchFlow() {
        flowHelper.singleSwitchFlow(topology.getActiveSwitches().first())
    }

    def withOutIntermediateSwitchFlow() {
        def isl = topology.getIslsForActiveSwitches().first()
        flowHelper.randomFlow(isl.srcSwitch, isl.dstSwitch)
    }

    def withIntermediateSwitchFlow() {
        def switches = topology.getActiveSwitches()
        def links = northboundService.getAllLinks()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            links.every { link ->
                def switchIds = link.path*.switchId
                !(switchIds.contains(src.dpId) && switchIds.contains(dst.dpId))
            }
        } ?: assumeTrue("No suiting switches found", false)
        flowHelper.randomFlow(srcSwitch, dstSwitch)
    }

    def cleanup() {
        northboundService.deleteLinkProps(northboundService.getAllLinkProps())
        db.resetCosts()
    }
}
