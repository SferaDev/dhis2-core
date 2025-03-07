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
package org.hisp.dhis.tracker.validation.hooks;

import static org.hisp.dhis.tracker.report.TrackerErrorCode.E1005;
import static org.hisp.dhis.tracker.report.TrackerErrorCode.E1010;
import static org.hisp.dhis.tracker.report.TrackerErrorCode.E1011;
import static org.hisp.dhis.tracker.report.TrackerErrorCode.E1013;
import static org.hisp.dhis.tracker.report.TrackerErrorCode.E1068;
import static org.hisp.dhis.tracker.report.TrackerErrorCode.E1069;
import static org.hisp.dhis.tracker.report.TrackerErrorCode.E1070;
import static org.hisp.dhis.tracker.report.TrackerErrorCode.E4006;
import static org.hisp.dhis.tracker.validation.hooks.ValidationUtils.trackedEntityInstanceExist;

import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.program.Program;
import org.hisp.dhis.program.ProgramStage;
import org.hisp.dhis.relationship.RelationshipType;
import org.hisp.dhis.trackedentity.TrackedEntityType;
import org.hisp.dhis.tracker.domain.Enrollment;
import org.hisp.dhis.tracker.domain.Event;
import org.hisp.dhis.tracker.domain.Relationship;
import org.hisp.dhis.tracker.domain.TrackedEntity;
import org.hisp.dhis.tracker.report.TrackerErrorCode;
import org.hisp.dhis.tracker.report.ValidationErrorReporter;
import org.hisp.dhis.tracker.validation.TrackerImportValidationContext;
import org.springframework.stereotype.Component;

/**
 * @author Morten Svanæs <msvanaes@dhis2.org>
 */
@Component
public class PreCheckMetaValidationHook
    extends AbstractTrackerDtoValidationHook
{
    @Override
    public void validateTrackedEntity( ValidationErrorReporter reporter, TrackedEntity tei )
    {
        TrackerImportValidationContext context = reporter.getValidationContext();

        OrganisationUnit organisationUnit = context.getOrganisationUnit( tei.getOrgUnit() );
        if ( organisationUnit == null )
        {
            addError( reporter, tei, TrackerErrorCode.E1049, tei.getOrgUnit() );
        }

        TrackedEntityType entityType = context.getTrackedEntityType( tei.getTrackedEntityType() );
        if ( entityType == null )
        {
            addError( reporter, tei, E1005, tei.getTrackedEntityType() );
        }
    }

    @Override
    public void validateEnrollment( ValidationErrorReporter reporter, Enrollment enrollment )
    {
        TrackerImportValidationContext context = reporter.getValidationContext();

        OrganisationUnit organisationUnit = context.getOrganisationUnit( enrollment.getOrgUnit() );
        addErrorIfNull( organisationUnit, reporter, enrollment, E1070, enrollment.getOrgUnit() );

        Program program = context.getProgram( enrollment.getProgram() );
        addErrorIfNull( program, reporter, enrollment, E1069, enrollment.getProgram() );

        addErrorIf( () -> !trackedEntityInstanceExist( context, enrollment.getTrackedEntity() ), reporter, enrollment,
            E1068, enrollment.getTrackedEntity() );
    }

    @Override
    public void validateEvent( ValidationErrorReporter reporter, Event event )
    {
        TrackerImportValidationContext context = reporter.getValidationContext();

        OrganisationUnit organisationUnit = context.getOrganisationUnit( event.getOrgUnit() );
        addErrorIfNull( organisationUnit, reporter, event, E1011, event.getOrgUnit() );

        Program program = context.getProgram( event.getProgram() );
        addErrorIfNull( program, reporter, event, E1010, event.getProgram() );

        ProgramStage programStage = context.getProgramStage( event.getProgramStage() );
        addErrorIfNull( programStage, reporter, event, E1013, event.getProgramStage() );
    }

    @Override
    public void validateRelationship( ValidationErrorReporter reporter, Relationship relationship )
    {
        TrackerImportValidationContext context = reporter.getValidationContext();

        RelationshipType relationshipType = context.getRelationShipType( relationship.getRelationshipType() );

        addErrorIfNull( relationshipType, reporter, relationship, E4006, relationship.getRelationshipType() );
    }

    @Override
    public boolean removeOnError()
    {
        return true;
    }

}
