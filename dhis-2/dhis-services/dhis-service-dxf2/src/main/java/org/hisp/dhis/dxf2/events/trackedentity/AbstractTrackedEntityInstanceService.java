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
package org.hisp.dhis.dxf2.events.trackedentity;

import static org.hisp.dhis.system.notification.NotificationLevel.ERROR;
import static org.hisp.dhis.trackedentity.TrackedEntityAttributeService.TEA_VALUE_MAX_LENGTH;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.SessionFactory;
import org.hisp.dhis.audit.payloads.TrackedEntityInstanceAudit;
import org.hisp.dhis.common.AuditType;
import org.hisp.dhis.common.CodeGenerator;
import org.hisp.dhis.common.IdSchemes;
import org.hisp.dhis.common.IdentifiableObjectManager;
import org.hisp.dhis.common.IllegalQueryException;
import org.hisp.dhis.commons.collection.CachingMap;
import org.hisp.dhis.commons.util.DebugUtils;
import org.hisp.dhis.dbms.DbmsManager;
import org.hisp.dhis.dxf2.Constants;
import org.hisp.dhis.dxf2.common.ImportOptions;
import org.hisp.dhis.dxf2.events.RelationshipParams;
import org.hisp.dhis.dxf2.events.TrackedEntityInstanceParams;
import org.hisp.dhis.dxf2.events.aggregates.TrackedEntityInstanceAggregate;
import org.hisp.dhis.dxf2.events.enrollment.Enrollment;
import org.hisp.dhis.dxf2.events.enrollment.EnrollmentService;
import org.hisp.dhis.dxf2.events.importer.context.WorkContext;
import org.hisp.dhis.dxf2.events.importer.context.WorkContextLoader;
import org.hisp.dhis.dxf2.importsummary.ImportConflicts;
import org.hisp.dhis.dxf2.importsummary.ImportStatus;
import org.hisp.dhis.dxf2.importsummary.ImportSummaries;
import org.hisp.dhis.dxf2.importsummary.ImportSummary;
import org.hisp.dhis.dxf2.metadata.feedback.ImportReportMode;
import org.hisp.dhis.fileresource.FileResource;
import org.hisp.dhis.fileresource.FileResourceService;
import org.hisp.dhis.importexport.ImportStrategy;
import org.hisp.dhis.organisationunit.FeatureType;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.program.Program;
import org.hisp.dhis.program.ProgramInstance;
import org.hisp.dhis.program.ProgramInstanceService;
import org.hisp.dhis.query.QueryService;
import org.hisp.dhis.relationship.RelationshipItem;
import org.hisp.dhis.relationship.RelationshipService;
import org.hisp.dhis.reservedvalue.ReservedValueService;
import org.hisp.dhis.scheduling.JobConfiguration;
import org.hisp.dhis.schema.SchemaService;
import org.hisp.dhis.security.Authorities;
import org.hisp.dhis.system.notification.NotificationLevel;
import org.hisp.dhis.system.notification.Notifier;
import org.hisp.dhis.system.util.GeoUtils;
import org.hisp.dhis.trackedentity.TrackedEntityAttribute;
import org.hisp.dhis.trackedentity.TrackedEntityAttributeService;
import org.hisp.dhis.trackedentity.TrackedEntityAttributeStore;
import org.hisp.dhis.trackedentity.TrackedEntityInstanceAuditService;
import org.hisp.dhis.trackedentity.TrackedEntityInstanceQueryParams;
import org.hisp.dhis.trackedentity.TrackedEntityProgramOwner;
import org.hisp.dhis.trackedentity.TrackedEntityType;
import org.hisp.dhis.trackedentity.TrackedEntityTypeService;
import org.hisp.dhis.trackedentity.TrackerAccessManager;
import org.hisp.dhis.trackedentity.TrackerOwnershipManager;
import org.hisp.dhis.trackedentityattributevalue.TrackedEntityAttributeValue;
import org.hisp.dhis.trackedentityattributevalue.TrackedEntityAttributeValueService;
import org.hisp.dhis.user.CurrentUserService;
import org.hisp.dhis.user.User;
import org.hisp.dhis.user.UserCredentials;
import org.hisp.dhis.user.UserService;
import org.hisp.dhis.util.DateUtils;
import org.locationtech.jts.geom.Geometry;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

/**
 * @author Morten Olav Hansen <mortenoh@gmail.com>
 */
@Slf4j
public abstract class AbstractTrackedEntityInstanceService implements TrackedEntityInstanceService
{
    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    protected org.hisp.dhis.trackedentity.TrackedEntityInstanceService teiService;

    protected TrackedEntityAttributeService trackedEntityAttributeService;

    protected TrackedEntityTypeService trackedEntityTypeService;

    protected RelationshipService _relationshipService;

    protected org.hisp.dhis.dxf2.events.relationship.RelationshipService relationshipService;

    protected TrackedEntityAttributeValueService trackedEntityAttributeValueService;

    protected IdentifiableObjectManager manager;

    protected UserService userService;

    protected DbmsManager dbmsManager;

    protected EnrollmentService enrollmentService;

    protected ProgramInstanceService programInstanceService;

    protected TrackedEntityInstanceAuditService trackedEntityInstanceAuditService;

    protected CurrentUserService currentUserService;

    protected SchemaService schemaService;

    protected QueryService queryService;

    protected ReservedValueService reservedValueService;

    protected TrackerAccessManager trackerAccessManager;

    protected FileResourceService fileResourceService;

    protected TrackerOwnershipManager trackerOwnershipAccessManager;

    protected Notifier notifier;

    protected TrackedEntityInstanceAggregate trackedEntityInstanceAggregate;

    protected TrackedEntityAttributeStore trackedEntityAttributeStore;

    protected ObjectMapper jsonMapper;

    protected ObjectMapper xmlMapper;

    protected WorkContextLoader workContextLoader;

    protected SessionFactory sessionFactory;

    private final CachingMap<String, TrackedEntityType> trackedEntityCache = new CachingMap<>();

    // -------------------------------------------------------------------------
    // READ
    // -------------------------------------------------------------------------

    /**
     * Merges the two sets, if the passed condition is true
     *
     * @param set1 a Set
     * @param set2 a second Set
     * @param condition a boolean condition
     * @return if condition is true, a new Set consisting of the first and
     *         second set. If false, the first set
     */
    private Set<TrackedEntityAttribute> mergeIf( Set<TrackedEntityAttribute> set1, Set<TrackedEntityAttribute> set2,
        boolean condition )
    {
        if ( condition )
        {
            set1.addAll( set2 );
        }
        return set1;
    }

    @Override
    @Transactional( readOnly = true )
    public List<TrackedEntityInstance> getTrackedEntityInstances( TrackedEntityInstanceQueryParams queryParams,
        TrackedEntityInstanceParams params, boolean skipAccessValidation, boolean skipSearchScopeValidation )
    {
        if ( queryParams == null )
        {
            return Collections.emptyList();
        }
        List<TrackedEntityInstance> trackedEntityInstances;

        final List<Long> ids = teiService.getTrackedEntityInstanceIds( queryParams, skipAccessValidation,
            skipSearchScopeValidation );

        if ( ids.isEmpty() )
        {
            return Collections.emptyList();
        }

        trackedEntityInstances = this.trackedEntityInstanceAggregate.find( ids, params,
            queryParams );

        addSearchAudit( trackedEntityInstances, queryParams.getUser() );

        return trackedEntityInstances;
    }

    private void addSearchAudit( List<TrackedEntityInstance> trackedEntityInstances, User user )
    {
        if ( trackedEntityInstances.isEmpty() )
        {
            return;
        }
        final String accessedBy = user != null ? user.getUsername() : currentUserService.getCurrentUsername();
        Map<String, TrackedEntityType> tetMap = trackedEntityTypeService.getAllTrackedEntityType().stream()
            .collect( Collectors.toMap( TrackedEntityType::getUid, t -> t ) );

        List<TrackedEntityInstanceAudit> auditable = trackedEntityInstances
            .stream()
            .filter( Objects::nonNull )
            .filter( tei -> tei.getTrackedEntityType() != null )
            .filter( tei -> tetMap.get( tei.getTrackedEntityType() ).isAllowAuditLog() )
            .map(
                tei -> new TrackedEntityInstanceAudit( tei.getTrackedEntityInstance(), accessedBy, AuditType.SEARCH ) )
            .collect( Collectors.toList() );

        if ( !auditable.isEmpty() )
        {
            trackedEntityInstanceAuditService.addTrackedEntityInstanceAudit( auditable );
        }
    }

    @Override
    @Transactional( readOnly = true )
    public int getTrackedEntityInstanceCount( TrackedEntityInstanceQueryParams params, boolean skipAccessValidation,
        boolean skipSearchScopeValidation )
    {
        return teiService.getTrackedEntityInstanceCount( params, skipAccessValidation, skipSearchScopeValidation );
    }

    @Override
    @Transactional( readOnly = true )
    public TrackedEntityInstance getTrackedEntityInstance( String uid )
    {
        return getTrackedEntityInstance( teiService.getTrackedEntityInstance( uid ) );
    }

    @Override
    @Transactional( readOnly = true )
    public TrackedEntityInstance getTrackedEntityInstance( String uid, User user )
    {
        return getTrackedEntityInstance( teiService.getTrackedEntityInstance( uid, user ),
            TrackedEntityInstanceParams.TRUE, user );
    }

    @Override
    @Transactional( readOnly = true )
    public TrackedEntityInstance getTrackedEntityInstance( String uid, TrackedEntityInstanceParams params )
    {
        return getTrackedEntityInstance( teiService.getTrackedEntityInstance( uid ), params );
    }

    @Override
    @Transactional( readOnly = true )
    public TrackedEntityInstance getTrackedEntityInstance(
        org.hisp.dhis.trackedentity.TrackedEntityInstance daoTrackedEntityInstance )
    {
        return getTrackedEntityInstance( daoTrackedEntityInstance, TrackedEntityInstanceParams.TRUE );
    }

    @Override
    @Transactional( readOnly = true )
    public TrackedEntityInstance getTrackedEntityInstance(
        org.hisp.dhis.trackedentity.TrackedEntityInstance daoTrackedEntityInstance, TrackedEntityInstanceParams params )
    {
        return getTrackedEntityInstance( daoTrackedEntityInstance, params, currentUserService.getCurrentUser() );
    }

    @Override
    @Transactional( readOnly = true )
    public TrackedEntityInstance getTrackedEntityInstance(
        org.hisp.dhis.trackedentity.TrackedEntityInstance daoTrackedEntityInstance, TrackedEntityInstanceParams params,
        User user )
    {
        if ( daoTrackedEntityInstance == null )
        {
            return null;
        }

        List<String> errors = trackerAccessManager.canRead( user, daoTrackedEntityInstance );

        if ( !errors.isEmpty() )
        {
            throw new IllegalQueryException( errors.toString() );
        }

        Set<TrackedEntityAttribute> readableAttributes = trackedEntityAttributeService
            .getAllUserReadableTrackedEntityAttributes( user );

        return getTei( daoTrackedEntityInstance, readableAttributes, params, user );
    }

    private org.hisp.dhis.trackedentity.TrackedEntityInstance createDAOTrackedEntityInstance(
        TrackedEntityInstance dtoEntityInstance, WorkContext workContext, ImportSummary importSummary )
    {
        if ( StringUtils.isEmpty( dtoEntityInstance.getOrgUnit() ) )
        {
            importSummary.addConflict( dtoEntityInstance.getTrackedEntityInstance(),
                "No org unit ID in tracked entity instance object" );
            return null;
        }

        org.hisp.dhis.trackedentity.TrackedEntityInstance daoEntityInstance = new org.hisp.dhis.trackedentity.TrackedEntityInstance();

        OrganisationUnit organisationUnit = workContext.getOrganisationUnitMap()
            .get( dtoEntityInstance.getTrackedEntityInstance() );

        if ( organisationUnit == null )
        {
            importSummary.addConflict( dtoEntityInstance.getTrackedEntityInstance(),
                "Invalid org unit ID: " + dtoEntityInstance.getOrgUnit() );
            return null;
        }

        daoEntityInstance.setOrganisationUnit( organisationUnit );

        TrackedEntityType trackedEntityType = getTrackedEntityType( workContext.getImportOptions().getIdSchemes(),
            dtoEntityInstance.getTrackedEntityType() );

        if ( trackedEntityType == null )
        {
            importSummary.addConflict( dtoEntityInstance.getTrackedEntityInstance(),
                "Invalid tracked entity ID: " + dtoEntityInstance.getTrackedEntityType() );
            return null;
        }

        if ( dtoEntityInstance.getGeometry() != null )
        {
            FeatureType featureType = trackedEntityType.getFeatureType();

            if ( featureType.equals( FeatureType.NONE ) || !featureType
                .equals( FeatureType.getTypeFromName( dtoEntityInstance.getGeometry().getGeometryType() ) ) )
            {
                importSummary.addConflict( dtoEntityInstance.getTrackedEntityInstance(),
                    "Geometry does not conform to feature type '" + featureType + "'" );
                importSummary.incrementIgnored();
                return null;
            }
            else
            {
                daoEntityInstance.setGeometry( dtoEntityInstance.getGeometry() );
            }
        }
        else if ( !FeatureType.NONE.equals( dtoEntityInstance.getFeatureType() )
            && dtoEntityInstance.getCoordinates() != null )
        {
            try
            {
                daoEntityInstance.setGeometry( GeoUtils.getGeometryFromCoordinatesAndType(
                    dtoEntityInstance.getFeatureType(), dtoEntityInstance.getCoordinates() ) );
            }
            catch ( IOException e )
            {
                importSummary.addConflict( dtoEntityInstance.getTrackedEntityInstance(),
                    "Could not parse coordinates" );

                importSummary.incrementIgnored();
                return null;
            }
        }
        else
        {
            daoEntityInstance.setGeometry( null );
        }

        daoEntityInstance.setTrackedEntityType( trackedEntityType );
        daoEntityInstance.setUid( CodeGenerator.isValidUid( dtoEntityInstance.getTrackedEntityInstance() )
            ? dtoEntityInstance.getTrackedEntityInstance()
            : CodeGenerator.generateUid() );

        User user = workContext.getImportOptions().getUser();

        String storedBy = !StringUtils.isEmpty( dtoEntityInstance.getStoredBy() ) ? dtoEntityInstance.getStoredBy()
            : (user == null || StringUtils.isEmpty( user.getUsername() )
                ? "system-process"
                : user.getUsername());

        daoEntityInstance.setStoredBy( storedBy );
        daoEntityInstance.setPotentialDuplicate( dtoEntityInstance.isPotentialDuplicate() );

        updateDateFields( dtoEntityInstance, daoEntityInstance );

        return daoEntityInstance;
    }

    // -------------------------------------------------------------------------
    // CREATE, UPDATE or DELETE
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public ImportSummaries mergeOrDeleteTrackedEntityInstances( List<TrackedEntityInstance> trackedEntityInstances,
        ImportOptions importOptions, JobConfiguration jobId )
    {
        notifier.clear( jobId ).notify( jobId, "Importing tracked entities" );

        try
        {
            ImportSummaries importSummaries = new ImportSummaries();

            List<TrackedEntityInstance> create = new ArrayList<>();
            List<TrackedEntityInstance> update = new ArrayList<>();
            List<TrackedEntityInstance> delete = new ArrayList<>();

            // TODO: Check whether relationships are modified during
            // create/update/delete TEI logic. Decide whether logic below can be
            // removed
            List<Relationship> relationships = getRelationships( trackedEntityInstances );

            WorkContext workContext = workContextLoader.loadForTei( updateImportOptions( importOptions ),
                trackedEntityInstances );

            setTrackedEntityListByStrategy( trackedEntityInstances, workContext, create, update, delete );

            importSummaries.addImportSummaries( addTrackedEntityInstances( create, workContext ) );

            ImportSummaries importSummariesWithUpdatedTeis = new ImportSummaries();
            importSummariesWithUpdatedTeis.addImportSummaries( updateTrackedEntityInstances( update, workContext ) );
            importSummariesWithUpdatedTeis.addImportSummaries( deleteTrackedEntityInstances( delete, workContext ) );

            importSummaries.addImportSummaries( importSummariesWithUpdatedTeis );

            finalizeTeis( workContext.getImportOptions().getUser(), importSummariesWithUpdatedTeis );

            // TODO: Created importSummaries don't contain correct href (TEI
            // endpoint instead of relationships is used)
            importSummaries
                .addImportSummaries( relationshipService.processRelationshipList( relationships, importOptions ) );

            if ( ImportReportMode.ERRORS == importOptions.getReportMode() )
            {
                importSummaries.getImportSummaries().removeIf( is -> !is.hasConflicts() );
            }

            notifier.notify( jobId, NotificationLevel.INFO, "Import done", true ).addJobSummary( jobId,
                importSummaries, ImportSummaries.class );

            return importSummaries;
        }
        catch ( RuntimeException ex )
        {
            log.error( DebugUtils.getStackTrace( ex ) );
            notifier.notify( jobId, ERROR, "Process failed: " + ex.getMessage(), true );
            return new ImportSummaries().addImportSummary(
                new ImportSummary( ImportStatus.ERROR, "The import process failed: " + ex.getMessage() ) );
        }
    }

    private void finalizeTeis( User user, ImportSummaries importSummariesWithUpdatedTeis )
    {
        Set<String> updatedTeis = importSummariesWithUpdatedTeis.getImportSummaries().stream()
            .map( ImportSummary::getUpdatedTei )
            .filter( Objects::nonNull ).collect( Collectors.toSet() );

        if ( !updatedTeis.isEmpty() )
        {
            String userName = User.username( user );

            trackedEntityInstanceAuditService.addTrackedEntityInstanceAudit(
                updatedTeis.stream().map( tei -> new TrackedEntityInstanceAudit(
                    tei, userName, AuditType.READ ) ).collect( Collectors.toList() ) );
        }
    }

    private List<Relationship> getRelationships( List<TrackedEntityInstance> trackedEntityInstances )
    {
        List<Relationship> relationships = new ArrayList<>();
        trackedEntityInstances.stream()
            .filter( tei -> !tei.getRelationships().isEmpty() )
            .forEach( tei -> {
                org.hisp.dhis.dxf2.events.trackedentity.RelationshipItem item = new org.hisp.dhis.dxf2.events.trackedentity.RelationshipItem();
                item.setTrackedEntityInstance( tei );

                tei.getRelationships().forEach( rel -> {
                    // Update from if it is empty. Current tei is then "from"
                    if ( rel.getFrom() == null )
                    {
                        rel.setFrom( item );
                    }
                    relationships.add( rel );
                } );
            } );
        return relationships;
    }

    private void setTrackedEntityListByStrategy( List<TrackedEntityInstance> trackedEntityInstances,
        WorkContext workContext, List<TrackedEntityInstance> create, List<TrackedEntityInstance> update,
        List<TrackedEntityInstance> delete )
    {
        ImportOptions importOptions = workContext.getImportOptions();

        if ( importOptions.getImportStrategy().isCreate() )
        {
            create.addAll( trackedEntityInstances );
        }
        else if ( importOptions.getImportStrategy().isCreateAndUpdate() )
        {
            sortCreatesAndUpdates( trackedEntityInstances, workContext, create, update );
        }
        else if ( importOptions.getImportStrategy().isUpdate() )
        {
            update.addAll( trackedEntityInstances );
        }
        else if ( importOptions.getImportStrategy().isDelete() )
        {
            delete.addAll( trackedEntityInstances );
        }
        else if ( importOptions.getImportStrategy().isSync() )
        {
            for ( TrackedEntityInstance trackedEntityInstance : trackedEntityInstances )
            {
                if ( trackedEntityInstance.isDeleted() )
                {
                    delete.add( trackedEntityInstance );
                }
                else
                {
                    sortCreatesAndUpdates( trackedEntityInstance, workContext, create, update );
                }
            }
        }
    }

    private void sortCreatesAndUpdates( List<TrackedEntityInstance> trackedEntityInstances, WorkContext workContext,
        List<TrackedEntityInstance> create, List<TrackedEntityInstance> update )
    {
        Map<String, org.hisp.dhis.trackedentity.TrackedEntityInstance> trackedEntityInstanceMap = workContext
            .getTrackedEntityInstanceMap();

        for ( TrackedEntityInstance trackedEntityInstance : trackedEntityInstances )
        {
            if ( !trackedEntityInstanceMap.containsKey( trackedEntityInstance.getTrackedEntityInstance() ) )
            {
                create.add( trackedEntityInstance );
            }
            else
            {
                update.add( trackedEntityInstance );
            }
        }
    }

    private void sortCreatesAndUpdates( TrackedEntityInstance trackedEntityInstance, WorkContext workContext,
        List<TrackedEntityInstance> create,
        List<TrackedEntityInstance> update )
    {
        Map<String, org.hisp.dhis.trackedentity.TrackedEntityInstance> trackedEntityInstanceMap = workContext
            .getTrackedEntityInstanceMap();

        if ( StringUtils.isEmpty( trackedEntityInstance.getTrackedEntityInstance() ) )
        {
            create.add( trackedEntityInstance );
        }
        else
        {
            if ( !trackedEntityInstanceMap.containsKey( trackedEntityInstance.getTrackedEntityInstance() ) )
            {
                create.add( trackedEntityInstance );
            }
            else if ( !trackedEntityInstanceMap.get( trackedEntityInstance.getTrackedEntityInstance() ).isDeleted() )
            {
                update.add( trackedEntityInstance );
            }
        }
    }

    public ImportSummaries addTrackedEntityInstances( List<TrackedEntityInstance> trackedEntityInstances,
        WorkContext workContext )
    {
        ImportOptions importOptions = workContext.getImportOptions();
        ImportSummaries importSummaries = new ImportSummaries();
        List<Enrollment> enrollments = new ArrayList<>();

        Set<String> uidsWithDeleted = workContext.getTrackedEntityInstanceMap().keySet();

        Map<Boolean, List<TrackedEntityInstance>> partitionedEnrollmentOnExistingUids = trackedEntityInstances
            .stream()
            .collect( Collectors.partitioningBy( e -> !uidsWithDeleted.contains( e.getTrackedEntityInstance() ) ) );

        partitionedEnrollmentOnExistingUids.get( false )
            .forEach( invalidUid -> importSummaries.addImportSummary( new ImportSummary( ImportStatus.ERROR,
                "Tracked Entity Instance " + invalidUid.getTrackedEntityInstance()
                    + " already exists or was deleted earlier" )
                        .setReference( invalidUid.getTrackedEntityInstance() ).incrementIgnored() ) );

        List<List<TrackedEntityInstance>> partitions = Lists.partition( partitionedEnrollmentOnExistingUids.get( true ),
            FLUSH_FREQUENCY );

        for ( List<TrackedEntityInstance> _trackedEntityInstances : partitions )
        {
            reloadUser( importOptions );

            for ( TrackedEntityInstance trackedEntityInstance : _trackedEntityInstances )
            {
                ImportSummary importSummary = addTrackedEntityInstance( trackedEntityInstance, workContext, false );
                importSummaries.addImportSummary( importSummary );

                if ( importSummary.isStatus( ImportStatus.SUCCESS ) )
                {
                    enrollments.addAll( trackedEntityInstance.getEnrollments() );
                }
            }

            clearSession();
        }

        ImportSummaries enrollmentImportSummaries = enrollmentService.mergeOrDeleteEnrollments( enrollments,
            importOptions, null, true );
        linkEnrollmentSummaries( importSummaries, enrollmentImportSummaries, enrollments );

        return importSummaries;
    }

    private ImportSummary addTrackedEntityInstance( TrackedEntityInstance dtoEntityInstance,
        WorkContext workContext, boolean handleEnrollments )
    {
        ImportOptions importOptions = workContext.getImportOptions();
        dtoEntityInstance.trimValuesToNull();

        ImportSummary importSummary = new ImportSummary( dtoEntityInstance.getTrackedEntityInstance() );
        checkTrackedEntityType( dtoEntityInstance, importOptions, importSummary );
        checkAttributes( dtoEntityInstance, workContext, importSummary, false );

        if ( importSummary.hasConflicts() )
        {
            importSummary.setStatus( ImportStatus.ERROR );
            importSummary.getImportCount().incrementIgnored();
            return importSummary;
        }

        org.hisp.dhis.trackedentity.TrackedEntityInstance daoEntityInstance = createDAOTrackedEntityInstance(
            dtoEntityInstance, workContext, importSummary );

        if ( importSummary.hasConflicts() )
        {
            importSummary.setStatus( ImportStatus.ERROR );
            importSummary.getImportCount().incrementIgnored();
            return importSummary;
        }

        if ( daoEntityInstance == null )
        {
            return importSummary;
        }

        List<String> errors = trackerAccessManager.canWrite( importOptions.getUser(), daoEntityInstance );

        if ( !errors.isEmpty() )
        {
            return new ImportSummary( ImportStatus.ERROR, errors.toString() ).incrementIgnored();
        }

        teiService.addTrackedEntityInstance( daoEntityInstance );

        addAttributeValues( dtoEntityInstance, daoEntityInstance, workContext );

        importSummary.setReference( daoEntityInstance.getUid() );
        importSummary.getImportCount().incrementImported();

        if ( handleEnrollments )
        {
            importSummary.setEnrollments( handleEnrollments( dtoEntityInstance, daoEntityInstance, importOptions ) );
        }
        else
        {
            for ( Enrollment enrollment : dtoEntityInstance.getEnrollments() )
            {
                enrollment.setTrackedEntityType( dtoEntityInstance.getTrackedEntityType() );
                enrollment.setTrackedEntityInstance( daoEntityInstance.getUid() );
            }
        }

        return importSummary;
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------

    private ImportSummaries updateTrackedEntityInstances( List<TrackedEntityInstance> trackedEntityInstances,
        WorkContext workContext )
    {
        ImportOptions importOptions = workContext.getImportOptions();

        List<List<TrackedEntityInstance>> partitions = Lists.partition( trackedEntityInstances, FLUSH_FREQUENCY );
        importOptions = updateImportOptions( importOptions );
        ImportSummaries importSummaries = new ImportSummaries();
        List<Enrollment> enrollments = new ArrayList<>();

        for ( List<TrackedEntityInstance> _trackedEntityInstances : partitions )
        {
            reloadUser( importOptions );

            for ( TrackedEntityInstance trackedEntityInstance : _trackedEntityInstances )
            {
                ImportSummary importSummary = updateTrackedEntityInstance( trackedEntityInstance, null, workContext,
                    false, false );
                importSummaries.addImportSummary( importSummary );

                if ( importSummary.isStatus( ImportStatus.SUCCESS ) )
                {
                    enrollments.addAll( trackedEntityInstance.getEnrollments() );
                }
            }

            clearSession();
        }

        ImportSummaries enrollmentImportSummaries = enrollmentService.mergeOrDeleteEnrollments( enrollments,
            importOptions, null, true );
        linkEnrollmentSummaries( importSummaries, enrollmentImportSummaries, enrollments );

        return importSummaries;
    }

    @Override
    @Transactional
    public ImportSummary updateTrackedEntityInstance( TrackedEntityInstance dtoEntityInstance, String programId,
        ImportOptions importOptions, boolean singleUpdate )
    {
        WorkContext workContext = workContextLoader.loadForTei( updateImportOptions( importOptions ),
            Collections.singletonList( dtoEntityInstance ) );

        return updateTrackedEntityInstance( dtoEntityInstance, programId, workContext, singleUpdate, true );
    }

    private ImportSummary updateTrackedEntityInstance( TrackedEntityInstance dtoEntityInstance, String programId,
        WorkContext workContext, boolean singleUpdate, boolean handleEnrollments )
    {
        ImportSummary importSummary = new ImportSummary( dtoEntityInstance.getTrackedEntityInstance() );
        ImportOptions importOptions = workContext.getImportOptions();

        dtoEntityInstance.trimValuesToNull();

        checkAttributes( dtoEntityInstance, workContext, importSummary, true );

        org.hisp.dhis.trackedentity.TrackedEntityInstance daoEntityInstance = workContext.getTrackedEntityInstanceMap()
            .get( dtoEntityInstance.getTrackedEntityInstance() );

        List<String> errors = trackerAccessManager.canWrite( importOptions.getUser(), daoEntityInstance );
        OrganisationUnit organisationUnit = workContext.getOrganisationUnitMap()
            .get( dtoEntityInstance.getTrackedEntityInstance() );

        Program program = getProgram( importOptions.getIdSchemes(), programId );

        TrackedEntityType trackedEntityType = getTrackedEntityType( importOptions.getIdSchemes(),
            dtoEntityInstance.getTrackedEntityType() );

        if ( daoEntityInstance == null || !errors.isEmpty() || organisationUnit == null
            || importSummary.hasConflicts() )
        {
            importSummary.setStatus( ImportStatus.ERROR );
            importSummary.getImportCount().incrementIgnored();

            if ( daoEntityInstance == null )
            {
                String message = "You are trying to add or update tracked entity instance "
                    + dtoEntityInstance.getTrackedEntityInstance() + " that has already been deleted";

                importSummary.addConflict( "TrackedEntityInstance", message );
            }
            else if ( !errors.isEmpty() )
            {
                importSummary.setDescription( errors.toString() );
            }
            else if ( organisationUnit == null )
            {
                String message = "Org unit " + dtoEntityInstance.getOrgUnit() + " does not exist";
                importSummary.addConflict( "OrganisationUnit", message );
            }

            return importSummary;
        }

        daoEntityInstance.setTrackedEntityType( trackedEntityType );
        daoEntityInstance.setOrganisationUnit( organisationUnit );
        daoEntityInstance.setInactive( dtoEntityInstance.isInactive() );
        daoEntityInstance.setPotentialDuplicate( dtoEntityInstance.isPotentialDuplicate() );

        if ( dtoEntityInstance.getGeometry() != null )
        {
            FeatureType featureType = daoEntityInstance.getTrackedEntityType().getFeatureType();
            if ( featureType.equals( FeatureType.NONE ) || !featureType
                .equals( FeatureType.getTypeFromName( dtoEntityInstance.getGeometry().getGeometryType() ) ) )
            {
                importSummary.addConflict( dtoEntityInstance.getTrackedEntityInstance(),
                    "Geometry does not conform to feature type '" + featureType + "'" );

                importSummary.getImportCount().incrementIgnored();
                return importSummary;
            }
            else
            {
                daoEntityInstance.setGeometry( dtoEntityInstance.getGeometry() );
            }
        }
        else if ( !FeatureType.NONE.equals( dtoEntityInstance.getFeatureType() )
            && dtoEntityInstance.getCoordinates() != null )
        {
            try
            {
                daoEntityInstance.setGeometry( GeoUtils.getGeometryFromCoordinatesAndType(
                    dtoEntityInstance.getFeatureType(), dtoEntityInstance.getCoordinates() ) );
            }
            catch ( IOException e )
            {
                importSummary.addConflict( dtoEntityInstance.getTrackedEntityInstance(),
                    "Could not parse coordinates" );

                importSummary.getImportCount().incrementIgnored();
                return importSummary;
            }
        }
        else
        {
            daoEntityInstance.setGeometry( null );
        }

        if ( !importOptions.isIgnoreEmptyCollection() || !dtoEntityInstance.getAttributes().isEmpty() )
        {
            updateAttributeValues( dtoEntityInstance, daoEntityInstance, program, workContext );
        }

        updateDateFields( dtoEntityInstance, daoEntityInstance );

        daoEntityInstance.setLastUpdatedBy( importOptions.getUser() );
        daoEntityInstance.setLastUpdated( new Date() );

        sessionFactory.getCurrentSession().merge( daoEntityInstance );

        importSummary.setReference( daoEntityInstance.getUid() );
        importSummary.getImportCount().incrementUpdated();
        importSummary.setUpdatedTei( daoEntityInstance.getUid() );

        if ( singleUpdate
            && (!importOptions.isIgnoreEmptyCollection() || !dtoEntityInstance.getRelationships().isEmpty()) )
        {
            importSummary
                .setRelationships( handleRelationships( dtoEntityInstance, daoEntityInstance, importOptions ) );
        }

        if ( handleEnrollments )
        {
            importSummary.setEnrollments( handleEnrollments( dtoEntityInstance, daoEntityInstance, importOptions ) );
        }
        else
        {
            for ( Enrollment enrollment : dtoEntityInstance.getEnrollments() )
            {
                enrollment.setTrackedEntityType( dtoEntityInstance.getTrackedEntityType() );
                enrollment.setTrackedEntityInstance( daoEntityInstance.getUid() );
            }
        }

        return importSummary;
    }

    @Override
    @Transactional
    public void updateTrackedEntityInstancesSyncTimestamp( List<String> entityInstanceUIDs, Date lastSynced )
    {
        teiService.updateTrackedEntityInstancesSyncTimestamp( entityInstanceUIDs, lastSynced );
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public ImportSummary deleteTrackedEntityInstance( String uid )
    {
        TrackedEntityInstance trackedEntityInstance = new TrackedEntityInstance();
        trackedEntityInstance.setTrackedEntityInstance( uid );

        return deleteTrackedEntityInstance( uid, null, updateImportOptions( null ) );
    }

    private ImportSummary deleteTrackedEntityInstance( String uid, TrackedEntityInstance dtoEntityInstance,
        ImportOptions importOptions )
    {
        ImportSummary importSummary = new ImportSummary();

        boolean teiExists = teiService.trackedEntityInstanceExists( uid );

        if ( teiExists )
        {
            org.hisp.dhis.trackedentity.TrackedEntityInstance daoEntityInstance = teiService
                .getTrackedEntityInstance( uid );

            if ( dtoEntityInstance != null )
            {
                importSummary.setReference( uid );
                importSummary
                    .setEnrollments( handleEnrollments( dtoEntityInstance, daoEntityInstance, importOptions ) );
            }

            if ( importOptions.getUser() != null )
            {
                isAllowedToDelete( importOptions.getUser(), daoEntityInstance, importSummary );

                if ( importSummary.hasConflicts() )
                {
                    importSummary.setStatus( ImportStatus.ERROR );
                    importSummary.setReference( daoEntityInstance.getUid() );
                    importSummary.incrementIgnored();
                    return importSummary;
                }
            }

            teiService.deleteTrackedEntityInstance( daoEntityInstance );

            importSummary.setStatus( ImportStatus.SUCCESS );
            importSummary.setDescription( "Deletion of tracked entity instance " + uid + " was successful" );
            return importSummary.incrementDeleted();
        }
        else
        {
            importSummary.setStatus( ImportStatus.SUCCESS );
            importSummary.setDescription(
                "Tracked entity instance " + uid + " cannot be deleted as it is not present in the system" );
            return importSummary.incrementIgnored();
        }
    }

    @Override
    @Transactional
    public ImportSummaries deleteTrackedEntityInstances( List<TrackedEntityInstance> trackedEntityInstances,
        WorkContext workContext )
    {
        ImportSummaries importSummaries = new ImportSummaries();

        int counter = 0;

        for ( TrackedEntityInstance tei : trackedEntityInstances )
        {
            importSummaries
                .addImportSummary( deleteTrackedEntityInstance( tei.getTrackedEntityInstance(), tei,
                    workContext.getImportOptions() ) );

            if ( counter % FLUSH_FREQUENCY == 0 )
            {
                clearSession();
            }

            counter++;
        }

        clearSession();

        return importSummaries;
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    private void linkEnrollmentSummaries( ImportSummaries importSummaries, ImportSummaries enrollmentImportSummaries,
        List<Enrollment> enrollments )
    {
        importSummaries.getImportSummaries().forEach( is -> is.setEnrollments( new ImportSummaries() ) );

        Map<String, List<Enrollment>> enrollmentsGroupedByTe = enrollments.stream()
            .filter( en -> !StringUtils.isEmpty( en.getTrackedEntityInstance() ) )
            .collect( Collectors.groupingBy( Enrollment::getTrackedEntityInstance ) );

        Map<String, List<ImportSummary>> summariesGroupedByReference = importSummaries.getImportSummaries().stream()
            .filter( en -> !StringUtils.isEmpty( en.getReference() ) )
            .collect( Collectors.groupingBy( ImportSummary::getReference ) );

        Map<String, List<ImportSummary>> enrollmentSummariesGroupedByReference = enrollmentImportSummaries
            .getImportSummaries().stream()
            .filter( en -> !StringUtils.isEmpty( en.getReference() ) )
            .collect( Collectors.groupingBy( ImportSummary::getReference ) );

        for ( Map.Entry<String, List<Enrollment>> set : enrollmentsGroupedByTe.entrySet() )
        {
            if ( !summariesGroupedByReference.containsKey( set.getKey() ) )
            {
                continue;
            }

            ImportSummary importSummary = summariesGroupedByReference.get( set.getKey() ).get( 0 );
            ImportSummaries enrollmentSummaries = new ImportSummaries();

            for ( Enrollment enrollment : set.getValue() )
            {
                if ( !enrollmentSummariesGroupedByReference.containsKey( enrollment.getEnrollment() ) )
                {
                    continue;
                }

                ImportSummary enrollmentSummary = enrollmentSummariesGroupedByReference
                    .get( enrollment.getEnrollment() ).get( 0 );
                enrollmentSummaries.addImportSummary( enrollmentSummary );
            }

            if ( enrollmentImportSummaries.getImportSummaries().isEmpty() )
            {
                continue;
            }

            importSummary.setEnrollments( enrollmentSummaries );
        }
    }

    private ImportSummaries handleRelationships( TrackedEntityInstance dtoEntityInstance,
        org.hisp.dhis.trackedentity.TrackedEntityInstance daoEntityInstance, ImportOptions importOptions )
    {
        ImportSummaries importSummaries = new ImportSummaries();
        List<Relationship> create = new ArrayList<>();
        List<Relationship> update = new ArrayList<>();

        List<String> relationshipUids = dtoEntityInstance.getRelationships().stream()
            .map( Relationship::getRelationship ).collect( Collectors.toList() );

        List<Relationship> delete = daoEntityInstance.getRelationshipItems().stream()
            .map( RelationshipItem::getRelationship )

            // Remove items we cant write to
            .filter( relationship -> trackerAccessManager.canWrite( importOptions.getUser(), relationship ).isEmpty() )
            .filter( relationship -> isTeiPartOfRelationship( relationship, daoEntityInstance ) )
            .map( org.hisp.dhis.relationship.Relationship::getUid )

            // Remove items we are already referencing
            .filter( ( uid ) -> !relationshipUids.contains( uid ) )

            // Create Relationships for these uids
            .map( uid -> {
                Relationship relationship = new Relationship();
                relationship.setRelationship( uid );
                return relationship;
            } ).collect( Collectors.toList() );

        for ( Relationship relationship : dtoEntityInstance.getRelationships() )
        {
            if ( importOptions.getImportStrategy() == ImportStrategy.SYNC && dtoEntityInstance.isDeleted() )
            {
                delete.add( relationship );
            }
            else if ( relationship.getRelationship() == null )
            {
                org.hisp.dhis.dxf2.events.trackedentity.RelationshipItem relationshipItem = new org.hisp.dhis.dxf2.events.trackedentity.RelationshipItem();

                if ( !isTeiPartOfRelationship( relationship, daoEntityInstance ) )
                {
                    relationshipItem.setTrackedEntityInstance( dtoEntityInstance );
                    relationship.setFrom( relationshipItem );
                }

                create.add( relationship );
            }
            else
            {
                if ( isTeiPartOfRelationship( relationship, daoEntityInstance ) )
                {
                    if ( _relationshipService.relationshipExists( relationship.getRelationship() ) )
                    {
                        update.add( relationship );
                    }
                    else
                    {
                        create.add( relationship );
                    }
                }
                else
                {
                    String message = String.format(
                        "Can't update relationship '%s': TrackedEntityInstance '%s' is not the owner of the relationship",
                        relationship.getRelationship(), daoEntityInstance.getUid() );
                    importSummaries.addImportSummary( new ImportSummary( ImportStatus.ERROR, message )
                        .setReference( relationship.getRelationship() ).incrementIgnored() );
                }
            }
        }

        importSummaries.addImportSummaries( relationshipService.addRelationships( create, importOptions ) );
        importSummaries.addImportSummaries( relationshipService.updateRelationships( update, importOptions ) );
        importSummaries.addImportSummaries( relationshipService.deleteRelationships( delete, importOptions ) );

        return importSummaries;
    }

    private boolean isTeiPartOfRelationship( Relationship relationship,
        org.hisp.dhis.trackedentity.TrackedEntityInstance tei )
    {
        if ( relationship.getFrom() != null && relationship.getFrom().getTrackedEntityInstance() != null
            && relationship.getFrom().getTrackedEntityInstance().getTrackedEntityInstance().equals( tei.getUid() ) )
        {
            return true;
        }
        else if ( !relationship.isBidirectional() )
        {
            return false;
        }
        else
        {
            return relationship.getTo() != null && relationship.getTo().getTrackedEntityInstance() != null
                && relationship.getTo().getTrackedEntityInstance().getTrackedEntityInstance().equals( tei.getUid() );
        }

    }

    private boolean isTeiPartOfRelationship( org.hisp.dhis.relationship.Relationship relationship,
        org.hisp.dhis.trackedentity.TrackedEntityInstance tei )
    {
        if ( relationship.getFrom() != null && relationship.getFrom().getTrackedEntityInstance() != null
            && relationship.getFrom().getTrackedEntityInstance().getUid().equals( tei.getUid() ) )
        {
            return true;
        }
        else if ( !relationship.getRelationshipType().isBidirectional() )
        {
            return false;
        }
        else
        {
            return relationship.getTo() != null && relationship.getTo().getTrackedEntityInstance() != null
                && relationship.getTo().getTrackedEntityInstance().getUid().equals( tei.getUid() );
        }

    }

    private ImportSummaries handleEnrollments( TrackedEntityInstance dtoEntityInstance,
        org.hisp.dhis.trackedentity.TrackedEntityInstance daoEntityInstance, ImportOptions importOptions )
    {
        for ( Enrollment enrollment : dtoEntityInstance.getEnrollments() )
        {
            enrollment.setTrackedEntityType( dtoEntityInstance.getTrackedEntityType() );
            enrollment.setTrackedEntityInstance( daoEntityInstance.getUid() );
        }

        return enrollmentService.mergeOrDeleteEnrollments( dtoEntityInstance.getEnrollments(), importOptions, null,
            false );
    }

    private void updateAttributeValues( TrackedEntityInstance dtoEntityInstance,
        org.hisp.dhis.trackedentity.TrackedEntityInstance daoEntityInstance, Program program, WorkContext workContext )
    {
        Set<String> incomingAttributes = new HashSet<>();
        Map<String, TrackedEntityAttributeValue> teiAttributeToValueMap = getTeiAttributeValueMap(
            trackedEntityAttributeValueService.getTrackedEntityAttributeValues( daoEntityInstance ) );

        for ( Attribute dtoAttribute : dtoEntityInstance.getAttributes() )
        {
            String storedBy = getStoredBy( dtoAttribute, new ImportSummary(),
                User.username( workContext.getImportOptions().getUser(), Constants.UNKNOWN ) );

            TrackedEntityAttributeValue existingAttributeValue = teiAttributeToValueMap
                .get( dtoAttribute.getAttribute() );

            incomingAttributes.add( dtoAttribute.getAttribute() );

            if ( existingAttributeValue != null ) // value exists
            {
                if ( !existingAttributeValue.getValue().equals( dtoAttribute.getValue() ) ) // value
                                                                                            // is
                                                                                            // changed,
                                                                                            // do
                                                                                            // update
                {
                    existingAttributeValue.setStoredBy( storedBy );
                    existingAttributeValue.setValue( dtoAttribute.getValue() );
                    trackedEntityAttributeValueService.updateTrackedEntityAttributeValue( existingAttributeValue,
                        workContext.getImportOptions().getUser() );
                }
            }
            else // value is new, do add
            {
                TrackedEntityAttribute daoEntityAttribute = workContext.getTrackedEntityAttributeMap()
                    .get( dtoAttribute.getAttribute() );

                TrackedEntityAttributeValue newAttributeValue = new TrackedEntityAttributeValue();

                newAttributeValue.setStoredBy( storedBy );
                newAttributeValue.setEntityInstance( daoEntityInstance );
                newAttributeValue.setValue( dtoAttribute.getValue() );
                newAttributeValue.setAttribute( daoEntityAttribute );

                daoEntityInstance.getTrackedEntityAttributeValues().add( newAttributeValue );
                trackedEntityAttributeValueService.addTrackedEntityAttributeValue( newAttributeValue );
            }
        }

        if ( program != null )
        {
            for ( TrackedEntityAttribute att : program.getTrackedEntityAttributes() )
            {
                TrackedEntityAttributeValue attVal = teiAttributeToValueMap.get( att.getUid() );

                if ( attVal != null && !incomingAttributes.contains( att.getUid() ) )
                {
                    trackedEntityAttributeValueService.deleteTrackedEntityAttributeValue( attVal );
                }
            }
        }
    }

    private void addAttributeValues( TrackedEntityInstance dtoEntityInstance,
        org.hisp.dhis.trackedentity.TrackedEntityInstance daoEntityInstance, WorkContext workContext )
    {
        for ( Attribute dtoAttribute : dtoEntityInstance.getAttributes() )
        {
            TrackedEntityAttribute daoEntityAttribute = workContext.getTrackedEntityAttributeMap()
                .get( dtoAttribute.getAttribute() );

            if ( daoEntityAttribute != null )
            {
                TrackedEntityAttributeValue daoAttributeValue = new TrackedEntityAttributeValue();
                daoAttributeValue.setEntityInstance( daoEntityInstance );
                daoAttributeValue.setValue( dtoAttribute.getValue() );
                daoAttributeValue.setAttribute( daoEntityAttribute );

                daoEntityInstance.addAttributeValue( daoAttributeValue );

                String storedBy = getStoredBy( dtoAttribute, new ImportSummary(),
                    User.username( workContext.getImportOptions().getUser(), Constants.UNKNOWN ) );
                daoAttributeValue.setStoredBy( storedBy );

                trackedEntityAttributeValueService.addTrackedEntityAttributeValue( daoAttributeValue );
            }
        }
    }

    private Program getProgram( IdSchemes idSchemes, String id )
    {
        if ( id == null )
        {
            return null;
        }

        return manager.getObject( Program.class, idSchemes.getProgramIdScheme(), id );
    }

    private TrackedEntityType getTrackedEntityType( IdSchemes idSchemes, String id )
    {
        return trackedEntityCache.get( id,
            () -> manager.getObject( TrackedEntityType.class, idSchemes.getTrackedEntityIdScheme(), id ) );
    }

    private Map<String, TrackedEntityAttributeValue> getTeiAttributeValueMap(
        List<TrackedEntityAttributeValue> teiAttributeValues )
    {
        return teiAttributeValues.stream()
            .collect( Collectors.toMap( tav -> tav.getAttribute().getUid(), tav -> tav ) );
    }

    // --------------------------------------------------------------------------
    // VALIDATION
    // --------------------------------------------------------------------------

    private void validateAttributeType( Attribute attribute, WorkContext workContext,
        ImportConflicts importConflicts )
    {
        TrackedEntityAttribute daoTrackedEntityAttribute = workContext.getTrackedEntityAttributeMap()
            .get( attribute.getAttribute() );

        if ( daoTrackedEntityAttribute == null )
        {
            importConflicts.addConflict( "Attribute.attribute", "Does not point to a valid attribute" );
        }

        String errorMessage = trackedEntityAttributeService.validateValueType( daoTrackedEntityAttribute,
            attribute.getValue() );

        if ( errorMessage != null )
        {
            importConflicts.addConflict( "Attribute.value", errorMessage );
        }
    }

    private void checkAttributeUniquenessWithinScope( org.hisp.dhis.trackedentity.TrackedEntityInstance entityInstance,
        TrackedEntityAttribute trackedEntityAttribute, String value, OrganisationUnit organisationUnit,
        ImportConflicts importConflicts )
    {
        String errorMessage = trackedEntityAttributeService
            .validateAttributeUniquenessWithinScope( trackedEntityAttribute, value, entityInstance, organisationUnit );

        if ( errorMessage != null )
        {
            importConflicts.addConflict( "Attribute.value", errorMessage );
        }
    }

    private void checkAttributes( TrackedEntityInstance dtoEntityInstance, WorkContext workContext,
        ImportConflicts importConflicts, boolean teiExistsInDatabase )
    {
        if ( dtoEntityInstance.getAttributes().isEmpty() )
        {
            return;
        }

        List<String> fileValues = new ArrayList<>();
        org.hisp.dhis.trackedentity.TrackedEntityInstance daoEntityInstance = null;

        if ( teiExistsInDatabase )
        {
            daoEntityInstance = workContext.getTrackedEntityInstanceMap()
                .get( dtoEntityInstance.getTrackedEntityInstance() );

            if ( daoEntityInstance == null )
            {
                return;
            }

            daoEntityInstance.getTrackedEntityAttributeValues().stream()
                .filter( attrVal -> attrVal.getAttribute().getValueType().isFile() )
                .forEach( attrVal -> fileValues.add( attrVal.getValue() ) );
        }

        for ( Attribute attribute : dtoEntityInstance.getAttributes() )
        {
            if ( StringUtils.isNotEmpty( attribute.getValue() ) )
            {
                // Cache was populated in prepareCaches, so I should hit the
                // cache
                TrackedEntityAttribute daoEntityAttribute = workContext.getTrackedEntityAttributeMap()
                    .get( attribute.getAttribute() );

                if ( daoEntityAttribute == null )
                {
                    importConflicts.addConflict( "Attribute.attribute",
                        "Invalid attribute " + attribute.getAttribute() );
                    continue;
                }

                if ( attribute.getValue() != null && attribute.getValue().length() > TEA_VALUE_MAX_LENGTH )
                {
                    // We shorten the value to first 25 characters, since we
                    // dont want to post a 1200+ string back.
                    importConflicts.addConflict( "Attribute.value",
                        String.format( "Value exceeds the character limit of %s characters: '%s...'",
                            TEA_VALUE_MAX_LENGTH, attribute.getValue().substring( 0, 25 ) ) );
                }

                if ( daoEntityAttribute.isUnique() )
                {
                    OrganisationUnit organisationUnit = workContext.getOrganisationUnitMap()
                        .get( dtoEntityInstance.getTrackedEntityType() );
                    checkAttributeUniquenessWithinScope( daoEntityInstance, daoEntityAttribute, attribute.getValue(),
                        organisationUnit, importConflicts );
                }

                validateAttributeType( attribute, workContext, importConflicts );

                if ( daoEntityAttribute.getValueType().isFile() && checkAssigned( attribute, fileValues ) )
                {
                    importConflicts.addConflict( "Attribute.value",
                        String.format( "File resource with uid '%s' has already been assigned to a different object",
                            attribute.getValue() ) );
                }
            }
        }
    }

    private void checkTrackedEntityType( TrackedEntityInstance entityInstance,
        ImportOptions importOptions, ImportConflicts importConflicts )
    {
        if ( entityInstance.getTrackedEntityType() == null )
        {
            importConflicts.addConflict( "TrackedEntityInstance.trackedEntityType",
                "Missing required property trackedEntityType" );
            return;
        }

        TrackedEntityType daoTrackedEntityType = getTrackedEntityType( importOptions.getIdSchemes(),
            entityInstance.getTrackedEntityType() );

        if ( daoTrackedEntityType == null )
        {
            importConflicts.addConflict( "TrackedEntityInstance.trackedEntityType", "Invalid trackedEntityType " +
                entityInstance.getTrackedEntityType() );
        }
    }

    private void clearSession()
    {
        trackedEntityCache.clear();
        dbmsManager.flushSession();
    }

    private void updateDateFields( TrackedEntityInstance dtoEntityInstance,
        org.hisp.dhis.trackedentity.TrackedEntityInstance daoEntityInstance )
    {
        Date createdAtClient = DateUtils.parseDate( dtoEntityInstance.getCreatedAtClient() );

        if ( createdAtClient != null )
        {
            daoEntityInstance.setCreatedAtClient( createdAtClient );
        }

        String lastUpdatedAtClient = dtoEntityInstance.getLastUpdatedAtClient();

        if ( lastUpdatedAtClient != null )
        {
            daoEntityInstance.setLastUpdatedAtClient( DateUtils.parseDate( lastUpdatedAtClient ) );
        }

        daoEntityInstance.setAutoFields();
    }

    private String getStoredBy( Attribute attributeValue, ImportConflicts importConflicts, String fallbackUsername )
    {
        String storedBy = attributeValue.getStoredBy();

        if ( StringUtils.isEmpty( storedBy ) )
        {
            return fallbackUsername;
        }
        else if ( storedBy.length() > UserCredentials.USERNAME_MAX_LENGTH )
        {
            if ( importConflicts != null )
            {
                importConflicts.addConflict( "stored by", storedBy + " is more than "
                    + UserCredentials.USERNAME_MAX_LENGTH + " characters, using current username instead" );
            }

            return fallbackUsername;
        }

        return storedBy;
    }

    private boolean checkAssigned( Attribute attribute, List<String> oldFileValues )
    {
        FileResource fileResource = fileResourceService.getFileResource( attribute.getValue() );
        return fileResource != null && fileResource.isAssigned() && !oldFileValues.contains( attribute.getValue() );
    }

    protected ImportOptions updateImportOptions( ImportOptions importOptions )
    {
        if ( importOptions == null )
        {
            importOptions = new ImportOptions();
            importOptions.setSkipLastUpdated( true );
        }

        if ( importOptions.getUser() == null )
        {
            importOptions.setUser( currentUserService.getCurrentUser() );
        }

        return importOptions;
    }

    protected void reloadUser( ImportOptions importOptions )
    {
        if ( importOptions == null || importOptions.getUser() == null )
        {
            return;
        }

        importOptions.setUser( userService.getUser( importOptions.getUser().getUid() ) );
    }

    private void isAllowedToDelete( User user, org.hisp.dhis.trackedentity.TrackedEntityInstance tei,
        ImportConflicts importConflicts )
    {
        Set<ProgramInstance> programInstances = tei.getProgramInstances().stream().filter( pi -> !pi.isDeleted() )
            .collect( Collectors.toSet() );

        if ( !programInstances.isEmpty() && !user.isAuthorized( Authorities.F_TEI_CASCADE_DELETE.getAuthority() ) )
        {
            importConflicts.addConflict( tei.getUid(),
                "Tracked entity instance " + tei.getUid()
                    + " cannot be deleted as it has associated enrollments and user does not have authority "
                    + Authorities.F_TEI_CASCADE_DELETE.getAuthority() );
        }

        List<String> errors = trackerAccessManager.canWrite( user, tei );

        if ( !errors.isEmpty() )
        {
            errors.forEach( error -> importConflicts.addConflict( tei.getUid(), error ) );
        }
    }

    private TrackedEntityInstance getTei( org.hisp.dhis.trackedentity.TrackedEntityInstance daoTrackedEntityInstance,
        Set<TrackedEntityAttribute> readableAttributes, TrackedEntityInstanceParams params, User user )
    {
        if ( daoTrackedEntityInstance == null )
        {
            return null;
        }

        TrackedEntityInstance trackedEntityInstance = new TrackedEntityInstance();
        trackedEntityInstance.setTrackedEntityInstance( daoTrackedEntityInstance.getUid() );
        trackedEntityInstance.setOrgUnit( daoTrackedEntityInstance.getOrganisationUnit().getUid() );
        trackedEntityInstance.setTrackedEntityType( daoTrackedEntityInstance.getTrackedEntityType().getUid() );
        trackedEntityInstance.setCreated( DateUtils.getIso8601NoTz( daoTrackedEntityInstance.getCreated() ) );
        trackedEntityInstance
            .setCreatedAtClient( DateUtils.getIso8601NoTz( daoTrackedEntityInstance.getCreatedAtClient() ) );
        trackedEntityInstance.setLastUpdated( DateUtils.getIso8601NoTz( daoTrackedEntityInstance.getLastUpdated() ) );
        trackedEntityInstance
            .setLastUpdatedAtClient( DateUtils.getIso8601NoTz( daoTrackedEntityInstance.getLastUpdatedAtClient() ) );
        trackedEntityInstance
            .setInactive( Optional.ofNullable( daoTrackedEntityInstance.isInactive() ).orElse( false ) );
        trackedEntityInstance.setGeometry( daoTrackedEntityInstance.getGeometry() );
        trackedEntityInstance.setDeleted( daoTrackedEntityInstance.isDeleted() );
        trackedEntityInstance.setPotentialDuplicate( daoTrackedEntityInstance.isPotentialDuplicate() );
        trackedEntityInstance.setStoredBy( daoTrackedEntityInstance.getStoredBy() );
        trackedEntityInstance.setCreatedByUserInfo( daoTrackedEntityInstance.getCreatedByUserInfo() );
        trackedEntityInstance.setLastUpdatedByUserInfo( daoTrackedEntityInstance.getLastUpdatedByUserInfo() );

        if ( daoTrackedEntityInstance.getGeometry() != null )
        {
            Geometry geometry = daoTrackedEntityInstance.getGeometry();
            FeatureType featureType = FeatureType.getTypeFromName( geometry.getGeometryType() );
            trackedEntityInstance.setFeatureType( featureType );
            trackedEntityInstance.setCoordinates( GeoUtils.getCoordinatesFromGeometry( geometry ) );
        }

        if ( params.isIncludeRelationships() )
        {
            for ( RelationshipItem relationshipItem : daoTrackedEntityInstance.getRelationshipItems() )
            {
                org.hisp.dhis.relationship.Relationship daoRelationship = relationshipItem.getRelationship();

                if ( trackerAccessManager.canRead( user, daoRelationship ).isEmpty() )
                {
                    Relationship relationship = relationshipService.getRelationship( relationshipItem.getRelationship(),
                        RelationshipParams.FALSE, user );

                    trackedEntityInstance.getRelationships().add( relationship );
                }
            }
        }

        if ( params.isIncludeEnrollments() )
        {
            for ( ProgramInstance programInstance : daoTrackedEntityInstance.getProgramInstances() )
            {
                if ( trackerAccessManager.canRead( user, programInstance, false ).isEmpty()
                    && (params.isIncludeDeleted() || !programInstance.isDeleted()) )
                {
                    trackedEntityInstance.getEnrollments()
                        .add( enrollmentService.getEnrollment( user, programInstance, params, true ) );
                }
            }
        }

        if ( params.isIncludeProgramOwners() )
        {
            for ( TrackedEntityProgramOwner programOwner : daoTrackedEntityInstance.getProgramOwners() )
            {
                trackedEntityInstance.getProgramOwners().add( new ProgramOwner( programOwner ) );
            }

        }

        Set<TrackedEntityAttribute> readableAttributesCopy = filterOutSkipSyncAttributesIfApplies( params,
            trackedEntityInstance, readableAttributes );

        for ( TrackedEntityAttributeValue attributeValue : daoTrackedEntityInstance.getTrackedEntityAttributeValues() )
        {
            if ( readableAttributesCopy.contains( attributeValue.getAttribute() ) )
            {
                Attribute attribute = new Attribute();

                attribute.setCreated( DateUtils.getIso8601NoTz( attributeValue.getCreated() ) );
                attribute.setLastUpdated( DateUtils.getIso8601NoTz( attributeValue.getLastUpdated() ) );
                attribute.setDisplayName( attributeValue.getAttribute().getDisplayName() );
                attribute.setAttribute( attributeValue.getAttribute().getUid() );
                attribute.setValueType( attributeValue.getAttribute().getValueType() );
                attribute.setCode( attributeValue.getAttribute().getCode() );
                attribute.setValue( attributeValue.getValue() );
                attribute.setStoredBy( attributeValue.getStoredBy() );
                attribute.setSkipSynchronization( attributeValue.getAttribute().getSkipSynchronization() );

                trackedEntityInstance.getAttributes().add( attribute );
            }
        }

        return trackedEntityInstance;
    }

    private Set<TrackedEntityAttribute> filterOutSkipSyncAttributesIfApplies( TrackedEntityInstanceParams params,
        TrackedEntityInstance trackedEntityInstance, Set<TrackedEntityAttribute> readableAttributes )
    {
        Set<TrackedEntityAttribute> readableAttributesCopy;

        if ( params.isDataSynchronizationQuery() )
        {
            List<String> programs = trackedEntityInstance.getEnrollments().stream().map( Enrollment::getProgram )
                .collect( Collectors.toList() );

            readableAttributesCopy = readableAttributes.stream().filter( att -> !att.getSkipSynchronization() )
                .collect( Collectors.toSet() );

            IdSchemes idSchemes = new IdSchemes();
            for ( String programUid : programs )
            {
                Program program = getProgram( idSchemes, programUid );
                if ( program != null )
                {
                    readableAttributesCopy.addAll( program.getTrackedEntityAttributes().stream()
                        .filter( att -> !att.getSkipSynchronization() ).collect( Collectors.toSet() ) );
                }
            }
        }
        else
        {
            readableAttributesCopy = new HashSet<>( readableAttributes );
        }

        return readableAttributesCopy;
    }
}
