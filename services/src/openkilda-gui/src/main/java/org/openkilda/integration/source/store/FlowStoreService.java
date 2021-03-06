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

package org.openkilda.integration.source.store;

import org.openkilda.integration.auth.service.IAuthService;
import org.openkilda.integration.exception.StoreIntegrationException;
import org.openkilda.integration.source.store.dto.Contract;
import org.openkilda.integration.source.store.dto.InventoryFlow;
import org.openkilda.store.common.constants.RequestParams;
import org.openkilda.store.common.constants.StoreType;
import org.openkilda.store.common.constants.Url;
import org.openkilda.store.model.AuthConfigDto;
import org.openkilda.store.model.UrlDto;
import org.openkilda.store.service.AuthService;
import org.openkilda.store.service.StoreService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Class FlowStoreService.
 *
 */

@Service
public class FlowStoreService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlowStoreService.class);

    @Autowired
    private StoreService storeService;
    
    @Autowired
    private AuthService authService;
    
    /**
     * Gets the all flow list.
     *
     * @return the all flow list
     */
    public List<InventoryFlow> getAllFlows() {
        try {
            UrlDto urlDto = storeService.getUrl(StoreType.LINK_STORE, Url.GET_ALL_LINK);
            AuthConfigDto authDto = authService.getAuth(StoreType.LINK_STORE);
            IAuthService authService = IAuthService.getService(authDto.getAuthType());
            return authService.getResponseList(urlDto, authDto, InventoryFlow.class);
        } catch (Exception exception) {
            LOGGER.error("Exception in getAllFlowList " + exception.getMessage());
            throw new StoreIntegrationException(exception);
        }
    } 
    
    /**
     * Gets the flow by id.
     *
     * @param flowId the flow id
     * @return the flow by id
     */
    public InventoryFlow getFlowById(final String flowId) {
        try {
            UrlDto urlDto = storeService.getUrl(StoreType.LINK_STORE, Url.GET_LINK);

            Map<String, String> params = new HashMap<String, String>();
            params.put(RequestParams.LINK_ID.getName(), flowId);
            
            urlDto.setParams(params);
            
            AuthConfigDto authDto = authService.getAuth(StoreType.LINK_STORE);
            IAuthService authService = IAuthService.getService(authDto.getAuthType());
            return authService.getResponse(urlDto, authDto, InventoryFlow.class);
        } catch (Exception exception) {
            LOGGER.error("Exception in getFlowById " + exception.getMessage());
            throw new StoreIntegrationException(exception);
        }
    }
    
    /**
     * Gets the flows with params.
     *
     * @param status the status
     * @return the flows with params
     */
    public List<InventoryFlow> getFlowsWithParams(final String status) {
        try {
            UrlDto urlDto = storeService.getUrl(StoreType.LINK_STORE, Url.GET_LINKS_WITH_PARAMS);
            
            Map<String, String> params = new HashMap<String, String>();
            params.put(RequestParams.STATUS.getName(), status);
            
            urlDto.setParams(params);
            
            AuthConfigDto authDto = authService.getAuth(StoreType.LINK_STORE);
            IAuthService authService = IAuthService.getService(authDto.getAuthType());
            return authService.getResponseList(urlDto, authDto, InventoryFlow.class);
        } catch (Exception exception) {
            LOGGER.error("Exception in getFlowsWithParams " + exception.getMessage());
            throw new StoreIntegrationException(exception);
        }
    }
    
    /**
     * Gets the all contracts.
     *
     * @param flowId the flow id
     * @return the all contracts
     */
    public List<Contract> getAllContracts(final String flowId) {
        try {
            UrlDto urlDto = storeService.getUrl(StoreType.LINK_STORE, Url.GET_CONTRACT);
            
            Map<String, String> params = new HashMap<String, String>();
            params.put(RequestParams.LINK_ID.getName(), flowId);
            
            urlDto.setParams(params);
            
            AuthConfigDto authDto = authService.getAuth(StoreType.LINK_STORE);
            IAuthService authService = IAuthService.getService(authDto.getAuthType());
            return authService.getResponseList(urlDto, authDto, Contract.class);
        } catch (Exception exception) {
            LOGGER.error("Exception in getAllContracts " + exception.getMessage());
            throw new StoreIntegrationException(exception);
        }
    }
    
    /**
     * Delete contract.
     *
     * @param flowId the flow id
     * @param contractId the contract id
     */
    public void deleteContract(final String flowId, final String contractId) {
        try {
            UrlDto urlDto = storeService.getUrl(StoreType.LINK_STORE, Url.GET_CONTRACT);
            
            Map<String, String> params = new HashMap<String, String>();
            params.put(RequestParams.LINK_ID.getName(), flowId);
            params.put(RequestParams.CONTRACT_ID.getName(), contractId);
            
            urlDto.setParams(params);
            
            AuthConfigDto authDto = authService.getAuth(StoreType.LINK_STORE);
            IAuthService authService = IAuthService.getService(authDto.getAuthType());
            authService.getResponse(urlDto, authDto, null);
        } catch (Exception exception) {
            LOGGER.error("Exception in deleteContract " + exception.getMessage());
            throw new StoreIntegrationException(exception);
        }
    }
}
