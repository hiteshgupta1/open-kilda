package org.bitbucket.openkilda.floodlight.switchmanager;

import org.bitbucket.openkilda.floodlight.kafka.KafkaMessageProducer;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.info.InfoData;
import org.bitbucket.openkilda.messaging.info.InfoMessage;
import org.bitbucket.openkilda.messaging.info.event.PortInfoData;
import org.bitbucket.openkilda.messaging.info.event.SwitchEventType;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class SwitchEventCollector implements IFloodlightModule, IOFSwitchListener, IFloodlightService {
    private static final Logger logger = LoggerFactory.getLogger(SwitchEventCollector.class);
    private static final String TOPIC = "kilda-test";
    private IOFSwitchService switchService;
    private KafkaMessageProducer kafkaProducer;
    private ISwitchManager switchManager;

     /*
      * IOFSwitchListener methods
      */

    private static org.bitbucket.openkilda.messaging.info.event.PortChangeType toJsonType(PortChangeType type) {
        switch (type) {
            case ADD:
                return org.bitbucket.openkilda.messaging.info.event.PortChangeType.ADD;
            case OTHER_UPDATE:
                return org.bitbucket.openkilda.messaging.info.event.PortChangeType.OTHER_UPDATE;
            case DELETE:
                return org.bitbucket.openkilda.messaging.info.event.PortChangeType.DELETE;
            case UP:
                return org.bitbucket.openkilda.messaging.info.event.PortChangeType.UP;
            default:
                return org.bitbucket.openkilda.messaging.info.event.PortChangeType.DOWN;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void switchAdded(final DatapathId switchId) {
        Message message = buildSwitchMessage(switchService.getSwitch(switchId), SwitchEventType.ADDED);
        kafkaProducer.postMessage(TOPIC, message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void switchRemoved(final DatapathId switchId) {
        Message message = buildSwitchMessage(switchService.getSwitch(switchId), SwitchEventType.REMOVED);
        kafkaProducer.postMessage(TOPIC, message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void switchActivated(final DatapathId switchId) {
        Message message = buildSwitchMessage(switchService.getSwitch(switchId), SwitchEventType.ACTIVATED);
        boolean defaultFlowsInstalled = switchManager.installDefaultRules(switchId);
        kafkaProducer.postMessage(TOPIC, message);

        IOFSwitch sw = switchService.getSwitch(switchId);
        if (sw.getEnabledPortNumbers() != null) {
            for (OFPort p : sw.getEnabledPortNumbers()) {
                kafkaProducer.postMessage(TOPIC, buildPortMessage(sw.getId(), p, PortChangeType.UP));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void switchPortChanged(final DatapathId switchId, final OFPortDesc port, final PortChangeType type) {
        Message message = buildPortMessage(switchId, port, type);
        kafkaProducer.postMessage(TOPIC, message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void switchChanged(final DatapathId switchId) {
        Message message = buildSwitchMessage(switchService.getSwitch(switchId), SwitchEventType.CHANGED);
        kafkaProducer.postMessage(TOPIC, message);
    }

    /*
     * IFloodlightModule methods.
     */

    /**
     * {@inheritDoc}
     */
    @Override
    public void switchDeactivated(final DatapathId switchId) {
        Message message = buildSwitchMessage(switchService.getSwitch(switchId), SwitchEventType.DEACTIVATED);
        kafkaProducer.postMessage(TOPIC, message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>(1);
        services.add(SwitchEventCollector.class);
        return services;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> map = new HashMap<>();
        map.put(SwitchEventCollector.class, this);
        return map;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> services = new ArrayList<>(3);
        services.add(IOFSwitchService.class);
        services.add(KafkaMessageProducer.class);
        services.add(ISwitchManager.class);
        return services;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        switchService = context.getServiceImpl(IOFSwitchService.class);
        kafkaProducer = context.getServiceImpl(KafkaMessageProducer.class);
        switchManager = context.getServiceImpl(ISwitchManager.class);
    }

    /*
     * Utility functions
     */

    /**
     * {@inheritDoc}
     */
    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        logger.info("Starting " + SwitchEventCollector.class.getCanonicalName());
        switchService.addOFSwitchListener(this);
    }

    /**
     * Gets hostname by ip address.
     *
     * @param switchAddress switch ip address
     * @return switch name
     */
    private String getNameByAddress(final String switchAddress) {
        String host;
        try {
            InetAddress inetAddress = InetAddress.getByName(switchAddress);
            host = inetAddress.getHostName();
        } catch (UnknownHostException exception) {
            host = "Unknown host";
        }
        return host;
    }

    /**
     * Builds a switch message type.
     *
     * @param sw        switch instance
     * @param eventType type of event
     * @return Message
     */
    private Message buildSwitchMessage(final IOFSwitch sw, final SwitchEventType eventType) {
        String switchAddress = sw.getInetAddress().toString();
        String description = String.format("%s %s", sw.getSwitchDescription().getManufacturerDescription(),
                sw.getSwitchDescription().getSoftwareDescription());
        InfoData data = new SwitchInfoData(sw.getId().toString(), eventType,
                switchAddress, getNameByAddress(switchAddress), description);
        return buildMessage(data);
    }

    /**
     * Builds a generic message object.
     *
     * @param data data to use in the message body
     * @return Message
     */
    private Message buildMessage(final InfoData data) {
        return new InfoMessage(data, System.currentTimeMillis(), "system", null);
    }

    /**
     * Builds a port state change message with port number.
     *
     * @param switchId datapathId of switch
     * @param port     port that triggered the event
     * @param type     type of port event
     * @return Message
     */
    private Message buildPortMessage(final DatapathId switchId, final OFPort port, final PortChangeType type) {
        InfoData data = new PortInfoData(switchId.toString(), port.getPortNumber(), null, toJsonType(type));
        return buildMessage(data);
    }

    /**
     * Builds a port state message with OFPortDesc.
     *
     * @param switchId datapathId of switch
     * @param port     port that triggered the event
     * @param type     type of port event
     * @return Message
     */
    private Message buildPortMessage(final DatapathId switchId, final OFPortDesc port, final PortChangeType type) {
        InfoData data = new PortInfoData(switchId.toString(), port.getPortNo().getPortNumber(), null, toJsonType(type));
        return buildMessage(data);
    }
}