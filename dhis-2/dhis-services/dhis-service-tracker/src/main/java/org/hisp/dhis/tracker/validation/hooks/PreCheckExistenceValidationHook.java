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

import static org.hisp.dhis.tracker.report.TrackerErrorCode.E1002;
import static org.hisp.dhis.tracker.report.TrackerErrorCode.E1030;
import static org.hisp.dhis.tracker.report.TrackerErrorCode.E1032;
import static org.hisp.dhis.tracker.report.TrackerErrorCode.E1063;
import static org.hisp.dhis.tracker.report.TrackerErrorCode.E1080;
import static org.hisp.dhis.tracker.report.TrackerErrorCode.E1081;
import static org.hisp.dhis.tracker.report.TrackerErrorCode.E1082;
import static org.hisp.dhis.tracker.report.TrackerErrorCode.E1113;
import static org.hisp.dhis.tracker.report.TrackerErrorCode.E1114;
import static org.hisp.dhis.tracker.report.TrackerErrorCode.E4015;

import org.hisp.dhis.program.ProgramInstance;
import org.hisp.dhis.program.ProgramStageInstance;
import org.hisp.dhis.trackedentity.TrackedEntityInstance;
import org.hisp.dhis.tracker.TrackerImportStrategy;
import org.hisp.dhis.tracker.domain.Enrollment;
import org.hisp.dhis.tracker.domain.Event;
import org.hisp.dhis.tracker.domain.Relationship;
import org.hisp.dhis.tracker.domain.TrackedEntity;
import org.hisp.dhis.tracker.report.ValidationErrorReporter;
import org.hisp.dhis.tracker.validation.TrackerImportValidationContext;
import org.springframework.stereotype.Component;

/**
 * @author Morten Svanæs <msvanaes@dhis2.org>
 */
@Component
public class PreCheckExistenceValidationHook
    extends AbstractTrackerDtoValidationHook
{
    @Override
    public void validateTrackedEntity( ValidationErrorReporter reporter, TrackedEntity trackedEntity )
    {
        TrackerImportValidationContext context = reporter.getValidationContext();
        TrackerImportStrategy importStrategy = context.getStrategy( trackedEntity );

        TrackedEntityInstance existingTe = context
            .getTrackedEntityInstance( trackedEntity.getTrackedEntity() );

        // If the tracked entity is soft-deleted no operation is allowed
        if ( existingTe != null && existingTe.isDeleted() )
        {
            addError( reporter, trackedEntity, E1114, trackedEntity.getTrackedEntity() );
            return;
        }

        if ( existingTe != null && importStrategy.isCreate() )
        {
            addError( reporter, trackedEntity, E1002, trackedEntity.getTrackedEntity() );
        }
        else if ( existingTe == null && importStrategy.isUpdateOrDelete() )
        {
            addError( reporter, trackedEntity, E1063, trackedEntity.getTrackedEntity() );
        }
    }

    @Override
    public void validateEnrollment( ValidationErrorReporter reporter, Enrollment enrollment )
    {
        TrackerImportValidationContext context = reporter.getValidationContext();
        TrackerImportStrategy importStrategy = context.getStrategy( enrollment );

        ProgramInstance existingPi = context.getProgramInstance( enrollment.getEnrollment() );

        // If the tracked entity is soft-deleted no operation is allowed
        if ( existingPi != null && existingPi.isDeleted() )
        {
            addError( reporter, enrollment, E1113, enrollment.getEnrollment() );
            return;
        }

        if ( existingPi != null && importStrategy.isCreate() )
        {
            addError( reporter, enrollment, E1080, enrollment.getEnrollment() );
        }
        else if ( existingPi == null && importStrategy.isUpdateOrDelete() )
        {
            addError( reporter, enrollment, E1081, enrollment.getEnrollment() );
        }
    }

    @Override
    public void validateEvent( ValidationErrorReporter reporter, Event event )
    {
        TrackerImportValidationContext context = reporter.getValidationContext();
        TrackerImportStrategy importStrategy = context.getStrategy( event );

        ProgramStageInstance existingPsi = context.getProgramStageInstance( event.getEvent() );

        // If the event is soft-deleted no operation is allowed
        if ( existingPsi != null && existingPsi.isDeleted() )
        {
            addError( reporter, event, E1082, event.getEvent() );
            return;
        }

        if ( existingPsi != null && importStrategy.isCreate() )
        {
            addError( reporter, event, E1030, event.getEvent() );
        }
        else if ( existingPsi == null && importStrategy.isUpdateOrDelete() )
        {
            addError( reporter, event, E1032, event.getEvent() );
        }
    }

    @Override
    public void validateRelationship( ValidationErrorReporter reporter, Relationship relationship )
    {
        TrackerImportValidationContext context = reporter.getValidationContext();

        org.hisp.dhis.relationship.Relationship existingRelationship = context.getRelationship( relationship );

        if ( existingRelationship != null )
        {
            addWarning( reporter, relationship, E4015,
                relationship.getRelationship() );
        }
    }

    @Override
    public boolean needsToRun( TrackerImportStrategy strategy )
    {
        return true;
    }

    @Override
    public boolean removeOnError()
    {
        return true;
    }
}
