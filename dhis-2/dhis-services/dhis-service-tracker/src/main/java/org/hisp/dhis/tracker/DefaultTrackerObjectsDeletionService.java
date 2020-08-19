package org.hisp.dhis.tracker;

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

import org.hisp.dhis.program.ProgramInstance;
import org.hisp.dhis.program.ProgramInstanceService;
import org.hisp.dhis.program.ProgramStageInstance;
import org.hisp.dhis.program.ProgramStageInstanceService;
import org.hisp.dhis.relationship.RelationshipService;
import org.hisp.dhis.security.Authorities;
import org.hisp.dhis.system.notification.Notifier;
import org.hisp.dhis.trackedentity.TrackedEntityInstanceService;
import org.hisp.dhis.trackedentity.TrackerAccessManager;
import org.hisp.dhis.tracker.bundle.TrackerBundle;
import org.hisp.dhis.tracker.domain.Enrollment;
import org.hisp.dhis.tracker.report.TrackerErrorCode;
import org.hisp.dhis.tracker.report.TrackerErrorReport;
import org.hisp.dhis.tracker.report.TrackerObjectReport;
import org.hisp.dhis.tracker.report.TrackerTypeReport;
import org.hisp.dhis.user.CurrentUserService;
import org.hisp.dhis.user.User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Zubair Asghar
 */

@Service
public class DefaultTrackerObjectsDeletionService
    implements TrackerObjectDeletionService
{
    private final ProgramInstanceService programInstanceService;

    private final TrackedEntityInstanceService entityInstanceService;

    private final ProgramStageInstanceService stageInstanceService;

    private final RelationshipService relationshipService;

    private final CurrentUserService currentUserService;

    private final TrackerAccessManager trackerAccessManager;

    private final Notifier notifier;

    public DefaultTrackerObjectsDeletionService( ProgramInstanceService programInstanceService,
         TrackedEntityInstanceService entityInstanceService,
         ProgramStageInstanceService stageInstanceService,
         RelationshipService relationshipService,
         CurrentUserService currentUserService,
         TrackerAccessManager trackerAccessManager,
         Notifier notifier )
    {
        this.programInstanceService = programInstanceService;
        this.entityInstanceService = entityInstanceService;
        this.stageInstanceService = stageInstanceService;
        this.relationshipService = relationshipService;
        this.currentUserService = currentUserService;
        this.trackerAccessManager = trackerAccessManager;
        this.notifier = notifier;
    }

    @Override
    public TrackerTypeReport deleteEnrollment( TrackerBundle bundle, TrackerTypeReport typeReport )
    {
        List<Enrollment> enrollments = bundle.getEnrollments();

        for ( int idx = 0; idx < enrollments.size(); idx++ )
        {
            String uid = enrollments.get( idx ).getUid();

            boolean existsEnrollment = programInstanceService.programInstanceExists( uid );

            TrackerObjectReport trackerObjectReport = new TrackerObjectReport( TrackerType.ENROLLMENT );

            if ( existsEnrollment )
            {
                ProgramInstance programInstance = programInstanceService.getProgramInstance( uid );

                if ( bundle.getUser() != null )
                {
                    List<TrackerErrorReport> trackerErrorReports = isAllowedToDelete( idx, bundle.getUser(), programInstance, bundle );

                    if ( !trackerErrorReports.isEmpty() )
                    {
                        trackerObjectReport.getErrorReports().addAll( trackerErrorReports );
                        trackerObjectReport.setIndex( idx );
                        trackerObjectReport.setUid( uid );

                        typeReport.addObjectReport( trackerObjectReport );
                        typeReport.getStats().incIgnored();
                        return typeReport;
                    }
                }

                programInstanceService.deleteProgramInstance( programInstance );
                entityInstanceService.updateTrackedEntityInstance( programInstance.getEntityInstance() );

                typeReport.getStats().incDeleted();
            }
            else
            {
                trackerObjectReport.getErrorReports()
                        .add( TrackerErrorReport.builder()
                        .listIndex( idx )
                        .errorCode( TrackerErrorCode.E1081 )
                        .addArg( uid )
                        .build( bundle ) );

                trackerObjectReport.setIndex( idx );
                trackerObjectReport.setUid( uid );

                typeReport.addObjectReport( trackerObjectReport );
                typeReport.getStats().incIgnored();
            }
        }

        return typeReport;
    }

    private List<TrackerErrorReport> isAllowedToDelete( int index, User user, ProgramInstance pi, TrackerBundle bundle )
    {
        List<TrackerErrorReport> errorReports = new ArrayList<>();

        Set<ProgramStageInstance> notDeletedProgramStageInstances = pi.getProgramStageInstances().stream()
                .filter( psi -> !psi.isDeleted() )
                .collect( Collectors.toSet() );

        if ( !notDeletedProgramStageInstances.isEmpty() && !user.isAuthorized( Authorities.F_ENROLLMENT_CASCADE_DELETE.getAuthority() ) )
        {
            TrackerErrorReport trackerErrorReport = TrackerErrorReport.builder()
                .mainKlass( ProgramInstance.class )
                .listIndex( index )
                .errorCode( TrackerErrorCode.E1091 )
                .addArg( bundle.getUser().getSurname() ).addArg( pi.getUid() )
                .build( bundle );

            errorReports.add( trackerErrorReport );
        }

        List<String> errors = trackerAccessManager.canDelete( user, pi, false );

        if ( !errors.isEmpty() )
        {
            errors.forEach( error -> errorReports.add( TrackerErrorReport.builder()
                .mainKlass( ProgramInstance.class )
                .errorMessage( error )
                .build( bundle ) ) );
        }

        return errorReports;
    }
}
