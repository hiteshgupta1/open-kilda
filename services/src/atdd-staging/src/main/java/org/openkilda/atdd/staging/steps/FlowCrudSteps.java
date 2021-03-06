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

package org.openkilda.atdd.staging.steps;

import static com.nitorcreations.Matchers.reflectEquals;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isIn;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.openkilda.atdd.staging.helpers.FlowSet;
import org.openkilda.atdd.staging.helpers.TopologyUnderTest;
import org.openkilda.atdd.staging.service.flowmanager.FlowManager;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.FlowPair;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.northbound.dto.flows.FlowValidationDto;
import org.openkilda.testing.model.topology.TopologyDefinition;
import org.openkilda.testing.model.topology.TopologyDefinition.Switch;
import org.openkilda.testing.service.database.Database;
import org.openkilda.testing.service.floodlight.FloodlightService;
import org.openkilda.testing.service.floodlight.model.MeterEntry;
import org.openkilda.testing.service.floodlight.model.MetersEntriesMap;
import org.openkilda.testing.service.northbound.NorthboundService;
import org.openkilda.testing.service.traffexam.FlowNotApplicableException;
import org.openkilda.testing.service.traffexam.OperationalException;
import org.openkilda.testing.service.traffexam.TraffExamService;
import org.openkilda.testing.service.traffexam.model.Exam;
import org.openkilda.testing.service.traffexam.model.ExamReport;
import org.openkilda.testing.service.traffexam.model.ExamResources;
import org.openkilda.testing.tools.FlowTrafficExamBuilder;
import org.openkilda.testing.tools.SoftAssertions;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.api.java8.En;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.collections4.ListValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
public class FlowCrudSteps implements En {

    @Autowired
    private NorthboundService northboundService;

    @Autowired
    private FloodlightService floodlightService;

    @Autowired
    private Database db;

    @Autowired
    private TopologyDefinition topologyDefinition;

    @Autowired
    private TraffExamService traffExam;

    @Autowired
    private FlowManager flowManager;

    @Autowired
    @Qualifier("topologyUnderTest")
    private TopologyUnderTest topologyUnderTest;

    private Set<FlowPayload> flows;
    private Set<FlowPathPayload> flowPaths = new HashSet<>();
    private FlowPayload flowResponse;

    @Given("^flows defined over active switches in the reference topology$")
    public void defineFlowsOverActiveSwitches() {
        flows = flowManager.allActiveSwitchesFlows();
    }

    @Given("Create (\\d+) flows? with A Switch used and at least (\\d+) alternate paths? between source and "
            + "destination switch and (\\d+) bandwidth")
    public void flowsWithAlternatePaths(int flowsAmount, int alternatePaths, int bw) {
        Map<FlowPayload, List<TopologyDefinition.Isl>> flowIsls = topologyUnderTest.getFlowIsls();
        flowIsls.putAll(flowManager.createFlowsWithASwitch(flowsAmount, alternatePaths, bw));
        //temporary resaving flows before refactoring all methods to work with topologyUnderTest
        flows = flowIsls.keySet();
        flows.forEach(flow -> flowPaths.add(northboundService.getFlowPath(flow.getId())));
    }

    @And("^flow paths? (?:is|are) changed")
    public void flowPathIsChanged() {
        Set<FlowPathPayload> actualFlowPaths = new HashSet<>();
        flows.forEach(flow -> actualFlowPaths.add(northboundService.getFlowPath(flow.getId())));
        assertThat(actualFlowPaths, everyItem(not(isIn(flowPaths))));

        // Save actual flow paths
        flowPaths = actualFlowPaths;
    }

    @And("^flow paths? (?:is|are) unchanged")
    public void flowPathIsUnchanged() {
        Set<FlowPathPayload> actualFlowPaths = new HashSet<>();
        flows.forEach(flow -> actualFlowPaths.add(northboundService.getFlowPath(flow.getId())));
        assertThat(actualFlowPaths, everyItem(isIn(flowPaths)));
    }

    @And("Create defined flows?")
    public void createFlows() {
        eachFlowIsCreatedAndStoredInTopologyEngine();
        eachFlowIsInUpState();
    }

    @And("^each flow has unique flow_id$")
    public void setUniqueFlowIdToEachFlow() {
        flows.forEach(flow -> flow.setId(format("%s-%s", flow.getId(), UUID.randomUUID().toString())));
    }

    @And("^(?:each )?flow has max bandwidth set to (\\d+)$")
    public void setBandwidthToEachFlow(int bandwidth) {
        flows.forEach(flow -> flow.setMaximumBandwidth(bandwidth));
    }

    @When("^initialize creation of given flows$")
    public void creationRequestForEachFlowIsSuccessful() {
        for (FlowPayload flow : flows) {
            FlowPayload result = northboundService.addFlow(flow);
            assertThat(format("A flow creation request for '%s' failed.", flow.getId()), result,
                    reflectEquals(flow, "lastUpdated", "status"));
            assertThat(format("Flow status for '%s' was not set to '%s'. Received status: '%s'",
                    flow.getId(), FlowState.ALLOCATED, result.getStatus()),
                    result.getStatus(), equalTo(FlowState.ALLOCATED.toString()));
            assertThat(format("The flow '%s' is missing lastUpdated field", flow.getId()), result,
                    hasProperty("lastUpdated", notNullValue()));
        }
    }

    @Then("^each flow is created and stored in TopologyEngine$")
    public void eachFlowIsCreatedAndStoredInTopologyEngine() {
        List<Flow> expextedFlows = flows.stream()
                .map(flow -> new Flow(flow.getId(),
                        flow.getMaximumBandwidth(),
                        flow.isIgnoreBandwidth(), flow.isPeriodicPings(), 0,
                        flow.getDescription(), null,
                        flow.getSource().getDatapath(),
                        flow.getDestination().getDatapath(),
                        flow.getSource().getPortNumber(),
                        flow.getDestination().getPortNumber(),
                        flow.getSource().getVlanId(),
                        flow.getDestination().getVlanId(),
                        0, 0, null, null))
                .collect(toList());

        for (Flow expectedFlow : expextedFlows) {
            FlowPair<Flow, Flow> flowPair = Failsafe.with(retryPolicy()
                    .retryWhen(null))
                    .get(() -> db.getFlow(expectedFlow.getFlowId()));

            assertNotNull(format("The flow '%s' is missing in TopologyEngine.", expectedFlow.getFlowId()), flowPair);
            assertThat(format("The flow '%s' in TopologyEngine is different from defined.", expectedFlow.getFlowId()),
                    flowPair.getLeft(), is(equalTo(expectedFlow)));
        }
    }

    @And("^(?:each )?flow is in UP state$")
    public void eachFlowIsInUpState() {
        eachFlowIsUp(flows);
    }

    private void eachFlowIsUp(Set<FlowPayload> flows) {
        for (FlowPayload flow : flows) {
            FlowIdStatusPayload status = Failsafe.with(retryPolicy()
                    .retryIf(p -> p == null || ((FlowIdStatusPayload) p).getStatus() != FlowState.UP))
                    .get(() -> northboundService.getFlowStatus(flow.getId()));

            assertNotNull(format("The flow status for '%s' can't be retrived from Northbound.", flow.getId()), status);
            assertThat(format("The flow '%s' in Northbound is different from defined.", flow.getId()),
                    status, hasProperty("id", equalTo(flow.getId())));
            assertThat(format("The flow '%s' has wrong status in Northbound.", flow.getId()),
                    status, hasProperty("status", equalTo(FlowState.UP)));
        }
    }

    @And("^each flow can be read from Northbound$")
    public void eachFlowCanBeReadFromNorthbound() {
        for (FlowPayload flow : flows) {
            FlowPayload result = northboundService.getFlow(flow.getId());

            assertNotNull(format("The flow '%s' is missing in Northbound.", flow.getId()), result);
            assertEquals(format("The flow '%s' in Northbound is different from defined.", flow.getId()), flow.getId(),
                    result.getId());
        }
    }

    @And("^(?:each )?flow is valid per Northbound validation$")
    public void eachFlowIsValid() {
        flows.forEach(flow -> {
            List<FlowValidationDto> validations = northboundService.validateFlow(flow.getId());
            validations.forEach(flowValidation -> {
                assertEquals(flow.getId(), flowValidation.getFlowId());
                assertTrue(format("The flow '%s' has discrepancies: %s", flow.getId(),
                        flowValidation.getDiscrepancies()), flowValidation.getDiscrepancies().isEmpty());
                assertTrue(format("The flow '%s' didn't pass validation.", flow.getId()),
                        flowValidation.getAsExpected());
            });

        });
    }

    @And("^(?:each )?flow has traffic going with bandwidth not less than (\\d+) and not greater than (\\d+)$")
    public void eachFlowHasTrafficGoingWithBandwidthNotLessThan(int bandwidthLowLimit, int bandwidthHighLimit)
            throws Throwable {
        List<Exam> examsInProgress = buildAndStartTraffExams();
        SoftAssertions softAssertions = new SoftAssertions();
        List<String> issues = new ArrayList<>();

        for (Exam exam : examsInProgress) {
            String flowId = exam.getFlow().getId();

            ExamReport report = traffExam.waitExam(exam);
            softAssertions.checkThat(format("The flow %s had errors: %s",
                    flowId, report.getErrors()), report.hasError(), is(false));
            softAssertions.checkThat(format("The flow %s had no traffic.", flowId),
                    report.hasTraffic(), is(true));
            softAssertions.checkThat(format("The flow %s had unexpected bandwidth: %s", flowId, report.getBandwidth()),
                    report.getBandwidth().getKbps() > bandwidthLowLimit
                            && report.getBandwidth().getKbps() < bandwidthHighLimit, is(true));
        }
        softAssertions.verify();
    }

    @And("^each flow has no traffic$")
    public void eachFlowHasNoTraffic() {
        List<Exam> examsInProgress = buildAndStartTraffExams();

        List<ExamReport> hasTraffic = examsInProgress.stream()
                .map(exam -> traffExam.waitExam(exam))
                .filter(ExamReport::hasTraffic)
                .collect(toList());

        assertThat("Detected unexpected traffic.", hasTraffic, empty());
    }

    private List<Exam> buildAndStartTraffExams() {
        FlowTrafficExamBuilder examBuilder = new FlowTrafficExamBuilder(topologyDefinition, traffExam);

        List<Exam> result = flows.stream()
                .flatMap(flow -> {
                    try {
                        // Instruct TraffGen to produce traffic with maximum bandwidth.
                        return Stream.of(examBuilder.buildExam(flow, 0));
                    } catch (FlowNotApplicableException ex) {
                        log.info("Skip traffic exam. {}", ex.getMessage());
                        return Stream.empty();
                    }
                })
                .peek(exam -> {
                    try {
                        ExamResources resources = traffExam.startExam(exam);
                        exam.setResources(resources);
                    } catch (OperationalException ex) {
                        log.error("Unable to start traffic exam for {}.", exam.getFlow(), ex);
                        fail(ex.getMessage());
                    }
                })
                .collect(toList());

        log.info("{} of {} flow's traffic examination have been started", result.size(),
                flows.size());

        return result;
    }

    @Then("^each flow can be updated with (\\d+) max bandwidth( and new vlan)?$")
    public void eachFlowCanBeUpdatedWithBandwidth(int bandwidth, String newVlanStr) {
        final boolean newVlan = newVlanStr != null;
        for (FlowPayload flow : flows) {
            flow.setMaximumBandwidth(bandwidth);
            if (newVlan) {
                flow.getDestination().setVlanId(getAllowedVlan(flows, flow.getDestination().getSwitchDpId()));
                flow.getSource().setVlanId(getAllowedVlan(flows, flow.getSource().getSwitchDpId()));
            }
            FlowPayload result = northboundService.updateFlow(flow.getId(), flow);
            assertThat(format("A flow update request for '%s' failed.", flow.getId()), result,
                    reflectEquals(flow, "lastUpdated", "status"));
        }
    }

    private int getAllowedVlan(Set<FlowPayload> flows, SwitchId switchDpId) {
        RangeSet<Integer> allocatedVlans = TreeRangeSet.create();
        flows.forEach(f -> {
            allocatedVlans.add(Range.singleton(f.getSource().getVlanId()));
            allocatedVlans.add(Range.singleton(f.getDestination().getVlanId()));
        });
        RangeSet<Integer> availableVlansRange = TreeRangeSet.create();
        Switch theSwitch = topologyDefinition.getSwitches().stream()
                .filter(sw -> sw.getDpId().equals(switchDpId)).findFirst().get();
        theSwitch.getOutPorts().forEach(port -> availableVlansRange.addAll(port.getVlanRange()));
        availableVlansRange.removeAll(allocatedVlans);
        return availableVlansRange.asRanges().stream()
                .flatMap(range -> ContiguousSet.create(range, DiscreteDomain.integers()).stream())
                .findFirst().get();
    }

    @And("^each flow has meters installed with (\\d+) max bandwidth$")
    public void eachFlowHasMetersInstalledWithBandwidth(long bandwidth) {
        for (FlowPayload flow : flows) {
            FlowPair<Flow, Flow> flowPair = db.getFlow(flow.getId());

            try {
                MetersEntriesMap forwardSwitchMeters = floodlightService
                        .getMeters(flowPair.getLeft().getSourceSwitch());
                int forwardMeterId = flowPair.getLeft().getMeterId();
                assertThat(forwardSwitchMeters, hasKey(forwardMeterId));
                MeterEntry forwardMeter = forwardSwitchMeters.get(forwardMeterId);
                assertThat(forwardMeter.getEntries(), contains(hasProperty("rate", equalTo(bandwidth))));

                MetersEntriesMap reverseSwitchMeters = floodlightService
                        .getMeters(flowPair.getRight().getSourceSwitch());
                int reverseMeterId = flowPair.getRight().getMeterId();
                assertThat(reverseSwitchMeters, hasKey(reverseMeterId));
                MeterEntry reverseMeter = reverseSwitchMeters.get(reverseMeterId);
                assertThat(reverseMeter.getEntries(), contains(hasProperty("rate", equalTo(bandwidth))));
            } catch (UnsupportedOperationException ex) {
                //TODO: a workaround for not implemented dumpMeters on OF_12 switches.
                log.warn("Switch doesn't support dumping of meters. {}", ex.getMessage());
            }
        }
    }

    @And("^all active switches have no excessive meters installed$")
    public void noExcessiveMetersInstalledOnActiveSwitches() {
        ListValuedMap<SwitchId, Integer> switchMeters = new ArrayListValuedHashMap<>();
        for (FlowPayload flow : flows) {
            FlowPair<Flow, Flow> flowPair = db.getFlow(flow.getId());
            if (flowPair != null) {
                switchMeters.put(flowPair.getLeft().getSourceSwitch(), flowPair.getLeft().getMeterId());
                switchMeters.put(flowPair.getRight().getSourceSwitch(), flowPair.getRight().getMeterId());
            }
        }

        List<TopologyDefinition.Switch> switches = topologyDefinition.getActiveSwitches();
        switches.forEach(sw -> {
            List<Integer> expectedMeters = switchMeters.get(sw.getDpId());
            try {
                List<Integer> actualMeters = floodlightService.getMeters(sw.getDpId()).values().stream()
                        .map(MeterEntry::getMeterId)
                        .collect(toList());

                if (!expectedMeters.isEmpty() || !actualMeters.isEmpty()) {
                    assertThat(format("Meters of switch %s don't match expected.", sw), actualMeters,
                            containsInAnyOrder(expectedMeters));
                }

            } catch (UnsupportedOperationException ex) {
                //TODO: a workaround for not implemented dumpMeters on OF_12 switches.
                log.warn("Switch doesn't support dumping of meters. {}", ex.getMessage());
            }
        });
    }

    @Then("^each flow can be deleted$")
    public void eachFlowCanBeDeleted() {
        List<String> deletedFlowIds = new ArrayList<>();

        for (FlowPayload flow : flows) {
            FlowPayload result = northboundService.deleteFlow(flow.getId());
            if (result != null) {
                deletedFlowIds.add(result.getId());
            }
        }

        assertThat("Deleted flows from Northbound don't match expected", deletedFlowIds, containsInAnyOrder(
                flows.stream().map(flow -> equalTo(flow.getId())).collect(toList())));
    }

    @And("^each flow can not be read from Northbound$")
    public void eachFlowCanNotBeReadFromNorthbound() {
        for (FlowPayload flow : flows) {
            FlowPayload result = Failsafe.with(retryPolicy()
                    .abortWhen(null)
                    .retryIf(Objects::nonNull))
                    .get(() -> northboundService.getFlow(flow.getId()));

            assertNull(format("The flow '%s' exists.", flow.getId()), result);
        }
    }

    @And("^each flow can not be read from TopologyEngine$")
    public void eachFlowCanNotBeReadFromTopologyEngine() {
        for (FlowPayload flow : flows) {
            FlowPair<Flow, Flow> result = Failsafe.with(retryPolicy()
                    .abortWhen(null)
                    .retryIf(Objects::nonNull))
                    .get(() -> db.getFlow(flow.getId()));

            assertNull(format("The flow '%s' exists.", flow.getId()), result);
        }
    }

    @And("^create flow between '(.*)' and '(.*)' and alias it as '(.*)'$")
    public void createFlowBetween(String srcAlias, String dstAlias, String flowAlias) {
        Switch srcSwitch = topologyUnderTest.getAliasedObject(srcAlias);
        Switch dstSwitch = topologyUnderTest.getAliasedObject(dstAlias);
        FlowPayload flow = new FlowSet().buildWithAnyPortsInUniqueVlan("auto" + getTimestamp(),
                srcSwitch, dstSwitch, 1000);
        northboundService.addFlow(flow);
        topologyUnderTest.addAlias(flowAlias, flow);
    }

    @And("^'(.*)' flow is in UP state$")
    public void flowIsUp(String flowAlias) {
        FlowPayload flow = topologyUnderTest.getAliasedObject(flowAlias);
        eachFlowIsUp(Collections.singleton(flow));
    }

    @When("^request all switch meters for switch '(.*)' and alias results as '(.*)'$")
    public void requestMeters(String switchAlias, String meterAlias) {
        Switch sw = topologyUnderTest.getAliasedObject(switchAlias);
        topologyUnderTest.addAlias(meterAlias, floodlightService.getMeters(sw.getDpId()));

    }

    @And("^select first meter of '(.*)' and alias it as '(.*)'$")
    public void selectFirstMeter(String metersAlias, String newMeterAlias) {
        MetersEntriesMap meters = topologyUnderTest.getAliasedObject(metersAlias);
        Entry<Integer, MeterEntry> firstMeter = meters.entrySet().iterator().next();
        topologyUnderTest.addAlias(newMeterAlias, firstMeter);
    }

    @Then("^meters '(.*)' does not have '(.*)'$")
    public void doesNotHaveMeter(String metersAlias, String meterAlias) {
        MetersEntriesMap meters = topologyUnderTest.getAliasedObject(metersAlias);
        Entry<Integer, MeterEntry> meter = topologyUnderTest.getAliasedObject(meterAlias);
        assertFalse(meters.containsKey(meter.getKey()));
    }

    private RetryPolicy retryPolicy() {
        return new RetryPolicy()
                .withDelay(2, TimeUnit.SECONDS)
                .withMaxRetries(10);
    }

    @Given("^random flow aliased as '(.*)'$")
    public void randomFlowAliasedAsFlow(String flowAlias) {
        topologyUnderTest.addAlias(flowAlias, flowManager.randomFlow());
    }

    @And("^change bandwidth of (.*) flow to (\\d+)$")
    public void changeBandwidthOfFlow(String flowAlias, int newBw) {
        FlowPayload flow = topologyUnderTest.getAliasedObject(flowAlias);
        flow.setMaximumBandwidth(newBw);
    }

    @When("^change bandwidth of (.*) flow to '(.*)'$")
    public void changeBandwidthOfFlow(String flowAlias, String bwAlias) {
        FlowPayload flow = topologyUnderTest.getAliasedObject(flowAlias);
        long bw = topologyUnderTest.getAliasedObject(bwAlias);
        flow.setMaximumBandwidth(bw);
    }

    @And("^create flow '(.*)'$")
    public void createFlow(String flowAlias) {
        FlowPayload flow = topologyUnderTest.getAliasedObject(flowAlias);
        flowResponse = northboundService.addFlow(flow);
    }

    @And("^get available bandwidth and maximum speed for flow (.*) and alias them as '(.*)' "
            + "and '(.*)' respectively$")
    public void getAvailableBandwidthAndSpeed(String flowAlias, String bwAlias, String speedAlias) {
        FlowPayload flow = topologyUnderTest.getAliasedObject(flowAlias);
        List<PathNodePayload> flowPath = northboundService.getFlowPath(flow.getId()).getForwardPath();
        List<IslInfoData> allLinks = northboundService.getAllLinks();
        long minBw = Long.MAX_VALUE;
        long minSpeed = Long.MAX_VALUE;

        /*
        Take flow path and all links. Now for every pair in flow path find a link.
        Take minimum available bandwidth and minimum available speed from those links
        (flow's speed and left bandwidth depends on the weakest isl)
        */
        for (int i = 1; i < flowPath.size(); i++) {
            PathNodePayload from = flowPath.get(i - 1);
            PathNodePayload to = flowPath.get(i);
            IslInfoData isl = allLinks.stream().filter(link ->
                    link.getPath().get(0).getSwitchId().equals(from.getSwitchId())
                            && link.getPath().get(1).getSwitchId().equals(to.getSwitchId()))
                    .findFirst().get();
            minBw = Math.min(isl.getAvailableBandwidth(), minBw);
            minSpeed = Math.min(isl.getSpeed(), minSpeed);
        }
        topologyUnderTest.addAlias(bwAlias, minBw);
        topologyUnderTest.addAlias(speedAlias, minSpeed);
    }

    @And("^update flow (.*)$")
    public void updateFlow(String flowAlias) {
        FlowPayload flow = topologyUnderTest.getAliasedObject(flowAlias);
        flowResponse = northboundService.updateFlow(flow.getId(), flow);
    }

    @When("^get info about flow (.*)$")
    public void getInfoAboutFlow(String flowAlias) {
        FlowPayload flow = topologyUnderTest.getAliasedObject(flowAlias);
        flowResponse = northboundService.getFlow(flow.getId());
    }

    @Then("^response flow has bandwidth equal to '(.*)'$")
    public void responseFlowHasBandwidth(String bwAlias) {
        long expectedBw = topologyUnderTest.getAliasedObject(bwAlias);
        assertThat((long) flowResponse.getMaximumBandwidth(), equalTo(expectedBw));
    }

    @Then("^response flow has bandwidth equal to (\\d+)$")
    public void responseFlowHasBandwidth(long expectedBw) {
        assertThat(flowResponse.getMaximumBandwidth(), equalTo(expectedBw));
    }

    @And("^delete flow (.*)$")
    public void deleteFlow(String flowAlias) {
        FlowPayload flow = topologyUnderTest.getAliasedObject(flowAlias);
        northboundService.deleteFlow(flow.getId());
    }

    @And("^get path of '(.*)' and alias it as '(.*)'$")
    public void getPathAndAlias(String flowAlias, String pathAlias) {
        FlowPayload flow = topologyUnderTest.getAliasedObject(flowAlias);
        topologyUnderTest.addAlias(pathAlias, northboundService.getFlowPath(flow.getId()));
    }

    @And("^(.*) flow's path equals to '(.*)'$")
    public void verifyFlowPath(String flowAlias, String pathAlias) {
        FlowPayload flow = topologyUnderTest.getAliasedObject(flowAlias);
        FlowPathPayload expectedPath = topologyUnderTest.getAliasedObject(pathAlias);
        FlowPathPayload actualPath = northboundService.getFlowPath(flow.getId());
        assertThat(actualPath, equalTo(expectedPath));
    }

    private String getTimestamp() {
        return new SimpleDateFormat("ddMMMHHmm", Locale.US).format(new Date());
    }
}
