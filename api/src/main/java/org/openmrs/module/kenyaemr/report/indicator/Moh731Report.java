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
package org.openmrs.module.kenyaemr.report.indicator;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Program;
import org.openmrs.api.PatientSetService.TimeModifier;
import org.openmrs.api.context.Context;
import org.openmrs.module.kenyaemr.MetadataConstants;
import org.openmrs.module.kenyaemr.calculation.art.InitialArtStartDateCalculation;
import org.openmrs.module.kenyaemr.report.KenyaEmrCalculationCohortDefinition;
import org.openmrs.module.reporting.cohort.definition.AgeCohortDefinition;
import org.openmrs.module.reporting.cohort.definition.CohortDefinition;
import org.openmrs.module.reporting.cohort.definition.CompositionCohortDefinition;
import org.openmrs.module.reporting.cohort.definition.DateObsCohortDefinition;
import org.openmrs.module.reporting.cohort.definition.EncounterCohortDefinition;
import org.openmrs.module.reporting.cohort.definition.GenderCohortDefinition;
import org.openmrs.module.reporting.cohort.definition.ProgramEnrollmentCohortDefinition;
import org.openmrs.module.reporting.common.TimeQualifier;
import org.openmrs.module.reporting.dataset.definition.CohortIndicatorDataSetDefinition;
import org.openmrs.module.reporting.dataset.definition.DataSetDefinition;
import org.openmrs.module.reporting.definition.DefinitionSummary;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.evaluation.parameter.Mapped;
import org.openmrs.module.reporting.evaluation.parameter.Parameter;
import org.openmrs.module.reporting.evaluation.parameter.Parameterizable;
import org.openmrs.module.reporting.evaluation.parameter.ParameterizableUtil;
import org.openmrs.module.reporting.indicator.CohortIndicator;
import org.openmrs.module.reporting.indicator.dimension.CohortDefinitionDimension;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.ui.framework.SimpleObject;
import org.openmrs.util.OpenmrsClassLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * MOH 731 report
 */
@Component
public class Moh731Report extends IndicatorReportManager {

	private Boolean configured = Boolean.FALSE;

	private final Log log = LogFactory.getLog(getClass());

	public static final String NAME_PREFIX = "MOH 731";

	ReportDefinition reportDefinition;

	Map<String, CohortDefinition> cohortDefinitions;

	Map<String, CohortDefinitionDimension> dimensions;

	Map<String, CohortIndicator> indicators;

	/**
	 * @see org.openmrs.module.kenyaemr.report.ReportManager#getTags()
	 */
	@Override
	public String[] getTags() {
		return new String[] { "moh", "hiv" };
	}

	/**
	 * @see org.openmrs.module.kenyaemr.report.ReportManager#getReportDefinitionSummary()
	 */
	@Override
	public DefinitionSummary getReportDefinitionSummary() {
		DefinitionSummary ret = new DefinitionSummary();
		ret.setName(NAME_PREFIX);
		ret.setUuid(getClass().getName());
		return ret;
	}

	public void setup() {
		log.debug("Setting up cohort definitions");
		setupCohortDefinitions();
		log.debug("Setting up dimensions");
		setupDimensions();
		log.debug("Setting up indicators");
		setupIndicators();
		log.debug("Setting up report definition");
		reportDefinition = createReportDefinition();
	}

	/**
	 * @return the reportDefinition
	 */
	public ReportDefinition getReportDefinition() {
		synchronized (configured) {
			if (!configured) {
				setup();
				configured = true;
			}
		}
		return reportDefinition;
	}

	private void setupCohortDefinitions() {

		Program hivProgram = Context.getProgramWorkflowService().getProgramByUuid(MetadataConstants.HIV_PROGRAM_UUID);
		Program tbProgram = Context.getProgramWorkflowService().getProgramByUuid(MetadataConstants.TB_PROGRAM_UUID);
		Concept transferInDate = Context.getConceptService().getConceptByUuid(MetadataConstants.TRANSFER_IN_DATE_CONCEPT_UUID);

		cohortDefinitions = new HashMap<String, CohortDefinition>();
		{
			GenderCohortDefinition cd = new GenderCohortDefinition();
			cd.setName("Gender = Male");
			cd.setMaleIncluded(true);
			cohortDefinitions.put("gender.M", cd);
		}
		{
			GenderCohortDefinition cd = new GenderCohortDefinition();
			cd.setName("Gender = Female");
			cd.setFemaleIncluded(true);
			cohortDefinitions.put("gender.F", cd);
		}
		{
			AgeCohortDefinition cd = new AgeCohortDefinition();
			cd.setName("Age < 1");
			cd.addParameter(new Parameter("effectiveDate", "Date", Date.class));
			cd.setMaxAge(0);
			cohortDefinitions.put("age.<1", cd);
		}
		{
			AgeCohortDefinition cd = new AgeCohortDefinition();
			cd.setName("Age < 15");
			cd.addParameter(new Parameter("effectiveDate", "Date", Date.class));
			cd.setMaxAge(14);
			cohortDefinitions.put("age.<15", cd);
		}
		{
			AgeCohortDefinition cd = new AgeCohortDefinition();
			cd.setName("Age 15+");
			cd.addParameter(new Parameter("effectiveDate", "Date", Date.class));
			cd.setMinAge(15);
			cohortDefinitions.put("age.15+", cd);
		}
		{
			ProgramEnrollmentCohortDefinition cd = new ProgramEnrollmentCohortDefinition();
			cd.setName("Enrolled in HIV Program between dates");
			cd.addParameter(new Parameter("enrolledOnOrAfter", "From Date", Date.class));
			cd.addParameter(new Parameter("enrolledOnOrBefore", "To Date", Date.class));
			cd.setPrograms(Collections.singletonList(hivProgram));
			cohortDefinitions.put("enrolledInHivProgram", cd);
		}
		{
			ProgramEnrollmentCohortDefinition cd = new ProgramEnrollmentCohortDefinition();
			cd.setName("Enrolled in TB Program between dates");
			cd.addParameter(new Parameter("enrolledOnOrAfter", "From Date", Date.class));
			cd.addParameter(new Parameter("enrolledOnOrBefore", "To Date", Date.class));
			cd.setPrograms(Collections.singletonList(tbProgram));
			cohortDefinitions.put("enrolledInTbProgram", cd);
		}
		{
			DateObsCohortDefinition cd = new DateObsCohortDefinition();
			cd.addParameter(new Parameter("onOrBefore", "Before Date", Date.class));
			cd.setName("Transfer in before date");
			cd.setTimeModifier(TimeModifier.ANY);
			cd.setQuestion(transferInDate);
			cohortDefinitions.put("transferInBefore", cd);
		}
		{
			CompositionCohortDefinition cd = new CompositionCohortDefinition();
			cd.addParameter(new Parameter("fromDate", "From Date", Date.class));
			cd.addParameter(new Parameter("toDate", "To Date", Date.class));
			cd.addSearch("enrolled", map(cohortDefinitions.get("enrolledInHivProgram"), "enrolledOnOrAfter=${fromDate},enrolledOnOrBefore=${toDate}"));
			cd.addSearch("transferIn", map(cohortDefinitions.get("transferInBefore"), "onOrBefore=${toDate}"));
			cd.setCompositionString("enrolled AND NOT transferIn");
			cohortDefinitions.put("enrolledNoTransfers", cd);
		}
		{
			EncounterCohortDefinition cd = new EncounterCohortDefinition();
			cd.setTimeQualifier(TimeQualifier.ANY);
			cd.addParameter(new Parameter("onOrBefore", "Before Date", Date.class));
			cd.addParameter(new Parameter("onOrAfter", "After Date", Date.class));
			cohortDefinitions.put("anyEncounterBetween", cd);
		}
		{ // Started ART
			InitialArtStartDateCalculation calc = new InitialArtStartDateCalculation();
			KenyaEmrCalculationCohortDefinition cd = new KenyaEmrCalculationCohortDefinition(calc);
			cd.setName("Started ART between dates");
			cd.addParameter(new Parameter("resultOnOrBefore", "Before Date", Date.class));
			cd.addParameter(new Parameter("resultOnOrAfter", "After Date", Date.class));
			cohortDefinitions.put("startedArtBetween", cd);
		}
		{ // Started ART and is TB patient
			CompositionCohortDefinition cd = new CompositionCohortDefinition();
			cd.addParameter(new Parameter("fromDate", "From Date", Date.class));
			cd.addParameter(new Parameter("toDate", "To Date", Date.class));

			cd.addSearch("startedArtBetween", map(cohortDefinitions.get("startedArtBetween"), "resultOnOrAfter=${startDate},resultOnOrBefore=${endDate}"));
			cd.addSearch("enrolledInTb", map(cohortDefinitions.get("enrolledInTbProgram"), "enrolledOnOrAfter=${fromDate},enrolledOnOrBefore=${toDate}"));
			cd.setCompositionString("startedArtBetween AND enrolledInTb");
			cohortDefinitions.put("startedArtAndIsTbPatient", cd);
		}
		{ // Revisits on ART
			// This seems wrong: "Count all patients where ART visit date is within 90 days and ART start date is before reporting period"
			CohortDefinition startedArtBetween = cohortDefinitions.get("startedArtBetween");
			CohortDefinition anyEncountersBetween = cohortDefinitions.get("anyEncounterBetween");

			CompositionCohortDefinition cd = new CompositionCohortDefinition();
			cd.addParameter(new Parameter("fromDate", "From Date", Date.class));
			cd.addParameter(new Parameter("toDate", "To Date", Date.class));

			SimpleObject mappings = SimpleObject.create("resultOnOrAfter", null, "resultOnOrBefore", "${startDate-1d}");
			cd.addSearch("startedBefore", new Mapped<CohortDefinition>(startedArtBetween, mappings));
			cd.addSearch("recentEncounter", map(anyEncountersBetween, "onOrAfter=${endDate-90d},onOrBefore=${endDate}"));
			cd.setCompositionString("recentEncounter AND startedBefore");
			cohortDefinitions.put("revisitsArt", cd);
		}
		{ // Currently on ART
			CompositionCohortDefinition cd = new CompositionCohortDefinition();
			cd.addParameter(new Parameter("fromDate", "From Date", Date.class));
			cd.addParameter(new Parameter("toDate", "To Date", Date.class));

			cd.addSearch("startedArtBetween", map(cohortDefinitions.get("startedArtBetween"), "resultOnOrAfter=${startDate},resultOnOrBefore=${endDate}"));
			cd.addSearch("revisitsArt", map(cohortDefinitions.get("revisitsArt"), "fromDate=${startDate},toDate=${endDate}"));
			cd.setCompositionString("startedArtBetween OR revisitsArt");
			cohortDefinitions.put("currentlyOnArt", cd);
		}
	}

	private void setupDimensions() {
		dimensions = new HashMap<String, CohortDefinitionDimension>();
		{
			CohortDefinitionDimension dim = new CohortDefinitionDimension();
			dim.setName("Age (<1, <15, 15+)");
			dim.addParameter(new Parameter("date", "Date", Date.class));
			dim.addCohortDefinition("<1", map(cohortDefinitions.get("age.<1"), "effectiveDate=${date}"));
			dim.addCohortDefinition("<15", map(cohortDefinitions.get("age.<15"), "effectiveDate=${date}"));
			dim.addCohortDefinition("15+", map(cohortDefinitions.get("age.15+"), "effectiveDate=${date}"));
			dimensions.put("age", dim);
		}
		{
			CohortDefinitionDimension dim = new CohortDefinitionDimension();
			dim.setName("Gender");
			dim.addCohortDefinition("M", map(cohortDefinitions.get("gender.M"), null));
			dim.addCohortDefinition("F", map(cohortDefinitions.get("gender.F"), null));
			dimensions.put("gender", dim);
		}
	}

	private void setupIndicators() {
		indicators = new HashMap<String, CohortIndicator>();
		{
			CohortIndicator ind = new CohortIndicator("Enrolled in Care (no transfers)");
			ind.addParameter(new Parameter("startDate", "Start Date", Date.class));
			ind.addParameter(new Parameter("endDate", "End Date", Date.class));
			ind.setCohortDefinition(map(cohortDefinitions.get("enrolledNoTransfers"), "fromDate=${startDate},toDate=${endDate}"));
			indicators.put("enrolledInCare", ind);
		}
		{
			CohortIndicator ind = new CohortIndicator("Currently in Care (includes transfers)");
			ind.addParameter(new Parameter("startDate", "Start Date", Date.class));
			ind.addParameter(new Parameter("endDate", "End Date", Date.class));
			ind.setCohortDefinition(map(cohortDefinitions.get("anyEncounterBetween"), "onOrAfter=${endDate-90d},onOrBefore=${endDate}"));
			indicators.put("currentlyInCare", ind);
		}
		{
			CohortIndicator ind = new CohortIndicator("Starting ART");
			ind.addParameter(new Parameter("startDate", "Start Date", Date.class));
			ind.addParameter(new Parameter("endDate", "End Date", Date.class));
			ind.setCohortDefinition(map(cohortDefinitions.get("startedArtBetween"), "resultOnOrAfter=${startDate},resultOnOrBefore=${endDate}"));
			indicators.put("startingArt", ind);
		}
		{
			CohortIndicator ind = new CohortIndicator("Starting ART (TB Patient)");
			ind.addParameter(new Parameter("startDate", "Start Date", Date.class));
			ind.addParameter(new Parameter("endDate", "End Date", Date.class));
			ind.setCohortDefinition(map(cohortDefinitions.get("startedArtAndIsTbPatient"), "fromDate=${startDate},toDate=${endDate}"));
			indicators.put("startingArtTbPatient", ind);
		}
		{
			CohortIndicator ind = new CohortIndicator("Revisits ART");
			ind.addParameter(new Parameter("startDate", "Start Date", Date.class));
			ind.addParameter(new Parameter("endDate", "End Date", Date.class));
			ind.setCohortDefinition(map(cohortDefinitions.get("revisitsArt"), "fromDate=${startDate},toDate=${endDate}"));
			indicators.put("revisitsArt", ind);
		}
		{
			CohortIndicator ind = new CohortIndicator("Currently on ART");
			ind.addParameter(new Parameter("startDate", "Start Date", Date.class));
			ind.addParameter(new Parameter("endDate", "End Date", Date.class));
			ind.setCohortDefinition(map(cohortDefinitions.get("currentlyOnArt"), "fromDate=${startDate},toDate=${endDate}"));
			indicators.put("currentlyOnArt", ind);
		}
		{
			CohortIndicator ind = new CohortIndicator("Cumulative Ever on ART");
			ind.addParameter(new Parameter("startDate", "Start Date", Date.class));
			ind.addParameter(new Parameter("endDate", "End Date", Date.class));
			ind.setCohortDefinition(map(cohortDefinitions.get("startedArtBetween"), "resultOnOrBefore=${endDate}"));
			indicators.put("cumulativeOnArt", ind);
		}
	}

	/**
	 * Creates the report definition
	 * @return the definition
	 */
	public ReportDefinition createReportDefinition() {
		ReportDefinition rd = new ReportDefinition();
		rd.addParameter(new Parameter("startDate", "Start Date", Date.class));
		rd.addParameter(new Parameter("endDate", "End Date", Date.class));
		rd.setName(NAME_PREFIX);
		rd.addDataSetDefinition(createDataSet(),
				ParameterizableUtil.createParameterMappings("startDate=${startDate},endDate=${endDate}"));
		return rd;
	}

	/**
	 * Creates the report data set
	 * @return the data set
	 */
	private DataSetDefinition createDataSet() {
		CohortIndicatorDataSetDefinition dsd = new CohortIndicatorDataSetDefinition();
		dsd.setName(NAME_PREFIX + " DSD");
		dsd.addParameter(new Parameter("startDate", "Start Date", Date.class));
		dsd.addParameter(new Parameter("endDate", "End Date", Date.class));

		dsd.addDimension("age", map(dimensions.get("age"), "date=${endDate}"));
		dsd.addDimension("gender", map(dimensions.get("gender"), null));

		dsd.addColumn("HV03-08", "Enrolled in Care (<1)", map(indicators.get("enrolledInCare"), "startDate=${startDate},endDate=${endDate}"), "age=<1");
		dsd.addColumn("HV03-09", "Enrolled in Care (<15, Male)", map(indicators.get("enrolledInCare"), "startDate=${startDate},endDate=${endDate}"), "gender=M|age=<15");
		dsd.addColumn("HV03-10", "Enrolled in Care (<15, Female)", map(indicators.get("enrolledInCare"), "startDate=${startDate},endDate=${endDate}"), "gender=F|age=<15");
		dsd.addColumn("HV03-11", "Enrolled in Care (15+, Male)", map(indicators.get("enrolledInCare"), "startDate=${startDate},endDate=${endDate}"), "gender=M|age=15+");
		dsd.addColumn("HV03-12", "Enrolled in Care (15+, Female)", map(indicators.get("enrolledInCare"), "startDate=${startDate},endDate=${endDate}"), "gender=F|age=15+");
		dsd.addColumn("HV03-13", "Enrolled in Care (Total)", map(indicators.get("enrolledInCare"), "startDate=${startDate},endDate=${endDate}"), "");

		dsd.addColumn("HV03-14", "Currently in Care (<1)", map(indicators.get("currentlyInCare"), "startDate=${startDate},endDate=${endDate}"), "age=<1");
		dsd.addColumn("HV03-15", "Currently in Care (<15, Male)", map(indicators.get("currentlyInCare"), "startDate=${startDate},endDate=${endDate}"), "gender=M|age=<15");
		dsd.addColumn("HV03-16", "Currently in Care (<15, Female)", map(indicators.get("currentlyInCare"), "startDate=${startDate},endDate=${endDate}"), "gender=F|age=<15");
		dsd.addColumn("HV03-17", "Currently in Care (15+, Male)", map(indicators.get("currentlyInCare"), "startDate=${startDate},endDate=${endDate}"), "gender=M|age=15+");
		dsd.addColumn("HV03-18", "Currently in Care (15+, Female)", map(indicators.get("currentlyInCare"), "startDate=${startDate},endDate=${endDate}"), "gender=F|age=15+");
		dsd.addColumn("HV03-19", "Currently in Care (Total)", map(indicators.get("currentlyInCare"), "startDate=${startDate},endDate=${endDate}"), "");

		dsd.addColumn("HV03-20", "Starting ART (<1)", map(indicators.get("startingArt"), "startDate=${startDate},endDate=${endDate}"), "age=<1");
		dsd.addColumn("HV03-21", "Starting ART (<15, Male)", map(indicators.get("startingArt"), "startDate=${startDate},endDate=${endDate}"), "gender=M|age=<15");
		dsd.addColumn("HV03-22", "Starting ART (<15, Female)", map(indicators.get("startingArt"), "startDate=${startDate},endDate=${endDate}"), "gender=F|age=<15");
		dsd.addColumn("HV03-23", "Starting ART (15+, Male)", map(indicators.get("startingArt"), "startDate=${startDate},endDate=${endDate}"), "gender=M|age=15+");
		dsd.addColumn("HV03-24", "Starting ART (15+, Female)", map(indicators.get("startingArt"), "startDate=${startDate},endDate=${endDate}"), "gender=F|age=15+");
		dsd.addColumn("HV03-25", "Starting ART (Total)", map(indicators.get("startingArt"), "startDate=${startDate},endDate=${endDate}"), "");

		// TODO HV03-26

		dsd.addColumn("HV03-27", "Starting ART (TB Patient)", map(indicators.get("startingArtTbPatient"), "startDate=${startDate},endDate=${endDate}"), "");

		dsd.addColumn("HV03-28", "Revisits ART (<1)", map(indicators.get("revisitsArt"), "startDate=${startDate},endDate=${endDate}"), "age=<1");
		dsd.addColumn("HV03-29", "Revisits ART (<15, Male)", map(indicators.get("revisitsArt"), "startDate=${startDate},endDate=${endDate}"), "gender=M|age=<15");
		dsd.addColumn("HV03-30", "Revisits ART (<15, Female)", map(indicators.get("revisitsArt"), "startDate=${startDate},endDate=${endDate}"), "gender=F|age=<15");
		dsd.addColumn("HV03-31", "Revisits ART (15+, Male)", map(indicators.get("revisitsArt"), "startDate=${startDate},endDate=${endDate}"), "gender=M|age=15+");
		dsd.addColumn("HV03-32", "Revisits ART (15+, Female)", map(indicators.get("revisitsArt"), "startDate=${startDate},endDate=${endDate}"), "gender=F|age=15+");
		dsd.addColumn("HV03-33", "Revisits ART (Total)", map(indicators.get("revisitsArt"), "startDate=${startDate},endDate=${endDate}"), "");

		dsd.addColumn("HV03-28", "Currently on ART (<1)", map(indicators.get("currentlyOnArt"), "startDate=${startDate},endDate=${endDate}"), "age=<1");
		dsd.addColumn("HV03-35", "Currently on ART (<15, Male)", map(indicators.get("currentlyOnArt"), "startDate=${startDate},endDate=${endDate}"), "gender=M|age=<15");
		dsd.addColumn("HV03-36", "Currently on ART (<15, Female)", map(indicators.get("currentlyOnArt"), "startDate=${startDate},endDate=${endDate}"), "gender=F|age=<15");
		dsd.addColumn("HV03-37", "Currently on ART (15+, Male)", map(indicators.get("currentlyOnArt"), "startDate=${startDate},endDate=${endDate}"), "gender=M|age=15+");
		dsd.addColumn("HV03-38", "Currently on ART (15+, Female)", map(indicators.get("currentlyOnArt"), "startDate=${startDate},endDate=${endDate}"), "gender=F|age=15+");
		dsd.addColumn("HV03-39", "Currently on ART (Total)", map(indicators.get("currentlyOnArt"), "startDate=${startDate},endDate=${endDate}"), "");

		dsd.addColumn("HV03-40", "Cumulative Ever on ART (<15, Male)", map(indicators.get("cumulativeOnArt"), "startDate=${startDate},endDate=${endDate}"), "gender=M|age=<15");
		dsd.addColumn("HV03-41", "Cumulative Ever on ART (<15, Female)", map(indicators.get("cumulativeOnArt"), "startDate=${startDate},endDate=${endDate}"), "gender=F|age=<15");
		dsd.addColumn("HV03-42", "Cumulative Ever on ART (15+, Male)", map(indicators.get("cumulativeOnArt"), "startDate=${startDate},endDate=${endDate}"), "gender=M|age=15+");
		dsd.addColumn("HV03-43", "Cumulative Ever on ART (15+, Female)", map(indicators.get("cumulativeOnArt"), "startDate=${startDate},endDate=${endDate}"), "gender=F|age=15+");
		dsd.addColumn("HV03-44", "Cumulative Ever on ART (Total)", map(indicators.get("cumulativeOnArt"), "startDate=${startDate},endDate=${endDate}"), "");

		return dsd;
	}

	private <T extends Parameterizable> Mapped<T> map(T parameterizable, String mappings) {
		if (parameterizable == null) {
			throw new NullPointerException("Programming error: missing parameterizable");
		}
		if (mappings == null) {
			mappings = ""; // probably not necessary, just to be safe
		}
		return new Mapped<T>(parameterizable, ParameterizableUtil.createParameterMappings(mappings));
	}

	/**
	 * @see org.openmrs.module.kenyaemr.report.ReportManager#getExcelTemplate()
	 */
	@Override
	public byte[] getExcelTemplate() {
		try {
			InputStream is = OpenmrsClassLoader.getInstance().getResourceAsStream("report_templates/Moh731Report.xls");
			byte[] contents = IOUtils.toByteArray(is);
			IOUtils.closeQuietly(is);
			return contents;
		} catch (IOException ex) {
			throw new RuntimeException("Error loading excel template", ex);
		}
	}

	/**
	 * @see org.openmrs.module.kenyaemr.report.ReportManager#getExcelFilename(org.openmrs.module.reporting.evaluation.EvaluationContext)
	 */
	@Override
	public String getExcelFilename(EvaluationContext ec) {
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM");
		return NAME_PREFIX + " " + df.format(ec.getParameterValue("startDate")) + ".xls";
	}

}
