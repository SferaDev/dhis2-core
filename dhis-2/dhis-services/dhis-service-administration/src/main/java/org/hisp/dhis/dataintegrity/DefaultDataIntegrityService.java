/*
 * Copyright (c) 2004-2022, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.dataintegrity;

import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.hisp.dhis.commons.collection.ListUtils.getDuplicates;
import static org.hisp.dhis.dataintegrity.DataIntegrityDetails.DataIntegrityIssue.toIssue;
import static org.hisp.dhis.dataintegrity.DataIntegrityDetails.DataIntegrityIssue.toRefsList;
import static org.hisp.dhis.dataintegrity.DataIntegrityYamlReader.readDataIntegrityYaml;
import static org.hisp.dhis.expression.ParseType.INDICATOR_EXPRESSION;
import static org.hisp.dhis.expression.ParseType.VALIDATION_RULE_EXPRESSION;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hibernate.SessionFactory;
import org.hisp.dhis.antlr.ParserException;
import org.hisp.dhis.category.CategoryService;
import org.hisp.dhis.common.IdentifiableObject;
import org.hisp.dhis.dataelement.DataElement;
import org.hisp.dhis.dataelement.DataElementGroupSet;
import org.hisp.dhis.dataelement.DataElementService;
import org.hisp.dhis.dataentryform.DataEntryFormService;
import org.hisp.dhis.dataintegrity.DataIntegrityDetails.DataIntegrityIssue;
import org.hisp.dhis.dataset.DataSet;
import org.hisp.dhis.dataset.DataSetService;
import org.hisp.dhis.expression.Expression;
import org.hisp.dhis.expression.ExpressionService;
import org.hisp.dhis.expression.ExpressionValidationOutcome;
import org.hisp.dhis.i18n.I18n;
import org.hisp.dhis.i18n.I18nManager;
import org.hisp.dhis.indicator.Indicator;
import org.hisp.dhis.indicator.IndicatorGroupSet;
import org.hisp.dhis.indicator.IndicatorService;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.organisationunit.OrganisationUnitGroupService;
import org.hisp.dhis.organisationunit.OrganisationUnitService;
import org.hisp.dhis.period.Period;
import org.hisp.dhis.period.PeriodService;
import org.hisp.dhis.period.PeriodType;
import org.hisp.dhis.program.Program;
import org.hisp.dhis.program.ProgramIndicator;
import org.hisp.dhis.program.ProgramIndicatorService;
import org.hisp.dhis.programrule.ProgramRule;
import org.hisp.dhis.programrule.ProgramRuleAction;
import org.hisp.dhis.programrule.ProgramRuleActionService;
import org.hisp.dhis.programrule.ProgramRuleService;
import org.hisp.dhis.programrule.ProgramRuleVariable;
import org.hisp.dhis.programrule.ProgramRuleVariableService;
import org.hisp.dhis.scheduling.JobProgress;
import org.hisp.dhis.validation.ValidationRule;
import org.hisp.dhis.validation.ValidationRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Lars Helge Overland
 */
@Slf4j
@Service( "org.hisp.dhis.dataintegrity.DataIntegrityService" )
@Transactional
@AllArgsConstructor
public class DefaultDataIntegrityService
    implements DataIntegrityService
{
    private static final String FORMULA_SEPARATOR = "#";

    private final I18nManager i18nManager;

    private final ProgramRuleService programRuleService;

    private final ProgramRuleActionService programRuleActionService;

    private final ProgramRuleVariableService programRuleVariableService;

    private final DataElementService dataElementService;

    private final IndicatorService indicatorService;

    private final DataSetService dataSetService;

    private final OrganisationUnitService organisationUnitService;

    private final OrganisationUnitGroupService organisationUnitGroupService;

    private final ValidationRuleService validationRuleService;

    private final ExpressionService expressionService;

    private final DataEntryFormService dataEntryFormService;

    private final CategoryService categoryService;

    private final PeriodService periodService;

    private final ProgramIndicatorService programIndicatorService;

    private final SessionFactory sessionFactory;

    private static int alphabeticalOrder( DataIntegrityIssue a, DataIntegrityIssue b )
    {
        return a.getName().compareTo( b.getName() );
    }

    private static List<DataIntegrityIssue> toSimpleIssueList( Stream<? extends IdentifiableObject> items )
    {
        return items.map( DataIntegrityIssue::toIssue )
            .sorted( DefaultDataIntegrityService::alphabeticalOrder )
            .collect( toUnmodifiableList() );
    }

    private static <T extends IdentifiableObject> List<DataIntegrityIssue> toIssueList( Stream<T> items,
        Function<T, ? extends Collection<? extends IdentifiableObject>> toRefs )
    {
        return items.map( e -> DataIntegrityIssue.toIssue( e, toRefs.apply( e ) ) )
            .sorted( DefaultDataIntegrityService::alphabeticalOrder )
            .collect( toUnmodifiableList() );
    }

    // -------------------------------------------------------------------------
    // DataElement
    // -------------------------------------------------------------------------

    /**
     * Gets all data elements which are not assigned to any data set.
     */
    List<DataIntegrityIssue> getDataElementsWithoutDataSet()
    {
        return toSimpleIssueList( dataElementService.getDataElementsWithoutDataSets().stream() );

    }

    /**
     * Gets all data elements which are not members of any groups.
     */
    List<DataIntegrityIssue> getDataElementsWithoutGroups()
    {
        return toSimpleIssueList( dataElementService.getDataElementsWithoutGroups().stream() );
    }

    /**
     * Returns all data elements which are members of data sets with different
     * period types.
     */
    List<DataIntegrityIssue> getDataElementsAssignedToDataSetsWithDifferentPeriodTypes()
    {
        Collection<DataElement> dataElements = dataElementService.getAllDataElements();

        Collection<DataSet> dataSets = dataSetService.getAllDataSets();

        List<DataIntegrityIssue> issues = new ArrayList<>();

        for ( DataElement element : dataElements )
        {
            final Set<PeriodType> targetPeriodTypes = new HashSet<>();
            final List<DataSet> targetDataSets = new ArrayList<>();

            for ( DataSet dataSet : dataSets )
            {
                if ( dataSet.getDataElements().contains( element ) )
                {
                    targetPeriodTypes.add( dataSet.getPeriodType() );
                    targetDataSets.add( dataSet );
                }
            }

            if ( targetPeriodTypes.size() > 1 )
            {
                issues.add( DataIntegrityIssue.toIssue( element, targetDataSets ) );
            }
        }

        return issues;
    }

    /**
     * Gets all data elements units which are members of more than one group
     * which enter into an exclusive group set.
     */
    List<DataIntegrityIssue> getDataElementsViolatingExclusiveGroupSets()
    {
        Collection<DataElementGroupSet> groupSets = dataElementService.getAllDataElementGroupSets();

        List<DataIntegrityIssue> issues = new ArrayList<>();

        for ( DataElementGroupSet groupSet : groupSets )
        {
            Set<DataElement> duplicates = getDuplicates( groupSet.getDataElements() );

            for ( DataElement duplicate : duplicates )
            {
                issues.add( DataIntegrityIssue.toIssue( duplicate, duplicate.getGroups() ) );
            }
        }

        return issues;
    }

    /**
     * Returns all data elements which are member of a data set but not part of
     * either the custom form or sections of the data set.
     */
    List<DataIntegrityIssue> getDataElementsInDataSetNotInForm()
    {
        List<DataIntegrityIssue> issues = new ArrayList<>();

        Collection<DataSet> dataSets = dataSetService.getAllDataSets();

        for ( DataSet dataSet : dataSets )
        {
            if ( !dataSet.getFormType().isDefault() )
            {
                Set<DataElement> formElements = new HashSet<>();

                if ( dataSet.hasDataEntryForm() )
                {
                    formElements.addAll( dataEntryFormService.getDataElementsInDataEntryForm( dataSet ) );
                }
                else if ( dataSet.hasSections() )
                {
                    formElements.addAll( dataSet.getDataElementsInSections() );
                }

                Set<DataElement> dataSetElements = new HashSet<>( dataSet.getDataElements() );

                dataSetElements.removeAll( formElements );

                if ( !dataSetElements.isEmpty() )
                {
                    issues.add( DataIntegrityIssue.toIssue( dataSet, dataSetElements ) );
                }
            }
        }

        return issues;
    }

    /**
     * Returns all invalid category combinations.
     */
    List<DataIntegrityIssue> getInvalidCategoryCombos()
    {
        return toSimpleIssueList( categoryService.getAllCategoryCombos().stream().filter( c -> !c.isValid() ) );
    }

    // -------------------------------------------------------------------------
    // DataSet
    // -------------------------------------------------------------------------

    List<DataIntegrityIssue> getDataSetsNotAssignedToOrganisationUnits()
    {
        return toSimpleIssueList( dataSetService.getDataSetsNotAssignedToOrganisationUnits().stream() );
    }

    // -------------------------------------------------------------------------
    // Indicator
    // -------------------------------------------------------------------------

    /**
     * Gets all indicators with identical numerator and denominator.
     */
    List<DataIntegrityIssue> getIndicatorsWithIdenticalFormulas()
    {
        List<DataIntegrityIssue> issues = new ArrayList<>();

        Map<String, List<Indicator>> byFormula = indicatorService.getAllIndicators().stream().collect(
            groupingBy( indicator -> indicator.getNumerator() + FORMULA_SEPARATOR + indicator.getDenominator() ) );

        for ( Entry<String, List<Indicator>> e : byFormula.entrySet() )
        {
            if ( e.getValue().size() > 1 )
            {
                issues.add( new DataIntegrityIssue( null, e.getKey(), null, toRefsList( e.getValue().stream() ) ) );
            }
        }
        return issues;
    }

    /**
     * Gets all indicators which are not assigned to any groups.
     */
    List<DataIntegrityIssue> getIndicatorsWithoutGroups()
    {
        return toSimpleIssueList( indicatorService.getIndicatorsWithoutGroups().stream() );
    }

    /**
     * Gets all indicators with invalid indicator numerators.
     */
    List<DataIntegrityIssue> getInvalidIndicatorNumerators()
    {
        return getInvalidIndicators( Indicator::getNumerator );
    }

    /**
     * Gets all indicators with invalid indicator denominators.
     */
    List<DataIntegrityIssue> getInvalidIndicatorDenominators()
    {
        return getInvalidIndicators( Indicator::getDenominator );
    }

    private List<DataIntegrityIssue> getInvalidIndicators( Function<Indicator, String> getter )
    {
        List<DataIntegrityIssue> issues = new ArrayList<>();
        I18n i18n = i18nManager.getI18n();

        for ( Indicator indicator : indicatorService.getAllIndicators() )
        {
            ExpressionValidationOutcome result = expressionService.expressionIsValid( getter.apply( indicator ),
                INDICATOR_EXPRESSION );

            if ( !result.isValid() )
            {
                issues.add( toIssue( indicator, i18n.getString( result.getKey() ) ) );
            }
        }

        return issues;
    }

    /**
     * Gets all indicators units which are members of more than one group which
     * enter into an exclusive group set.
     */
    List<DataIntegrityIssue> getIndicatorsViolatingExclusiveGroupSets()
    {
        Collection<IndicatorGroupSet> groupSets = indicatorService.getAllIndicatorGroupSets();

        List<DataIntegrityIssue> issues = new ArrayList<>();

        for ( IndicatorGroupSet groupSet : groupSets )
        {
            Collection<Indicator> duplicates = getDuplicates(
                new ArrayList<>( groupSet.getIndicators() ) );

            for ( Indicator duplicate : duplicates )
            {
                issues.add( DataIntegrityIssue.toIssue( duplicate, duplicate.getGroups() ) );
            }
        }

        return issues;
    }

    // -------------------------------------------------------------------------
    // Period
    // -------------------------------------------------------------------------

    /**
     * Lists all Periods which are duplicates, based on the period type and
     * start date.
     */
    List<DataIntegrityIssue> getDuplicatePeriods()
    {
        List<Period> periods = periodService.getAllPeriods();

        List<DataIntegrityIssue> issues = new ArrayList<>();

        for ( Entry<String, List<Period>> group : periods.stream().collect(
            groupingBy( p -> p.getPeriodType().getName() + p.getStartDate().toString() ) ).entrySet() )
        {
            if ( group.getValue().size() > 1 )
            {
                issues.add( new DataIntegrityIssue( null, group.getKey(), null,
                    group.getValue().stream().map( p -> p.toString() + ":" + p.getUid() )
                        .collect( toUnmodifiableList() ) ) );
            }
        }
        return issues;
    }

    // -------------------------------------------------------------------------
    // OrganisationUnit
    // -------------------------------------------------------------------------

    List<DataIntegrityIssue> getOrganisationUnitsWithCyclicReferences()
    {
        return toSimpleIssueList( organisationUnitService.getOrganisationUnitsWithCyclicReferences().stream() );
    }

    List<DataIntegrityIssue> getOrphanedOrganisationUnits()
    {
        return toSimpleIssueList( organisationUnitService.getOrphanedOrganisationUnits().stream() );
    }

    List<DataIntegrityIssue> getOrganisationUnitsWithoutGroups()
    {
        return toSimpleIssueList( organisationUnitService.getOrganisationUnitsWithoutGroups().stream() );
    }

    List<DataIntegrityIssue> getOrganisationUnitsViolatingExclusiveGroupSets()
    {
        return toIssueList( organisationUnitService.getOrganisationUnitsViolatingExclusiveGroupSets().stream(),
            OrganisationUnit::getGroups );
    }

    List<DataIntegrityIssue> getOrganisationUnitGroupsWithoutGroupSets()
    {
        return toSimpleIssueList( organisationUnitGroupService.getOrganisationUnitGroupsWithoutGroupSets().stream() );
    }

    // -------------------------------------------------------------------------
    // ValidationRule
    // -------------------------------------------------------------------------

    List<DataIntegrityIssue> getValidationRulesWithoutGroups()
    {
        return toSimpleIssueList( validationRuleService.getValidationRulesWithoutGroups().stream() );
    }

    /**
     * Gets all ValidationRules with invalid left side expressions.
     */
    List<DataIntegrityIssue> getInvalidValidationRuleLeftSideExpressions()
    {
        return getInvalidValidationRuleExpressions( ValidationRule::getLeftSide );
    }

    /**
     * Gets all ValidationRules with invalid right side expressions.
     */
    List<DataIntegrityIssue> getInvalidValidationRuleRightSideExpressions()
    {
        return getInvalidValidationRuleExpressions( ValidationRule::getRightSide );
    }

    private List<DataIntegrityIssue> getInvalidValidationRuleExpressions(
        Function<ValidationRule, Expression> getter )
    {
        List<DataIntegrityIssue> issues = new ArrayList<>();
        I18n i18n = i18nManager.getI18n();

        for ( ValidationRule rule : validationRuleService.getAllValidationRules() )
        {
            ExpressionValidationOutcome result = expressionService
                .expressionIsValid( getter.apply( rule ).getExpression(), VALIDATION_RULE_EXPRESSION );

            if ( !result.isValid() )
            {
                issues.add( toIssue( rule, i18n.getString( result.getKey() ) ) );
            }
        }

        return issues;
    }

    private void registerNonDatabaseIntegrityCheck( DataIntegrityCheckType type,
        Supplier<List<DataIntegrityIssue>> check )
    {
        String name = type.getName();
        checksByName.put( name, DataIntegrityCheck.builder()
            .name( name )
            .severity( DataIntegritySeverity.WARNING )
            .section( "Legacy" )
            .description( name.replace( '_', ' ' ) )
            .runDetailsCheck( c -> new DataIntegrityDetails( c, check.get() ) )
            .runSummaryCheck( c -> new DataIntegritySummary( c, check.get().size(), null ) )
            .build() );
    }

    /**
     * Maps all "hard coded" checks to their implementation method and registers
     * a {@link DataIntegrityCheck} to perform the method as
     * {@link DataIntegrityDetails}.
     */
    @PostConstruct
    public void initIntegrityChecks()
    {
        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.DATA_ELEMENTS_WITHOUT_DATA_SETS,
            this::getDataElementsWithoutDataSet );
        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.DATA_ELEMENTS_WITHOUT_GROUPS,
            this::getDataElementsWithoutGroups );
        registerNonDatabaseIntegrityCheck(
            DataIntegrityCheckType.DATA_ELEMENTS_ASSIGNED_TO_DATA_SETS_WITH_DIFFERENT_PERIOD_TYPES,
            this::getDataElementsAssignedToDataSetsWithDifferentPeriodTypes );
        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.DATA_ELEMENTS_VIOLATING_EXCLUSIVE_GROUP_SETS,
            this::getDataElementsViolatingExclusiveGroupSets );
        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.DATA_ELEMENTS_IN_DATA_SET_NOT_IN_FORM,
            this::getDataElementsInDataSetNotInForm );

        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.CATEGORY_COMBOS_BEING_INVALID,
            this::getInvalidCategoryCombos );

        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.DATA_SETS_NOT_ASSIGNED_TO_ORG_UNITS,
            this::getDataSetsNotAssignedToOrganisationUnits );

        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.INDICATORS_WITH_IDENTICAL_FORMULAS,
            this::getIndicatorsWithIdenticalFormulas );
        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.INDICATORS_WITHOUT_GROUPS,
            this::getIndicatorsWithoutGroups );
        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.INDICATORS_WITH_INVALID_NUMERATOR,
            this::getInvalidIndicatorNumerators );
        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.INDICATORS_WITH_INVALID_DENOMINATOR,
            this::getInvalidIndicatorDenominators );
        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.INDICATORS_VIOLATING_EXCLUSIVE_GROUP_SETS,
            this::getIndicatorsViolatingExclusiveGroupSets );

        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.PERIODS_DUPLICATES, this::getDuplicatePeriods );

        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.ORG_UNITS_WITH_CYCLIC_REFERENCES,
            this::getOrganisationUnitsWithCyclicReferences );
        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.ORG_UNITS_BEING_ORPHANED,
            this::getOrphanedOrganisationUnits );
        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.ORG_UNITS_WITHOUT_GROUPS,
            this::getOrganisationUnitsWithoutGroups );
        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.ORG_UNITS_VIOLATING_EXCLUSIVE_GROUP_SETS,
            this::getOrganisationUnitsViolatingExclusiveGroupSets );
        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.ORG_UNIT_GROUPS_WITHOUT_GROUP_SETS,
            this::getOrganisationUnitGroupsWithoutGroupSets );

        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.VALIDATION_RULES_WITHOUT_GROUPS,
            this::getValidationRulesWithoutGroups );
        registerNonDatabaseIntegrityCheck(
            DataIntegrityCheckType.VALIDATION_RULES_WITH_INVALID_LEFT_SIDE_EXPRESSION,
            this::getInvalidValidationRuleLeftSideExpressions );
        registerNonDatabaseIntegrityCheck(
            DataIntegrityCheckType.VALIDATION_RULES_WITH_INVALID_RIGHT_SIDE_EXPRESSION,
            this::getInvalidValidationRuleRightSideExpressions );

        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.PROGRAM_INDICATORS_WITH_INVALID_EXPRESSIONS,
            this::getInvalidProgramIndicatorExpressions );
        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.PROGRAM_INDICATORS_WITH_INVALID_FILTERS,
            this::getInvalidProgramIndicatorFilters );
        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.PROGRAM_INDICATORS_WITHOUT_EXPRESSION,
            this::getProgramIndicatorsWithNoExpression );

        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.PROGRAM_RULES_WITHOUT_CONDITION,
            this::getProgramRulesWithNoCondition );
        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.PROGRAM_RULES_WITHOUT_PRIORITY,
            this::getProgramRulesWithNoPriority );
        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.PROGRAM_RULES_WITHOUT_ACTION,
            this::getProgramRulesWithNoAction );

        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.PROGRAM_RULE_VARIABLES_WITHOUT_DATA_ELEMENT,
            this::getProgramRuleVariablesWithNoDataElement );
        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.PROGRAM_RULE_VARIABLES_WITHOUT_ATTRIBUTE,
            this::getProgramRuleVariablesWithNoAttribute );

        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.PROGRAM_RULE_ACTIONS_WITHOUT_DATA_OBJECT,
            this::getProgramRuleActionsWithNoDataObject );
        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.PROGRAM_RULE_ACTIONS_WITHOUT_NOTIFICATION,
            this::getProgramRuleActionsWithNoNotificationTemplate );
        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.PROGRAM_RULE_ACTIONS_WITHOUT_SECTION,
            this::getProgramRuleActionsWithNoSectionId );
        registerNonDatabaseIntegrityCheck( DataIntegrityCheckType.PROGRAM_RULE_ACTIONS_WITHOUT_STAGE,
            this::getProgramRuleActionsWithNoProgramStageId );
    }

    @Override
    @Transactional( readOnly = true )
    public FlattenedDataIntegrityReport getReport( Set<String> checks, JobProgress progress )
    {
        return new FlattenedDataIntegrityReport( getDetails( checks, progress ) );
    }

    /**
     * Get all ProgramIndicators with no expression.
     */
    List<DataIntegrityIssue> getProgramIndicatorsWithNoExpression()
    {
        return toSimpleIssueList( programIndicatorService.getProgramIndicatorsWithNoExpression().stream() );
    }

    /**
     * Get all ProgramIndicators with invalid expressions.
     */
    List<DataIntegrityIssue> getInvalidProgramIndicatorExpressions()
    {
        return getInvalidProgramIndicators( ProgramIndicator::getExpression,
            pi -> !programIndicatorService.expressionIsValid( pi.getExpression() ) );
    }

    /**
     * Get all ProgramIndicators with invalid filters.
     */
    List<DataIntegrityIssue> getInvalidProgramIndicatorFilters()
    {
        return getInvalidProgramIndicators( ProgramIndicator::getFilter,
            pi -> !programIndicatorService.filterIsValid( pi.getFilter() ) );
    }

    private List<DataIntegrityIssue> getInvalidProgramIndicators( Function<ProgramIndicator, String> property,
        Predicate<ProgramIndicator> filter )
    {
        List<ProgramIndicator> programIndicators = programIndicatorService.getAllProgramIndicators()
            .stream()
            .filter( filter )
            .collect( toList() );

        List<DataIntegrityIssue> issues = new ArrayList<>();
        for ( ProgramIndicator programIndicator : programIndicators )
        {
            String description = getInvalidExpressionDescription( property.apply( programIndicator ) );
            if ( description != null )
            {
                issues.add( toIssue( programIndicator, description ) );
            }
        }
        return issues;
    }

    /**
     * Get all ProgramRules with no priority and grouped them by {@link Program}
     */
    List<DataIntegrityIssue> getProgramRulesWithNoPriority()
    {
        return groupRulesByProgram( programRuleService.getProgramRulesWithNoPriority() );
    }

    /**
     * Get all ProgramRules with no action and grouped them by {@link Program}
     */
    List<DataIntegrityIssue> getProgramRulesWithNoAction()
    {
        return groupRulesByProgram( programRuleService.getProgramRulesWithNoAction() );
    }

    /**
     * Get all ProgramRules with no condition expression and grouped them by
     * {@link Program}
     */
    List<DataIntegrityIssue> getProgramRulesWithNoCondition()
    {
        return groupRulesByProgram( programRuleService.getProgramRulesWithNoCondition() );
    }

    /**
     * @return all {@link ProgramRuleAction} which are not linked to any
     *         DataElement/TrackedEntityAttribute
     */
    List<DataIntegrityIssue> getProgramRuleActionsWithNoDataObject()
    {
        return groupActionsByProgramRule( programRuleActionService.getProgramActionsWithNoLinkToDataObject() );
    }

    /**
     * @return all {@link ProgramRuleAction} which are not linked to any
     *         {@link org.hisp.dhis.notification.NotificationTemplate}
     */
    List<DataIntegrityIssue> getProgramRuleActionsWithNoNotificationTemplate()
    {
        return groupActionsByProgramRule( programRuleActionService.getProgramActionsWithNoLinkToNotification() );
    }

    /**
     * @return all {@link ProgramRuleAction} which are not linked to any
     *         {@link org.hisp.dhis.program.ProgramStageSection}
     */
    List<DataIntegrityIssue> getProgramRuleActionsWithNoSectionId()
    {
        return groupActionsByProgramRule( programRuleActionService.getProgramRuleActionsWithNoSectionId() );
    }

    /**
     * @return all {@link ProgramRuleAction} which are not linked to any
     *         {@link org.hisp.dhis.program.ProgramStage}
     */
    List<DataIntegrityIssue> getProgramRuleActionsWithNoProgramStageId()
    {
        return groupActionsByProgramRule( programRuleActionService.getProgramRuleActionsWithNoStageId() );
    }

    /**
     * @return all {@link ProgramRuleVariable} which are not linked to any
     *         DataElement and grouped them by {@link Program}
     */
    List<DataIntegrityIssue> getProgramRuleVariablesWithNoDataElement()
    {
        return groupVariablesByProgram( programRuleVariableService.getVariablesWithNoDataElement() );
    }

    /**
     * @return all {@link ProgramRuleVariable} which are not linked to any
     *         TrackedEntityAttribute and grouped them by {@link Program}
     */
    List<DataIntegrityIssue> getProgramRuleVariablesWithNoAttribute()
    {
        return groupVariablesByProgram( programRuleVariableService.getVariablesWithNoAttribute() );
    }

    private String getInvalidExpressionDescription( String expression )
    {
        try
        {
            expressionService.getExpressionDescription( expression, INDICATOR_EXPRESSION );
        }
        catch ( ParserException e )
        {
            return e.getMessage();
        }

        return null;
    }

    private static List<DataIntegrityIssue> groupRulesByProgram( List<ProgramRule> rules )
    {
        return groupBy( ProgramRule::getProgram, rules );
    }

    private static List<DataIntegrityIssue> groupVariablesByProgram(
        List<ProgramRuleVariable> variables )
    {
        return groupBy( ProgramRuleVariable::getProgram, variables );
    }

    private static List<DataIntegrityIssue> groupActionsByProgramRule(
        List<ProgramRuleAction> actions )
    {
        return groupBy( ProgramRuleAction::getProgramRule, actions );
    }

    private static <K extends IdentifiableObject, V extends IdentifiableObject> List<DataIntegrityIssue> groupBy(
        Function<V, K> property,
        Collection<V> values )
    {
        return values.stream().collect( groupingBy( property ) )
            .entrySet().stream()
            .map( e -> DataIntegrityIssue.toIssue( e.getKey(), e.getValue() ) )
            .collect( toUnmodifiableList() );
    }

    /*
     * Configuration based data integrity checks
     */

    private final Map<String, DataIntegrityCheck> checksByName = new ConcurrentHashMap<>();

    private final AtomicBoolean configurationsAreLoaded = new AtomicBoolean( false );

    @Override
    public Collection<DataIntegrityCheck> getDataIntegrityChecks()
    {
        ensureConfigurationsAreLoaded();
        return unmodifiableCollection( checksByName.values() );
    }

    @Override
    @Transactional( readOnly = true )
    public Map<String, DataIntegritySummary> getSummaries( Set<String> checks, JobProgress progress )
    {
        return runDataIntegrityChecks( "Data Integrity summary checks", expandChecks( checks ), progress,
            check -> check.getRunSummaryCheck().apply( check ) );
    }

    @Override
    @Transactional( readOnly = true )
    public Map<String, DataIntegrityDetails> getDetails( Set<String> checks, JobProgress progress )
    {
        return runDataIntegrityChecks( "Data Integrity details checks", expandChecks( checks ), progress,
            check -> check.getRunDetailsCheck().apply( check ) );
    }

    private <T> Map<String, T> runDataIntegrityChecks( String stageDesc, Set<String> checks, JobProgress progress,
        Function<DataIntegrityCheck, T> runCheck )
    {
        progress.startingProcess( "Data Integrity check" );
        progress.startingStage( stageDesc, checks.size() );
        Map<String, T> checkResults = new LinkedHashMap<>();
        progress.runStage( checks.stream().map( checksByName::get ).filter( Objects::nonNull ),
            DataIntegrityCheck::getDescription,
            check -> {
                T res = runCheck.apply( check );
                if ( res != null )
                {
                    checkResults.put( check.getName(), res );
                }
            } );
        progress.completedProcess( null );
        return checkResults;
    }

    private Set<String> expandChecks( Set<String> names )
    {
        ensureConfigurationsAreLoaded();
        if ( names == null || names.isEmpty() )
        {
            return unmodifiableSet( checksByName.keySet() );
        }
        Set<String> expanded = new LinkedHashSet<>();
        for ( String name : names )
        {
            String uniformName = name.toLowerCase().replace( '-', '_' );
            if ( uniformName.contains( "*" ) )
            {
                String pattern = uniformName.replace( "*", ".*" );
                for ( String existingName : checksByName.keySet() )
                {
                    if ( existingName.matches( pattern ) )
                    {
                        expanded.add( existingName );
                    }
                }
            }
            else
            {
                expanded.add( uniformName );
            }
        }
        return expanded;
    }

    private void ensureConfigurationsAreLoaded()
    {
        if ( configurationsAreLoaded.compareAndSet( false, true ) )
        {
            readDataIntegrityYaml( "data-integrity-checks.yaml",
                check -> checksByName.put( check.getName(), check ), this::querySummary, this::queryDetails );
        }
    }

    private Function<DataIntegrityCheck, DataIntegritySummary> querySummary( String sql )
    {
        return check -> {
            Object[] summary = (Object[]) sessionFactory.getCurrentSession()
                .createNativeQuery( sql ).getSingleResult();
            return new DataIntegritySummary( check, parseCount( summary[0] ), parsePercentage( summary[1] ) );
        };
    }

    private Function<DataIntegrityCheck, DataIntegrityDetails> queryDetails( String sql )
    {
        return check -> {
            @SuppressWarnings( "unchecked" )
            List<Object[]> rows = sessionFactory.getCurrentSession().createNativeQuery( sql ).list();
            return new DataIntegrityDetails( check, rows.stream()
                .map( row -> new DataIntegrityIssue( (String) row[0],
                    (String) row[1], row.length == 2 ? null : (String) row[2], null ) )
                .collect( toUnmodifiableList() ) );
        };
    }

    private static Double parsePercentage( Object value )
    {
        if ( value == null )
        {
            return null;
        }
        if ( value instanceof String )
        {
            return Double.parseDouble( value.toString().replace( "%", "" ) );
        }
        return ((Number) value).doubleValue();
    }

    private static int parseCount( Object value )
    {
        return value == null ? 0 : ((Number) value).intValue();
    }
}
