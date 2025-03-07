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
package org.hisp.dhis.actions.metadata;

import com.google.gson.JsonObject;
import org.hisp.dhis.actions.RestApiActions;
import org.hisp.dhis.helpers.JsonObjectBuilder;
import org.hisp.dhis.utils.DataGenerator;

/**
 * @author Gintare Vilkelyte <vilkelyte.gintare@gmail.com>
 */
public class TrackedEntityAttributeActions
    extends RestApiActions
{
    public TrackedEntityAttributeActions( )
    {
        super( "/trackedEntityAttributes" );
    }

    public String create( String valueType ) {
        JsonObject ob = build( valueType );

        return this.post( ob ).validateStatus( 201 ).extractUid();
    }

    public String create( String valueType, Boolean unique )
    {
        JsonObject ob = new JsonObjectBuilder( build( valueType ) )
            .addProperty( "unique", String.valueOf( unique ) )
            .build();

        return this.post( ob ).validateStatus( 201 ).extractUid();
    }

    public String createOptionSetAttribute( String optionSet ) {
        JsonObject ob = new JsonObjectBuilder( build( "TEXT" ))
            .addObject( "optionSet", new JsonObjectBuilder()
                .addProperty( "id", optionSet )
                .build())
            .build();

        return this.post( ob ).validateStatus( 201 ).extractUid();
    }

    private JsonObject build( String valueType ) {
       return new JsonObjectBuilder()
            .addProperty( "name", "TA TEA" + DataGenerator.randomString() )
            .addProperty( "shortName", "TA TEA " + DataGenerator.randomString() )
            .addProperty( "valueType", valueType )
            .addProperty( "aggregationType", "NONE" )
            .build();
    }
}
