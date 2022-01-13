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
package org.hisp.dhis.tracker.report;

import static org.hisp.dhis.utils.Assertions.assertContainsOnly;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.hisp.dhis.common.CodeGenerator;
import org.hisp.dhis.tracker.TrackerType;
import org.hisp.dhis.tracker.domain.Event;
import org.hisp.dhis.tracker.domain.TrackerDto;
import org.hisp.dhis.tracker.validation.ValidationFailFastException;
import org.junit.jupiter.api.Test;

class TrackerValidationReportTest
{

    @Test
    void addErrorIfItDoesNotExist()
    {

        TrackerValidationReport report = new TrackerValidationReport();
        Error error = newError();

        report.addError( error );

        assertNotNull( report.getErrors() );
        assertEquals( 1, report.getErrors().size() );
        assertContainsOnly( report.getErrors(), error );

        report.addError( error );

        assertEquals( 1, report.getErrors().size() );
    }

    @Test
    void addErrorThrowsInFailFastModeIrrespectiveOfWhetherErrorExisted()
    {

        TrackerValidationReport report = new TrackerValidationReport( true );
        Error error = newError();

        assertThrows( ValidationFailFastException.class, () -> report.addError( error ) );

        assertContainsOnly( report.getErrors(), error );

        assertThrows( ValidationFailFastException.class, () -> report.addError( error ) );

        assertContainsOnly( report.getErrors(), error );
    }

    @Test
    void addErrorIfExpressionIsTrue()
    {

        TrackerValidationReport report = new TrackerValidationReport();
        Error error = newError();

        report.addErrorIf( () -> true, () -> error );

        assertNotNull( report.getErrors() );
        assertContainsOnly( report.getErrors(), error );
    }

    @Test
    void addErrorIfInFailFastModeThrowsIfExpressionIsTrue()
    {

        TrackerValidationReport report = new TrackerValidationReport( true );
        Error error = newError();

        assertThrows( ValidationFailFastException.class, () -> report.addErrorIf( () -> true, () -> error ) );

        assertContainsOnly( report.getErrors(), error );
    }

    @Test
    void addErrorIfDoesNotAddErrorIfExpressionIsFalse()
    {

        TrackerValidationReport report = new TrackerValidationReport();

        report.addErrorIf( () -> false, this::newError );

        assertFalse( report.hasErrors() );
    }

    @Test
    void addErrorsThrowsInFailFastModeIrrespectiveOfWhetherAnyErrorExisted()
    {

        TrackerValidationReport report = new TrackerValidationReport( true );
        Error error1 = newError( CodeGenerator.generateUid(), TrackerErrorCode.E1001 );
        Error error2 = newError( CodeGenerator.generateUid(), TrackerErrorCode.E1002 );

        // swallow this one to test only non-existing errors are added and
        // exception is thrown by addErrors
        try
        {
            report.addError( error1 );
        }
        catch ( Exception e )
        {
            // another test is concerned with whether addError throws and what
            // the exception contains
        }

        assertContainsOnly( report.getErrors(), error1 );

        assertThrows( ValidationFailFastException.class, () -> report.addErrors( List.of( error1, error2 ) ) );

        assertContainsOnly( report.getErrors(), error1, error2 );
    }

    @Test
    void addErrorsIfTheyDoNotExist()
    {

        TrackerValidationReport report = new TrackerValidationReport();
        Error error1 = newError( CodeGenerator.generateUid(), TrackerErrorCode.E1001 );
        Error error2 = newError( CodeGenerator.generateUid(), TrackerErrorCode.E1002 );

        report.addError( error1 );

        assertContainsOnly( report.getErrors(), error1 );

        report.addErrors( List.of( error1, error2 ) );

        assertContainsOnly( report.getErrors(), error1, error2 );
    }

    @Test
    void addWarningIfItDoesNotExist()
    {

        TrackerValidationReport report = new TrackerValidationReport();
        Warning warning = newWarning();

        report.addWarning( warning );

        assertNotNull( report.getWarnings() );
        assertEquals( 1, report.getWarnings().size() );
        assertContainsOnly( report.getWarnings(), warning );

        report.addWarning( warning );

        assertEquals( 1, report.getWarnings().size() );
    }

    @Test
    void addWarningsIfTheyDoNotExist()
    {

        TrackerValidationReport report = new TrackerValidationReport();
        Warning warning1 = newWarning( CodeGenerator.generateUid(), TrackerErrorCode.E1001 );
        Warning warning2 = newWarning( CodeGenerator.generateUid(), TrackerErrorCode.E1002 );

        report.addWarning( warning1 );

        assertContainsOnly( report.getWarnings(), warning1 );

        report.addWarnings( List.of( warning1, warning2 ) );

        assertContainsOnly( report.getWarnings(), warning1, warning2 );
    }

    @Test
    void hasErrorsReturnsFalse()
    {

        TrackerValidationReport report = new TrackerValidationReport();

        assertFalse( report.hasErrors() );
    }

    @Test
    void hasErrorsReturnsTrue()
    {

        TrackerValidationReport report = new TrackerValidationReport();

        report.addError( newError() );

        assertTrue( report.hasErrors() );
    }

    @Test
    void hasWarningsReturnsFalse()
    {

        TrackerValidationReport report = new TrackerValidationReport();

        assertFalse( report.hasWarnings() );
    }

    @Test
    void hasWarningsReturnsTrue()
    {

        TrackerValidationReport report = new TrackerValidationReport();

        report.addWarning( newWarning() );

        assertTrue( report.hasWarnings() );
    }

    @Test
    void hasTimingsReturnsFalse()
    {

        TrackerValidationReport report = new TrackerValidationReport();

        assertFalse( report.hasTimings() );
    }

    @Test
    void hasTimingsReturnsTrue()
    {

        TrackerValidationReport report = new TrackerValidationReport();

        report.addTiming( new Timing( "1min", "validation" ) );

        assertTrue( report.hasTimings() );
    }

    @Test
    void hasErrorReportFound()
    {

        TrackerValidationReport report = new TrackerValidationReport();
        Error error = newError();
        report.addError( error );

        assertTrue( report.hasError( r -> error.getUid().equals( r.getUid() ) ) );
    }

    @Test
    void hasErrorReportNotFound()
    {

        TrackerValidationReport report = new TrackerValidationReport();
        Error error = newError( TrackerErrorCode.E1006 );
        report.addError( error );

        assertFalse( report.hasError( r -> TrackerErrorCode.E1048 == r.getErrorCode() ) );
    }

    @Test
    void hasWarningReportFound()
    {

        TrackerValidationReport report = new TrackerValidationReport();
        Warning warning = newWarning();
        report.addWarning( warning );

        assertTrue( report.hasWarning( r -> warning.getUid().equals( r.getUid() ) ) );
    }

    @Test
    void hasWarningReportNotFound()
    {

        TrackerValidationReport report = new TrackerValidationReport();
        Warning warning = newWarning( TrackerErrorCode.E1006 );
        report.addWarning( warning );

        assertFalse( report.hasWarning( r -> TrackerErrorCode.E1048 == r.getWarningCode() ) );
    }

    @Test
    void sizeReturnsErrorCountUniqueByUid()
    {

        TrackerValidationReport report = new TrackerValidationReport();
        Error error1 = newError( CodeGenerator.generateUid(), TrackerErrorCode.E1006 );
        Error error2 = newError( error1.getUid(), TrackerErrorCode.E1000 );
        Error error3 = newError( CodeGenerator.generateUid(), TrackerErrorCode.E1000 );

        report
            .addError( error1 )
            .addError( error2 )
            .addError( error3 );

        assertNotNull( report.getErrors() );
        assertEquals( 3, report.getErrors().size() );
        assertEquals( 2, report.size() );
    }

    @Test
    void isInvalidReturnsTrueWhenDtoHasError()
    {

        TrackerValidationReport report = new TrackerValidationReport();
        Event event = Event.builder().event( CodeGenerator.generateUid() ).build();

        report.addError( newError( event ) );

        assertTrue( report.isInvalid( event ) );
    }

    @Test
    void isInvalidReturnsFalseWhenDtoHasNoError()
    {

        TrackerValidationReport report = new TrackerValidationReport();
        Event event = Event.builder().event( CodeGenerator.generateUid() ).build();

        assertFalse( report.isInvalid( event ) );
    }

    private Error newError()
    {
        return newError( TrackerErrorCode.E9999 );
    }

    private Error newError( TrackerErrorCode code )
    {
        return newError( CodeGenerator.generateUid(), code );
    }

    private Error newError( String uid, TrackerErrorCode code )
    {
        return new Error( "", code, TrackerType.EVENT, uid );
    }

    private Error newError( TrackerDto dto )
    {
        return new Error( "", TrackerErrorCode.E9999, dto.getTrackerType(), dto.getUid() );
    }

    private Warning newWarning()
    {
        return newWarning( CodeGenerator.generateUid(), TrackerErrorCode.E9999 );
    }

    private Warning newWarning( TrackerErrorCode code )
    {
        return newWarning( CodeGenerator.generateUid(), code );
    }

    private Warning newWarning( String uid, TrackerErrorCode code )
    {
        return new Warning( "", code, TrackerType.EVENT, uid );
    }
}