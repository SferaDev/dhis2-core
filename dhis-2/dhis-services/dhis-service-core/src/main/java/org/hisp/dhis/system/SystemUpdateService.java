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
package org.hisp.dhis.system;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import org.hisp.dhis.message.MessageConversation;
import org.hisp.dhis.message.MessageConversationParams;
import org.hisp.dhis.message.MessageService;
import org.hisp.dhis.message.MessageType;
import org.hisp.dhis.system.util.DhisHttpResponse;
import org.hisp.dhis.system.util.HttpUtils;
import org.hisp.dhis.user.User;
import org.hisp.dhis.user.UserCredentials;
import org.hisp.dhis.user.UserCredentialsStore;
import org.hisp.dhis.user.UserService;
import org.springframework.stereotype.Service;

import com.google.api.client.http.HttpStatusCodes;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vdurmont.semver4j.Semver;

/**
 * @author Morten Svanaes
 */
@Slf4j
@Service
@AllArgsConstructor
public class SystemUpdateService
{
    public static final String DHIS_2_ORG_VERSIONS_JSON = "https://releases.dhis2.org/v1/versions/stable.json";

    public static final String NEW_VERSION_AVAILABLE_MESSAGE_SUBJECT = "System update available";

    private static final int MAX_NOTIFING_RECIPIENTS = 100;

    public static final String FIELD_NAME_VERSION = "version";

    public static final String FIELD_NAME_RELEASE_DATE = "releaseDate";

    public static final String FIELD_NAME_NAME = "name";

    public static final String FIELD_NAME_DOWNLOAD_URL = "downloadUrl";

    public static final String FIELD_NAME_URL = "url";

    @NonNull
    private final UserCredentialsStore userCredentialsStore;

    @NonNull
    private final UserService userService;

    @NonNull
    private final MessageService messageService;

    public static Map<Semver, Map<String, String>> getLatestNewerThanCurrent()
    {
        return getLatestNewerThan( getCurrentVersion() );
    }

    public static Map<Semver, Map<String, String>> getLatestNewerThan( Semver currentVersion )
    {
        JsonObject allVersions = fetchAllVersions();

        List<JsonElement> newerPatchVersions = extractNewerPatchVersions( currentVersion, allVersions );

        // Only pick the top/latest patch version
        if ( !newerPatchVersions.isEmpty() )
        {
            newerPatchVersions = newerPatchVersions.subList( newerPatchVersions.size() - 1, newerPatchVersions.size() );
        }

        return convertJsonToMap( newerPatchVersions );
    }

    private static JsonObject fetchAllVersions()
    {
        try
        {
            DhisHttpResponse httpResponse = HttpUtils.httpGET( DHIS_2_ORG_VERSIONS_JSON,
                false, null, null, null, 0, true );

            int statusCode = httpResponse.getStatusCode();
            if ( statusCode != HttpStatusCodes.STATUS_CODE_OK )
            {
                throw new IllegalStateException(
                    "Failed to fetch the version file, "
                        + "non OK(200) response code. Code was: " + statusCode );
            }

            return new JsonParser().parse( httpResponse.getResponse() ).getAsJsonObject();
        }
        catch ( Exception e )
        {
            log.error( "Failed to fetch list of latest versions.", e );
            throw new IllegalStateException( "Failed to fetch list of latest versions.", e );
        }
    }

    protected static List<JsonElement> extractNewerPatchVersions( Semver currentVersion, JsonObject allVersions )
    {
        List<JsonElement> newerPatchVersions = new ArrayList<>();

        for ( JsonElement versionElement : allVersions.getAsJsonArray( "versions" ) )
        {
            String majorDotMinor = versionElement.getAsJsonObject().getAsJsonPrimitive( FIELD_NAME_NAME ).getAsString();
            Semver semver = new Semver( String.format( "%s.0", majorDotMinor ) );

            // Skip other major/minor versions, we are only interested in the
            // patch versions higher than the currentVersion's patch version
            if ( Objects.equals( currentVersion.getMajor(), semver.getMajor() ) && Objects.equals(
                currentVersion.getMinor(), semver.getMinor() ) )
            {
                int latestPatchVersion = versionElement.getAsJsonObject().getAsJsonPrimitive( "latestPatchVersion" )
                    .getAsInt();

                if ( currentVersion.getPatch() < latestPatchVersion )
                {
                    for ( JsonElement patchElement : versionElement.getAsJsonObject()
                        .getAsJsonArray( "patchVersions" ) )
                    {
                        int patchVersion = patchElement.getAsJsonObject().getAsJsonPrimitive( FIELD_NAME_VERSION )
                            .getAsInt();

                        if ( currentVersion.getPatch() < patchVersion )
                        {
                            log.debug( "Found a newer patch version, "
                                + "adding it the result list; version={}", patchVersion );

                            newerPatchVersions.add( patchElement );
                        }
                    }
                }
            }
        }

        return newerPatchVersions;
    }

    /**
     * Converts a list of json elements representing patch versions, into a map
     * of: SemVer (parsed version) and a map of strings representing the message
     * to send to the admin user(s).
     */
    protected static Map<Semver, Map<String, String>> convertJsonToMap( List<JsonElement> patchVersions )
    {
        Map<Semver, Map<String, String>> versionsAndMessage = new TreeMap<>();

        for ( JsonElement patchVersion : patchVersions )
        {
            JsonObject patchJsonObject = patchVersion.getAsJsonObject();

            String version = patchJsonObject.getAsJsonPrimitive( FIELD_NAME_NAME ).getAsString();

            Map<String, String> message = new HashMap<>();
            message.put( FIELD_NAME_VERSION, patchJsonObject.getAsJsonPrimitive( FIELD_NAME_NAME ).getAsString() );
            message.put( FIELD_NAME_RELEASE_DATE,
                patchJsonObject.getAsJsonPrimitive( FIELD_NAME_RELEASE_DATE ).getAsString() );
            message.put( FIELD_NAME_DOWNLOAD_URL, patchJsonObject.getAsJsonPrimitive( FIELD_NAME_URL ).getAsString() );

            versionsAndMessage.put( new Semver( version ), message );
        }

        return versionsAndMessage;
    }

    public void sendMessageForEachVersion( Map<Semver, Map<String, String>> patchVersions )
    {
        Set<User> recipients = getRecipients();

        for ( Map.Entry<Semver, Map<String, String>> entry : patchVersions.entrySet() )
        {
            Semver version = entry.getKey();
            Map<String, String> message = entry.getValue();

            for ( User recipient : recipients )
            {
                // Check if message has been sent before using
                // version.getValue() as extMessageId
                List<MessageConversation> existingMessages = messageService.getMatchingExtId( version.getValue() );

                if ( existingMessages.isEmpty() )
                {
                    MessageConversationParams params = new MessageConversationParams.Builder()
                        .withRecipients( ImmutableSet.of( recipient ) )
                        .withSubject( NEW_VERSION_AVAILABLE_MESSAGE_SUBJECT )
                        .withText( buildMessageText( message ) )
                        .withMessageType( MessageType.SYSTEM )
                        .withExtMessageId( version.getValue() ).build();

                    messageService.sendMessage( params );
                }
            }
        }
    }

    private Set<User> getRecipients()
    {
        Set<User> recipients = messageService.getSystemUpdateNotificationRecipients();

        // Fallback to fetching all users with ALL authority for our recipient
        // list.
        return !recipients.isEmpty() ? recipients : getUsersWithAllAuthority();
    }

    private Set<User> getUsersWithAllAuthority()
    {
        Set<User> recipients = new HashSet<>();

        List<UserCredentials> userCredentials = userCredentialsStore.getHasAuthority( "ALL" );

        for ( UserCredentials credentials : userCredentials )
        {
            recipients.add( userService.getUserByUsername( credentials.getUsername() ) );
        }

        return recipients;
    }

    private String buildMessageText( Map<String, String> messageValues )
    {
        String version = messageValues.get( FIELD_NAME_VERSION );
        String releaseDate = messageValues.get( FIELD_NAME_RELEASE_DATE );
        String downloadUrl = messageValues.get( FIELD_NAME_DOWNLOAD_URL );

        return String.format(
            "%s %n%n"
                + "Version: %s%n"
                + "Release data: %s%n"
                + "Download URL: %s%n",
            NEW_VERSION_AVAILABLE_MESSAGE_SUBJECT,
            version,
            releaseDate,
            downloadUrl );
    }

    public static Semver getCurrentVersion()
    {
        String buildVersion = DefaultSystemService.loadBuildProperties().getVersion();

        // If we are on a snapshot version, convert '-SNAPSHOT' to
        // '.Int.MAX_VALUE', so we can sort it on top of the list
        if ( buildVersion.contains( "SNAPSHOT" ) )
        {
            log.info( "We are running a SNAPSHOT version, "
                + "handle current patch version as higher than max. "
                + "This effectively disables system update notifications." );

            buildVersion = buildVersion.replace( "-SNAPSHOT",
                "." + Integer.MAX_VALUE );
        }

        return new Semver( buildVersion );
    }
}
