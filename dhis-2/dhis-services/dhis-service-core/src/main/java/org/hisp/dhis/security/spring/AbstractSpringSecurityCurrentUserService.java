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
package org.hisp.dhis.security.spring;

import java.util.Set;
import java.util.stream.Collectors;

import org.hisp.dhis.security.oidc.DhisOidcUser;
import org.hisp.dhis.user.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * @author Torgeir Lorange Ostby
 */
public abstract class AbstractSpringSecurityCurrentUserService implements CurrentUserService
{
    @Override
    public String getCurrentUsername()
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if ( authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal() == null )
        {
            return null;
        }

        Object principal = authentication.getPrincipal();

        // Principal being a string implies anonymous authentication
        // This is the state before the user is authenticated.
        if ( principal instanceof String )
        {
            if ( !"anonymousUser".equals( principal ) )
            {
                return null;
            }

            return (String) principal;
        }

        if ( principal instanceof UserDetails )
        {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return userDetails.getUsername();
        }

        if ( principal instanceof DhisOidcUser )
        {
            DhisOidcUser dhisOidcUser = (DhisOidcUser) authentication.getPrincipal();
            return dhisOidcUser.getUserCredentials().getUsername();
        }

        throw new RuntimeException( "Authentication principal is not supported; principal:" + principal );
    }

    public Set<String> getCurrentUserAuthorities()
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        Object principal = authentication.getPrincipal();

        if ( principal instanceof UserDetails )
        {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return userDetails.getAuthorities().stream().map( GrantedAuthority::getAuthority )
                .collect( Collectors.toSet() );
        }

        if ( principal instanceof DhisOidcUser )
        {
            DhisOidcUser dhisOidcUser = (DhisOidcUser) authentication.getPrincipal();
            return dhisOidcUser.getAuthorities().stream().map( GrantedAuthority::getAuthority )
                .collect( Collectors.toSet() );
        }

        throw new RuntimeException( "Authentication principal is not supported; principal:" + principal );
    }

    public abstract Long getUserId( String username );
}
