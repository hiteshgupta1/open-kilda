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

package org.openkilda.controller;

import org.openkilda.auth.model.Permissions;
import org.openkilda.constants.IConstants;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;

/**
 * The Class TopologyController.
 *
 * @author Gaurav Chugh
 */
@Controller
@RequestMapping(value = "/topology")
public class TopologyController extends BaseController {

    /**
     * Topology.
     *
     * @param request the request
     * @return the model and view
     */
    @RequestMapping
    @Permissions(values = {IConstants.Permission.MENU_TOPOLOGY})
    public ModelAndView topology(final HttpServletRequest request) {
        return validateAndRedirect(request, IConstants.View.TOPOLOGY);
    }
}
