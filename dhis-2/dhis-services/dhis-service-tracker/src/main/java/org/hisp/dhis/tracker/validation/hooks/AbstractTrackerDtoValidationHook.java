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

import static org.hisp.dhis.tracker.report.TrackerErrorCode.E1125;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.hisp.dhis.common.ValueTypedDimensionalItemObject;
import org.hisp.dhis.option.Option;
import org.hisp.dhis.tracker.TrackerImportStrategy;
import org.hisp.dhis.tracker.TrackerType;
import org.hisp.dhis.tracker.bundle.TrackerBundle;
import org.hisp.dhis.tracker.domain.Enrollment;
import org.hisp.dhis.tracker.domain.Event;
import org.hisp.dhis.tracker.domain.Relationship;
import org.hisp.dhis.tracker.domain.TrackedEntity;
import org.hisp.dhis.tracker.domain.TrackerDto;
import org.hisp.dhis.tracker.report.Error;
import org.hisp.dhis.tracker.report.TrackerValidationReport;
import org.hisp.dhis.tracker.validation.TrackerImportValidationContext;
import org.hisp.dhis.tracker.validation.TrackerValidationHook;

import com.google.common.collect.ImmutableMap;

/**
 * @author Morten Svanæs <msvanaes@dhis2.org>
 */
public abstract class AbstractTrackerDtoValidationHook
    implements TrackerValidationHook
{
    interface TriConsumer<T, U, V>
    {
        void accept( T t, U u, V v );
    }

    private final Map<TrackerType, TriConsumer<TrackerValidationReport, TrackerImportValidationContext, TrackerDto>> validationMap = ImmutableMap
        .<TrackerType, TriConsumer<TrackerValidationReport, TrackerImportValidationContext, TrackerDto>> builder()
        .put( TrackerType.TRACKED_ENTITY,
            (( report, context, dto ) -> validateTrackedEntity( report, context, (TrackedEntity) dto )) )
        .put( TrackerType.ENROLLMENT,
            (( report, context, dto ) -> validateEnrollment( report, context, (Enrollment) dto )) )
        .put( TrackerType.EVENT, (( report, context, dto ) -> validateEvent( report, context, (Event) dto )) )
        .put( TrackerType.RELATIONSHIP,
            (( report, context, dto ) -> validateRelationship( report, context, (Relationship) dto )) )
        .build();

    /**
     * This constructor is used by the PreCheck* hooks
     */
    public AbstractTrackerDtoValidationHook()
    {
    }

    /**
     * Template method Must be implemented if dtoTypeClass == Event or
     * dtoTypeClass == null
     *
     * @param report TrackerValidationReport instance
     * @param context TrackerValidationContext
     * @param event entity to validate
     */
    public void validateEvent( TrackerValidationReport report, TrackerImportValidationContext context, Event event )
    {
    }

    /**
     * Template method Must be implemented if dtoTypeClass == Enrollment or
     * dtoTypeClass == null
     *
     * @param report TrackerValidationReport instance
     * @param context TrackerValidationContext
     * @param enrollment entity to validate
     */
    public void validateEnrollment( TrackerValidationReport report, TrackerImportValidationContext context,
        Enrollment enrollment )
    {
    }

    /**
     * Template method Must be implemented if dtoTypeClass == Relationship or
     * dtoTypeClass == null
     *
     * @param report TrackerValidationReport instance
     * @param context TrackerValidationContext
     * @param relationship entity to validate
     */
    public void validateRelationship( TrackerValidationReport report, TrackerImportValidationContext context,
        Relationship relationship )
    {
    }

    /**
     * Template method Must be implemented if dtoTypeClass == TrackedEntity or
     * dtoTypeClass == null
     *
     * @param report TrackerValidationReport instance
     * @param context TrackerValidationContext
     * @param tei entity to validate
     */
    public void validateTrackedEntity( TrackerValidationReport report, TrackerImportValidationContext context,
        TrackedEntity tei )
    {
    }

    protected <T extends ValueTypedDimensionalItemObject> void validateOptionSet( TrackerValidationReport report,
        TrackerDto dto,
        T optionalObject, String value )
    {
        Optional.ofNullable( optionalObject.getOptionSet() )
            .ifPresent( optionSet -> report.addErrorIf(
                () -> optionSet.getOptions().stream().filter( Objects::nonNull )
                    .noneMatch( o -> o.getCode().equalsIgnoreCase( value ) ),
                () -> Error.builder()
                    .uid( dto.getUid() )
                    .trackerType( dto.getTrackerType() )
                    .errorCode( E1125 )
                    .addArg( value )
                    .addArg( optionalObject.getUid() )
                    .addArg( optionalObject.getClass().getSimpleName() )
                    .addArg( optionalObject.getOptionSet().getOptions().stream().filter( Objects::nonNull )
                        .map( Option::getCode )
                        .collect( Collectors.joining( "," ) ) )
                    .build() ) );
    }

    /**
     * Delegating validate method, this delegates validation to the different
     * implementing hooks.
     *
     * @param context validation context
     */
    @Override
    public void validate( TrackerValidationReport report, TrackerImportValidationContext context )
    {
        TrackerBundle bundle = context.getBundle();
        /*
         * Validate the bundle, by passing each Tracker entities collection to
         * the validation hooks. If a validation hook reports errors and has
         * 'removeOnError=true' the Tracker entity under validation will be
         * removed from the bundle.
         */

        validateTrackerDtos( report, context, bundle.getTrackedEntities() );
        validateTrackerDtos( report, context, bundle.getEnrollments() );
        validateTrackerDtos( report, context, bundle.getEvents() );
        validateTrackerDtos( report, context, bundle.getRelationships() );
    }

    private void validateTrackerDtos( TrackerValidationReport report, TrackerImportValidationContext context,
        List<? extends TrackerDto> dtos )
    {
        Iterator<? extends TrackerDto> iter = dtos.iterator();
        while ( iter.hasNext() )
        {
            TrackerDto dto = iter.next();
            if ( needsToRun( context.getBundle().getStrategy( dto ) ) )
            {
                validationMap.get( dto.getTrackerType() ).accept( report, context, dto );
                if ( removeOnError() && didNotPassValidation( report, dto.getUid() ) )
                {
                    iter.remove();
                }
            }
        }
    }

    public boolean needsToRun( TrackerImportStrategy strategy )
    {
        return strategy != TrackerImportStrategy.DELETE;
    }

    /**
     * Signal the implementing Validator hook that, upon validation error, the
     * Tracker entity under validation must be removed from the payload.
     *
     */
    public boolean removeOnError()
    {
        return false;
    }

    private boolean didNotPassValidation( TrackerValidationReport report, String uid )
    {
        return report.getErrors().stream().anyMatch( r -> r.getUid().equals( uid ) );
    }
}
