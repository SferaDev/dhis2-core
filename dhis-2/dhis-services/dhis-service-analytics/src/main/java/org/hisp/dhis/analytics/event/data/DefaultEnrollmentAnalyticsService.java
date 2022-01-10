/*
 * Copyright (c) 2004-2021, University of Oslo
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
package org.hisp.dhis.analytics.event.data;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hisp.dhis.analytics.event.data.EnrollmentGridHeaderHandler.createGridWithDefaultHeaders;
import static org.hisp.dhis.analytics.event.data.EnrollmentGridHeaderHandler.createGridWithParamHeaders;

import java.util.ArrayList;

import org.hisp.dhis.analytics.AnalyticsSecurityManager;
import org.hisp.dhis.analytics.event.EnrollmentAnalyticsManager;
import org.hisp.dhis.analytics.event.EnrollmentAnalyticsService;
import org.hisp.dhis.analytics.event.EventQueryParams;
import org.hisp.dhis.analytics.event.EventQueryPlanner;
import org.hisp.dhis.analytics.event.EventQueryValidator;
import org.hisp.dhis.common.Grid;
import org.hisp.dhis.util.Timer;
import org.springframework.stereotype.Service;

/**
 * @author Markus Bekken
 */
@Service( "org.hisp.dhis.analytics.event.EnrollmentAnalyticsService" )
public class DefaultEnrollmentAnalyticsService
    extends
    AbstractAnalyticsService
    implements
    EnrollmentAnalyticsService
{
    private final EnrollmentAnalyticsManager enrollmentAnalyticsManager;

    private final EventQueryPlanner queryPlanner;

    public DefaultEnrollmentAnalyticsService( EnrollmentAnalyticsManager enrollmentAnalyticsManager,
        AnalyticsSecurityManager securityManager, EventQueryPlanner queryPlanner, EventQueryValidator queryValidator )
    {
        super( securityManager, queryValidator );

        checkNotNull( enrollmentAnalyticsManager );
        checkNotNull( queryPlanner );

        this.enrollmentAnalyticsManager = enrollmentAnalyticsManager;
        this.queryPlanner = queryPlanner;
    }

    // -------------------------------------------------------------------------
    // EventAnalyticsService implementation
    // -------------------------------------------------------------------------

    @Override
    public Grid getEnrollments( EventQueryParams params )
    {
        return getGrid( params );
    }

    @Override
    protected Grid createGridWithHeaders( EventQueryParams params )
    {
        // It means that the client is enforcing specific Grid response headers
        // along with their respective data.
        if ( params.hasHeaders() )
        {
            return createGridWithParamHeaders( new ArrayList<>( params.getHeaders() ) );
        }
        else
        {
            return createGridWithDefaultHeaders();
        }
    }

    @Override
    protected long addEventData( Grid grid, EventQueryParams params )
    {
        Timer timer = new Timer().start().disablePrint();

        params = queryPlanner.planEnrollmentQuery( params );

        timer.getSplitTime( "Planned event query, got partitions: " + params.getPartitions() );

        long count = 0;

        if ( params.isPaging() )
        {
            count += enrollmentAnalyticsManager.getEnrollmentCount( params );
        }

        enrollmentAnalyticsManager.getEnrollments( params, grid, queryValidator.getMaxLimit() );

        timer.getTime( "Got enrollments " + grid.getHeight() );

        return count;
    }
}
