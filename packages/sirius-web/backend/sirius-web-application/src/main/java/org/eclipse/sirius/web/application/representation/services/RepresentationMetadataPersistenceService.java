/*******************************************************************************
 * Copyright (c) 2024, 2025 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.sirius.web.application.representation.services;

import java.util.Objects;

import org.eclipse.sirius.components.collaborative.api.IRepresentationMetadataPersistenceService;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.events.ICause;
import org.eclipse.sirius.web.application.UUIDParser;
import org.eclipse.sirius.web.domain.boundedcontexts.projectsemanticdata.ProjectSemanticData;
import org.eclipse.sirius.web.domain.boundedcontexts.projectsemanticdata.services.api.IProjectSemanticDataSearchService;
import org.eclipse.sirius.web.domain.boundedcontexts.representationdata.RepresentationIconURL;
import org.eclipse.sirius.web.domain.boundedcontexts.representationdata.RepresentationMetadata;
import org.eclipse.sirius.web.domain.boundedcontexts.representationdata.services.api.IRepresentationMetadataCreationService;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Used to persist representation metadata.
 *
 * @author gcoutable
 */
@Service
public class RepresentationMetadataPersistenceService implements IRepresentationMetadataPersistenceService {

    private final IRepresentationMetadataCreationService representationMetadataCreationService;

    private final IProjectSemanticDataSearchService projectSemanticDataSearchService;


    public RepresentationMetadataPersistenceService(IRepresentationMetadataCreationService representationMetadataCreationService, IProjectSemanticDataSearchService projectSemanticDataSearchService) {
        this.representationMetadataCreationService = Objects.requireNonNull(representationMetadataCreationService);
        this.projectSemanticDataSearchService = Objects.requireNonNull(projectSemanticDataSearchService);
    }

    @Override
    @Transactional
    public void save(ICause cause, IEditingContext editingContext, org.eclipse.sirius.components.core.RepresentationMetadata representationMetadata, String targetObjectId) {
        var optionalRepresentationId = new UUIDParser().parse(representationMetadata.id());
        var optionalProjectId = new UUIDParser().parse(editingContext.getId())
                .flatMap(semanticDataId -> this.projectSemanticDataSearchService.findBySemanticDataId(AggregateReference.to(semanticDataId)))
                .map(ProjectSemanticData::getProject)
                .map(AggregateReference::getId);

        if (optionalRepresentationId.isPresent() && optionalProjectId.isPresent()) {
            var representationId = optionalRepresentationId.get();

            var boundedRepresentationMetadata = RepresentationMetadata.newRepresentationMetadata(representationId)
                    .project(AggregateReference.to(optionalProjectId.get()))
                    .label(representationMetadata.label())
                    .kind(representationMetadata.kind())
                    .descriptionId(representationMetadata.descriptionId())
                    .targetObjectId(targetObjectId)
                    .iconURLs(representationMetadata.iconURLs().stream()
                            .map(RepresentationIconURL::new)
                            .toList())
                    .documentation("")
                    .build(cause);

            this.representationMetadataCreationService.create(boundedRepresentationMetadata);
        }
    }
}
