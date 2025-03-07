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
package org.hisp.dhis.webapi.controller;

import static org.hisp.dhis.webapi.WebClient.Body;
import static org.hisp.dhis.webapi.utils.WebClientUtils.assertStatus;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.hisp.dhis.feedback.ErrorCode;
import org.hisp.dhis.message.FakeMessageSender;
import org.hisp.dhis.message.MessageSender;
import org.hisp.dhis.outboundmessage.OutboundMessage;
import org.hisp.dhis.security.RestoreType;
import org.hisp.dhis.security.SecurityService;
import org.hisp.dhis.user.User;
import org.hisp.dhis.user.UserCredentials;
import org.hisp.dhis.webapi.DhisControllerConvenienceTest;
import org.hisp.dhis.webapi.json.JsonObject;
import org.hisp.dhis.webapi.json.domain.JsonErrorReport;
import org.hisp.dhis.webapi.json.domain.JsonImportSummary;
import org.hisp.dhis.webapi.json.domain.JsonWebMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/**
 * Tests the {@link org.hisp.dhis.webapi.controller.user.UserController}.
 *
 * @author Jan Bernitt
 */
class UserControllerTest extends DhisControllerConvenienceTest
{

    @Autowired
    private MessageSender messageSender;

    @Autowired
    private SecurityService securityService;

    private User peter;

    @BeforeEach
    void setUp()
    {
        peter = switchToNewUser( "Peter" );
        switchToSuperuser();
        assertStatus( HttpStatus.OK, PATCH( "/users/{id}?importReportMode=ERRORS", peter.getUid(),
            Body( "[{'op': 'replace', 'path': '/email', 'value': 'peter@pan.net'}]" ) ) );
    }

    @Test
    void testResetToInvite()
    {
        assertStatus( HttpStatus.NO_CONTENT, POST( "/users/" + peter.getUid() + "/reset" ) );
        OutboundMessage email = assertMessageSendTo( "peter@pan.net" );
        assertValidToken( extractTokenFromEmailText( email.getText() ) );
    }

    @Test
    void testResetToInvite_NoEmail()
    {
        assertStatus( HttpStatus.OK, PATCH( "/users/{id}", peter.getUid() + "?importReportMode=ERRORS",
            Body( "[{'op': 'replace', 'path': '/email', 'value': null}]" ) ) );
        assertEquals( "user_does_not_have_valid_email",
            POST( "/users/" + peter.getUid() + "/reset" ).error( HttpStatus.CONFLICT ).getMessage() );
    }

    @Test
    void testResetToInvite_NoAuthority()
    {
        switchToNewUser( "someone" );
        assertEquals( "You don't have the proper permissions to update this user.",
            POST( "/users/" + peter.getUid() + "/reset" ).error( HttpStatus.FORBIDDEN ).getMessage() );
    }

    @Test
    void testResetToInvite_NoSuchUser()
    {
        assertEquals( "Object not found for uid: does-not-exist",
            POST( "/users/does-not-exist/reset" ).error( HttpStatus.NOT_FOUND ).getMessage() );
    }

    @Test
    void testReplicateUser()
    {
        assertWebMessage( "Created", 201, "OK", "User replica created",
            POST( "/users/" + peter.getUid() + "/replica", "{'username':'peter2','password':'Saf€sEcre1'}" )
                .content() );
    }

    @Test
    void testReplicateUser_UserNameAlreadyTaken()
    {
        assertWebMessage( "Conflict", 409, "ERROR", "Username already taken: peter",
            POST( "/users/" + peter.getUid() + "/replica", "{'username':'peter','password':'Saf€sEcre1'}" )
                .content( HttpStatus.CONFLICT ) );
    }

    @Test
    void testReplicateUser_UserNotFound()
    {
        assertWebMessage( "Conflict", 409, "ERROR", "User not found: notfoundid",
            POST( "/users/notfoundid/replica", "{'username':'peter2','password':'Saf€sEcre1'}" )
                .content( HttpStatus.CONFLICT ) );
    }

    @Test
    void testReplicateUser_UserNameNotSpecified()
    {
        assertWebMessage( "Conflict", 409, "ERROR", "Username must be specified",
            POST( "/users/" + peter.getUid() + "/replica", "{'password':'Saf€sEcre1'}" )
                .content( HttpStatus.CONFLICT ) );
    }

    @Test
    void testReplicateUser_PasswordNotSpecified()
    {
        assertWebMessage( "Conflict", 409, "ERROR", "Password must be specified",
            POST( "/users/" + peter.getUid() + "/replica", "{'username':'peter2'}" ).content( HttpStatus.CONFLICT ) );
    }

    @Test
    void testReplicateUser_PasswordNotValid()
    {
        assertWebMessage( "Conflict", 409, "ERROR",
            "Password must have at least 8 characters, one digit, one uppercase",
            POST( "/users/" + peter.getUid() + "/replica", "{'username':'peter2','password':'lame'}" )
                .content( HttpStatus.CONFLICT ) );
    }

    @Test
    void testPutJsonObject()
    {
        JsonObject user = GET( "/users/{id}", peter.getUid() ).content();
        assertWebMessage( "OK", 200, "OK", null,
            PUT( "/38/users/" + peter.getUid(), user.toString() ).content( HttpStatus.OK ) );
    }

    @Test
    void testPutJsonObject_Pre38()
    {
        JsonObject user = GET( "/users/{id}", peter.getUid() ).content();
        JsonImportSummary summary = PUT( "/37/users/" + peter.getUid(), user.toString() ).content( HttpStatus.OK )
            .as( JsonImportSummary.class );
        assertEquals( "ImportReport", summary.getResponseType() );
        assertEquals( "OK", summary.getStatus() );
        assertEquals( 1, summary.getStats().getUpdated() );
        assertEquals( peter.getUid(), summary.getTypeReports().get( 0 ).getObjectReports().get( 0 ).getUid() );
    }

    @Test
    void testPutProperty_InvalidWhatsapp()
    {
        JsonWebMessage msg = assertWebMessage( "Conflict", 409, "ERROR",
            "One more more errors occurred, please see full details in import report.",
            PATCH( "/users/" + peter.getUid() + "?importReportMode=ERRORS",
                "[{'op': 'add', 'path': '/whatsApp', 'value': 'not-a-phone-no'}]" ).content( HttpStatus.CONFLICT ) );
        JsonErrorReport report = msg.getResponse()
            .find( JsonErrorReport.class, error -> error.getErrorCode() == ErrorCode.E4027 );
        assertEquals( "whatsApp", report.getErrorProperty() );
    }

    @Test
    void testPostJsonObject()
    {
        assertWebMessage( "Created", 201, "OK", null,
            POST( "/users/", "{'surname':'S.','firstName':'Harry','userCredentials':{'username':'harrys'}}" )
                .content( HttpStatus.CREATED ) );
    }

    @Test
    void testPostJsonObjectInvalidUsername()
    {
        JsonWebMessage msg = assertWebMessage( "Conflict", 409, "ERROR",
            "One more more errors occurred, please see full details in import report.",
            POST( "/users/", "{'surname':'S.','firstName':'Harry','userCredentials':{'username':'Harrys'}}" )
                .content( HttpStatus.CONFLICT ) );
        JsonErrorReport report = msg.getResponse()
            .find( JsonErrorReport.class, error -> error.getErrorCode() == ErrorCode.E4049 );
        assertEquals( "username", report.getErrorProperty() );
    }

    @Test
    void testPostJsonInvite()
    {
        assertWebMessage( "Created", 201, "OK", null, POST( "/users/invite",
            "{'surname':'S.','firstName':'Harry', 'email':'test@example.com', 'userCredentials':{'username':'harrys'}}" )
                .content( HttpStatus.CREATED ) );
    }

    private String extractTokenFromEmailText( String message )
    {
        int tokenPos = message.indexOf( "?token=" );
        return message.substring( tokenPos + 7, message.indexOf( '\n', tokenPos ) ).trim();
    }

    /**
     * Unfortunately this is not yet a spring endpoint so we have to do it
     * directly instead of using the REST API.
     */
    private void assertValidToken( String token )
    {
        String[] idAndRestoreToken = securityService.decodeEncodedTokens( token );
        String idToken = idAndRestoreToken[0];
        String restoreToken = idAndRestoreToken[1];
        UserCredentials userCredentials = userService.getUserCredentialsByIdToken( idToken );
        assertNotNull( userCredentials );
        String errorMessage = securityService.verifyRestoreToken( userCredentials, restoreToken,
            RestoreType.RECOVER_PASSWORD );
        assertNull( errorMessage );
    }

    private OutboundMessage assertMessageSendTo( String email )
    {
        List<OutboundMessage> messagesByEmail = ((FakeMessageSender) messageSender).getMessagesByEmail( email );
        assertTrue( messagesByEmail.size() > 0 );
        return messagesByEmail.get( 0 );
    }
}
