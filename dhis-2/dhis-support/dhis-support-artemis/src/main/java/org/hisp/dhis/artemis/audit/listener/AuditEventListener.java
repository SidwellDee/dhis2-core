package org.hisp.dhis.artemis.audit.listener;

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

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hisp.dhis.artemis.audit.Audit;
import org.hisp.dhis.artemis.audit.AuditManager;
import org.hisp.dhis.artemis.audit.AuditableEntity;
import org.hisp.dhis.audit.AuditScope;
import org.hisp.dhis.audit.payloads.AuditPayload;
import org.hisp.dhis.audit.payloads.TrackedEntityAuditPayload;
import org.hisp.dhis.common.AuditType;
import org.hisp.dhis.trackedentity.TrackedEntityInstance;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Audit listener for Objects with complex logic that can't be handled by {@link AbstractHibernateListener}
 * It will process all events published with {@link Audit} payload
 * Use {@link TransactionalEventListener} here so Audit will only be sent after the Transaction committed successfully
 */
@Component
@Conditional( value = AuditEnabledCondition.class )
public class AuditEventListener
{
    private final AuditManager auditManager;

    private final SessionFactory sessionFactory;

    public AuditEventListener( AuditManager auditManager, SessionFactory sessionFactory )
    {
        this.auditManager = auditManager;
        this.sessionFactory = sessionFactory;
    }

    @TransactionalEventListener
    public void processAuditEvent( Audit audit )
    {
        auditManager.send( audit );
    }
}
