package org.openmrs.module.kenyaemr.calculation.library.hiv.art;

import org.openmrs.Obs;
import org.openmrs.PatientProgram;
import org.openmrs.Program;
import org.openmrs.calculation.patient.PatientCalculationContext;
import org.openmrs.calculation.result.CalculationResultMap;
import org.openmrs.calculation.result.ListResult;
import org.openmrs.calculation.result.SimpleResult;
import org.openmrs.module.kenyacore.calculation.AbstractPatientCalculation;
import org.openmrs.module.kenyacore.calculation.CalculationUtils;
import org.openmrs.module.kenyacore.calculation.Calculations;
import org.openmrs.module.kenyacore.calculation.Filters;
import org.openmrs.module.kenyaemr.Dictionary;
import org.openmrs.module.kenyaemr.calculation.EmrCalculationUtils;
import org.openmrs.module.kenyaemr.calculation.library.models.PatientEligibility;
import org.openmrs.module.kenyaemr.metadata.HivMetadata;
import org.openmrs.module.kenyaemr.metadata.TbMetadata;
import org.openmrs.module.kenyaemr.util.EmrUtils;
import org.openmrs.module.metadatadeploy.MetadataUtils;
import org.openmrs.module.reporting.common.Age;
import org.openmrs.module.reporting.common.DateUtil;
import org.openmrs.module.reporting.common.DurationUnit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by codehub on 23/06/15.
 * Calculates the date and reason a patient was eligible for ART
 */
public class DateAndReasonFirstMedicallyEligibleForArtCalculation extends AbstractPatientCalculation {

    @Override
    public CalculationResultMap evaluate(Collection<Integer> cohort, Map<String, Object> params, PatientCalculationContext context) {
        CalculationResultMap ret = new CalculationResultMap();

        Program hivProgram = MetadataUtils.existing(Program.class, HivMetadata._Program.HIV);
        Program tbProgram = MetadataUtils.existing(Program.class, TbMetadata._Program.TB);

        Set<Integer> inHivProgram = Filters.inProgram(hivProgram, cohort, context);
        //in tb program
        Set<Integer> inTbProgram = Filters.inProgram(tbProgram, cohort, context);

        Set<Integer> female = Filters.female(cohort, context);

        CalculationResultMap hivEnrollmenMap = Calculations.firstEnrollments(hivProgram, cohort, context);

        Integer outcomePeriod = (params != null && params.containsKey("outcomePeriod")) ? (Integer) params.get("outcomePeriod") : null;

        if(outcomePeriod != null) {
            context.setNow(DateUtil.adjustDate(context.getNow(), outcomePeriod, DurationUnit.MONTHS));
        }

        CalculationResultMap ages = Calculations.ages(cohort, context);
        CalculationResultMap allWhoStage = Calculations.allObs(Dictionary.getConcept(Dictionary.CURRENT_WHO_STAGE), cohort, context);
        CalculationResultMap allCd4 = Calculations.allObs(Dictionary.getConcept(Dictionary.CD4_COUNT), cohort, context);
        CalculationResultMap artStartDateMap = calculate(new InitialArtStartDateCalculation(), cohort, context);
        CalculationResultMap hepatitisMap = Calculations.lastObs(Dictionary.getConcept(Dictionary.PROBLEM_ADDED), cohort, context);
        CalculationResultMap pregStatusObss = Calculations.lastObs(Dictionary.getConcept(Dictionary.PREGNANCY_STATUS), female, context);
        CalculationResultMap hivRiskFactor = Calculations.lastObs(Dictionary.getConcept(Dictionary.HIV_RISK_FACTOR), cohort, context);
        CalculationResultMap tbStatus = Calculations.lastObs(Dictionary.getConcept(Dictionary.TUBERCULOSIS_DISEASE_STATUS), cohort, context);

        for(Integer ptId: cohort) {

            PatientEligibility patientEligibility = null;

            Obs hepatitisConcept = EmrCalculationUtils.obsResultForPatient(hepatitisMap, ptId);
            Obs isDiscodantCouple = EmrCalculationUtils.obsResultForPatient(hivRiskFactor, ptId);
            Obs hasTbConcpt = EmrCalculationUtils.obsResultForPatient(tbStatus, ptId);
            Obs pregnancyObs = EmrCalculationUtils.obsResultForPatient(pregStatusObss, ptId);
            Date artStartDate = EmrCalculationUtils.datetimeResultForPatient(artStartDateMap, ptId);

            ListResult allCd4ListResults = (ListResult) allCd4.get(ptId);
            List<Obs> obsList = CalculationUtils.extractResultValues(allCd4ListResults);
            ListResult allWhoListResults = (ListResult) allWhoStage.get(ptId);
            List<Obs> obsListWho = CalculationUtils.extractResultValues(allWhoListResults);

            int ageInMonths = ((Age) ages.get(ptId).getValue()).getFullMonths();

            PatientProgram hivEnrollment = EmrCalculationUtils.resultForPatient(hivEnrollmenMap, ptId);

            if(inHivProgram.contains(ptId) && hivEnrollment != null) {
                Date futureDate = DateUtil.adjustDate(DateUtil.adjustDate(hivEnrollment.getDateEnrolled(), outcomePeriod, DurationUnit.MONTHS), 1, DurationUnit.DAYS);

                if (pregnancyObs != null && pregnancyObs.getValueCoded().equals(Dictionary.getConcept(Dictionary.YES)) && pregnancyObs.getObsDatetime().before(futureDate)) {
                    patientEligibility = new PatientEligibility("Pregnant or breastfeeding", pregnancyObs.getObsDatetime());
                    if (artStartDate != null && pregnancyObs.getObsDatetime().after(artStartDate)) {
                        patientEligibility = new PatientEligibility("", artStartDate);
                    }
                } else if (hepatitisConcept != null && hepatitisConcept.getValueCoded().equals(Dictionary.getConcept(Dictionary.HEPATITIS_B)) && hepatitisConcept.getObsDatetime().before(futureDate)) {
                    patientEligibility = new PatientEligibility("HPV/HIV coinfection", hepatitisConcept.getObsDatetime());
                    if (artStartDate != null && hepatitisConcept.getObsDatetime().after(artStartDate)) {
                        patientEligibility = new PatientEligibility("", artStartDate);
                    }
                } else if ((inTbProgram.contains(ptId) || (hasTbConcpt != null && hasTbConcpt.getValueCoded().equals(Dictionary.getConcept(Dictionary.DISEASE_DIAGNOSED))) || (hasTbConcpt != null && hasTbConcpt.getValueCoded().equals(Dictionary.getConcept(Dictionary.ON_TREATMENT_FOR_DISEASE)))) && hasTbConcpt.getObsDatetime().before(futureDate)) {
                    patientEligibility = new PatientEligibility("TB/HIV co infection ", hasTbConcpt.getObsDatetime());
                    if (artStartDate != null && hasTbConcpt.getObsDatetime().after(artStartDate)) {
                        patientEligibility = new PatientEligibility("", artStartDate);
                    }
                } else if (isDiscodantCouple != null && isDiscodantCouple.getValueCoded().equals(Dictionary.getConcept(Dictionary.DISCORDANT_COUPLE)) && isDiscodantCouple.getObsDatetime().before(futureDate)) {
                    patientEligibility = new PatientEligibility("Discordant couple (HIV-negative partner)", isDiscodantCouple.getObsDatetime());
                    if (artStartDate != null && isDiscodantCouple.getObsDatetime().after(artStartDate)) {
                        patientEligibility = new PatientEligibility("", artStartDate);
                    }
                } else {
                        patientEligibility = getCriteriaAndDate(ageInMonths, obsList, obsListWho, artStartDate, hivEnrollment.getDateEnrolled(), outcomePeriod);

                }
            }
            ret.put(ptId, new SimpleResult(patientEligibility, this));
        }

        return ret;
    }

     private PatientEligibility getCriteriaAndDate(int ageInMonths, List<Obs> cd4, List<Obs> whoStag, Date artStartDate, Date hivEnrollmentDate, int period) {

        if (ageInMonths <= 120) {//children less than 10 years
             return new PatientEligibility("Age 10 years and below", hivEnrollmentDate);
        }

        else if (ageInMonths > 120 && ageInMonths <= 180) {
                Date artStartDt = checkIfOnArtBeforeOthers(artStartDate, cd4, whoStag, hivEnrollmentDate, period);
                Date whoDate = whoDate(whoStag, hivEnrollmentDate, period);

            if(artStartDt != null && whoDate != null && artStartDt.before(whoDate) ) {
                return new PatientEligibility(null, artStartDate);
            }
            else if(artStartDt != null && whoDate != null && artStartDt.equals(whoDate)) {
                return new PatientEligibility("WHO stage = Stage IV", whoDate);
            }

            else if(artStartDt != null && whoDate != null && artStartDt.after(whoDate)) {
                return new PatientEligibility("WHO stage = Stage IV", whoDate);
            }

            else if(artStartDt == null && whoDate != null) {
                return new PatientEligibility("WHO stage = Stage IV", whoDate);
            }

            else if(artStartDt != null && whoDate == null) {
                return new PatientEligibility(null, artStartDate);
            }
        }

        else if (ageInMonths > 180){
            Date artStartDt = checkIfOnArtBeforeOthers(artStartDate, cd4, whoStag, hivEnrollmentDate, period);
            Date cd4Date = cd4Date(cd4, hivEnrollmentDate, period);
            if(artStartDt != null && cd4Date != null && artStartDt.before(cd4Date)) {
                return new PatientEligibility(null, artStartDate);
            }

            else if(artStartDt != null && cd4Date != null && artStartDt.equals(cd4Date)) {
                return new PatientEligibility("CD4 count<=500", cd4Date);
            }

            else if(artStartDt != null && cd4Date != null && artStartDt.after(cd4Date)) {
                return new PatientEligibility("CD4 count<=500", cd4Date);
            }

            else if(artStartDt == null && cd4Date != null) {
                return new PatientEligibility("CD4 count<=500", cd4Date);
            }
            else if(artStartDt != null && cd4Date == null) {
                return new PatientEligibility(null, artStartDate);
            }

        }

        return  null;
    }

    Date whoDate(List<Obs> whoStage, Date hivEnrollmentDate, int period) {
        Date whoStageDate = null;
        Date futureDate = DateUtil.adjustDate(DateUtil.adjustDate(hivEnrollmentDate, period, DurationUnit.MONTHS), 1, DurationUnit.DAYS);
        List<Obs> listOfWho = new ArrayList<Obs>();

        if(whoStage.size() > 0) {

            for (Obs obsWhoStage : whoStage) {
                Integer stage = EmrUtils.whoStage(obsWhoStage.getValueCoded());

                if (stage != null && (stage == 3 || stage == 4) && obsWhoStage.getObsDatetime().before(futureDate) && (obsWhoStage.getObsDatetime().after(hivEnrollmentDate) || obsWhoStage.getObsDatetime().equals(hivEnrollmentDate))) {
                    listOfWho.add(obsWhoStage);

                }
            }
            if(listOfWho.size() > 0) {
                whoStageDate = listOfWho.get(0).getObsDatetime();
            }

        }

        return whoStageDate;

    }

    Date cd4Date(List<Obs> cd4, Date hivEnrollmentDate, int period) {
        Date cd4Date = null;
        Date futureDate = DateUtil.adjustDate(DateUtil.adjustDate(hivEnrollmentDate, period, DurationUnit.MONTHS), 1, DurationUnit.DAYS);
        Date enrollDate = DateUtil.adjustDate(hivEnrollmentDate, -1, DurationUnit.DAYS);
        List<Obs> cd4Less500 = new ArrayList<Obs>();

        if(cd4.size() > 0) {

            for (Obs obsCd4 : cd4) {
                if (obsCd4.getValueNumeric() <= 500 && obsCd4.getObsDatetime().before(futureDate) && obsCd4.getObsDatetime().after(enrollDate)) {
                    cd4Less500.add(obsCd4);
                }

            }
            if(cd4Less500.size() > 0){
                cd4Date = cd4Less500.get(0).getObsDatetime();
            }


        }

        return cd4Date;

    }

    Date checkIfOnArtBeforeOthers(Date artDate, List<Obs> cd4, List<Obs> whoStage, Date hivEnrollmentDate, int period) {
        Date isOnARTDate = null;
        Date futureDate = DateUtil.adjustDate(DateUtil.adjustDate(hivEnrollmentDate, period, DurationUnit.MONTHS), 1, DurationUnit.DAYS);

        if(whoDate(whoStage, hivEnrollmentDate, period) == null && cd4Date(cd4,hivEnrollmentDate, period) == null && artDate != null && artDate.before(futureDate)) {
            isOnARTDate = artDate;
        }

        if(whoDate(whoStage, hivEnrollmentDate, period) != null && cd4Date(cd4, hivEnrollmentDate, period) == null && artDate != null && whoDate(whoStage, hivEnrollmentDate, period).after(artDate) && artDate.before(futureDate)) {
            isOnARTDate = artDate;
        }

        if(cd4Date(cd4, hivEnrollmentDate, period) != null && whoDate(whoStage, hivEnrollmentDate, period) == null &&  artDate != null && cd4Date(cd4, hivEnrollmentDate, period).after(artDate) && artDate.before(futureDate)) {
            isOnARTDate = artDate;
        }

        if(cd4Date(cd4, hivEnrollmentDate, period) != null && whoDate(whoStage, hivEnrollmentDate, period) != null &&  artDate != null) {
            if(artDate.before(whoDate(whoStage, hivEnrollmentDate, period)) && artDate.before(cd4Date(cd4, hivEnrollmentDate, period))) {
                isOnARTDate = artDate;
            }
        }



        return isOnARTDate;
    }

}
