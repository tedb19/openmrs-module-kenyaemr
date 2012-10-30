/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.kenyaemr.fragment.controller;

import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.api.context.Context;
import org.openmrs.module.appframework.AppStatus;
import org.openmrs.module.appframework.AppUiUtil;
import org.openmrs.module.kenyaemr.MetadataConstants;
import org.openmrs.ui.framework.WebConstants;
import org.openmrs.ui.framework.fragment.FragmentConfiguration;
import org.openmrs.ui.framework.fragment.FragmentModel;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.ui.framework.session.Session;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Iterator;
import java.util.List;

/**
 * Lists active visits of the given patient
 */
public class ActiveVisitsFragmentController {

	public void controller(PageModel sharedPageModel, FragmentModel model) {
		Patient patient = (Patient) sharedPageModel.getAttribute("patient");
        if (patient != null) {
            model.addAttribute("visits", Context.getVisitService().getActiveVisitsByPatient(patient));
        }
	}
}
