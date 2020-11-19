/*
 * Copyright (c) 2004-2020, University of Oslo
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

package org.hisp.dhis.tracker_v2.tei;

import com.google.gson.JsonObject;
import org.hisp.dhis.ApiTest;
import org.hisp.dhis.Constants;
import org.hisp.dhis.actions.LoginActions;
import org.hisp.dhis.actions.tracker.TEIActions;
import org.hisp.dhis.actions.tracker_v2.TrackerActions;
import org.hisp.dhis.dto.ApiResponse;
import org.hisp.dhis.dto.TrackerApiResponse;
import org.hisp.dhis.helpers.file.FileReaderUtils;
import org.hisp.dhis.helpers.JsonObjectBuilder;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import java.io.File;

import static org.hamcrest.Matchers.*;

/**
 * @author Gintare Vilkelyte <vilkelyte.gintare@gmail.com>
 */
public class TrackerImporter_teiImportTests
    extends ApiTest
{
    private TrackerActions trackerActions;

    private TEIActions teiActions;

    @BeforeAll
    public void beforeAll()
    {
        trackerActions = new TrackerActions();
        teiActions = new TEIActions();

        new LoginActions().loginAsSuperUser();
    }

    @Test
    public void shouldImportTei()
        throws JSONException
    {
        // arrange
        JsonObject trackedEntities = new JsonObjectBuilder()
            .addProperty( "trackedEntityType", "Q9GufDoplCL" )
            .addProperty( "orgUnit", Constants.ORG_UNIT_IDS[0] )
            .wrapIntoArray( "trackedEntities" );

        // act
        TrackerApiResponse response = trackerActions.postAndGetJobReport( trackedEntities );

        response
            .validateSuccessfulImport()
            .validate()
            .body( "bundleReport.typeReportMap.TRACKED_ENTITY", notNullValue() )
            .rootPath( "bundleReport.typeReportMap.TRACKED_ENTITY" )
            .body( "stats.created", equalTo( 1 ) )
            .body( "objectReports", notNullValue() )
            .body( "objectReports[0].errorReports", empty() );

        // assert that the tei was imported
        String teiId = response.extractImportedTeis().get( 0 );

        ApiResponse teiResponse = teiActions.get( teiId );
        teiResponse.validate()
            .statusCode( 200 );

        JSONAssert.assertEquals( trackedEntities.get( "trackedEntities" ).getAsJsonArray().get( 0 ).toString(),
            teiResponse.getBody().toString(), false );
    }

    @Test
    public void shouldImportTeiWithAttributes()
        throws Exception
    {
        JsonObject teiBody = new FileReaderUtils()
            .readJsonAndGenerateData( new File( "src/test/resources/tracker/v2/teis/tei.json" ) );

        // act
        TrackerApiResponse response = trackerActions.postAndGetJobReport( teiBody );

        // assert
        response.validateSuccessfulImport()
            .validate()
            .body( "bundleReport.typeReportMap.TRACKED_ENTITY", notNullValue() )
            .rootPath( "bundleReport.typeReportMap.TRACKED_ENTITY" )
            .body( "stats.created", equalTo( 1 ) )
            .body( "objectReports", notNullValue() )
            .body( "objectReports[0].errorReports", empty() );

        // assert that the TEI was imported
        String teiId = response.extractImportedTeis().get( 0 );

        ApiResponse teiResponse = teiActions.get( teiId );

        teiResponse.validate()
            .statusCode( 200 );

        JSONAssert
            .assertEquals( teiBody.get( "trackedEntities" ).getAsJsonArray().get( 0 ).toString(), teiResponse.getBody().toString(),
                false );
    }

    @Test
    public void shouldImportTeisInBulk()
        throws Exception
    {
        // todo add enrollments and events to the payload
        JsonObject teiBody = new FileReaderUtils()
            .readJsonAndGenerateData( new File( "src/test/resources/tracker/v2/teis/teis.json" ) );

        // act
        ApiResponse response = trackerActions.postAndGetJobReport( teiBody );

        response.validate().statusCode( 200 )
            .body( "status", equalTo( "OK" ) )
            .body( "stats.created", equalTo( 2 ) )
            .body( "bundleReport.typeReportMap.TRACKED_ENTITY", notNullValue() )
            .body( "bundleReport.typeReportMap.TRACKED_ENTITY.objectReports", hasSize( 2 ) );
    }

}
