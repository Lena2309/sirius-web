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
package org.eclipse.sirius.web.domain.boundedcontexts.project.repositories;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.sirius.web.domain.boundedcontexts.project.Project;
import org.eclipse.sirius.web.domain.boundedcontexts.project.repositories.api.IProjectSearchRepositoryDelegate;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.data.relational.core.query.Query;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Used to execute the queries for the project search repository.
 *
 * @author sbegaudeau
 */
@Service
public class ProjectSearchRepositoryDelegate implements IProjectSearchRepositoryDelegate {

    private static final String ID = "id";

    private static final String CURSOR_PROJECT_ID = "cursorProjectId";

    private static final String LIMIT = "limit";

    private static final String FIND_ALL_BEFORE = """
            SELECT p.*
            FROM project p
            WHERE
                (cast(:cursorProjectId as uuid) IS NULL
                    OR (p.id <> :cursorProjectId
                        AND p.created_on <= (
                        SELECT created_on
                        FROM project
                        WHERE project.id = :cursorProjectId))
            )
            ORDER BY p.created_on desc, p.name
            LIMIT :limit;
            """;

    private static final String FIND_ALL_AFTER = """
            SELECT p.*
            FROM project p
            WHERE
                (cast(:cursorProjectId as uuid) IS NULL
                    OR (p.id <> :cursorProjectId
                        AND p.created_on >= (
                        SELECT created_on
                        FROM project
                        WHERE project.id = :cursorProjectId))
            )
            ORDER BY p.created_on asc, p.name
            LIMIT :limit;
            """;

    private final JdbcAggregateOperations jdbcAggregateOperations;

    private final JdbcClient jdbcClient;

    public ProjectSearchRepositoryDelegate(JdbcAggregateOperations jdbcAggregateOperations, JdbcClient jdbcClient) {
        this.jdbcAggregateOperations = Objects.requireNonNull(jdbcAggregateOperations);
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
    }

    @Override
    public boolean existsById(String projectId) {
        Query query = query(where(ID).is(projectId));
        return this.jdbcAggregateOperations.exists(query, Project.class);
    }

    @Override
    public Optional<Project> findById(String projectId) {
        Query query = query(where(ID).is(projectId));
        return this.jdbcAggregateOperations.findOne(query, Project.class);
    }

    @Override
    public List<Project> findAllBefore(String cursorProjectId, int limit) {
        List<Project> projectsBefore = null;
        if (limit > 0) {
            var projects = this.getAllProjectsQuery(FIND_ALL_BEFORE, cursorProjectId, limit + 1);
            projectsBefore = projects.subList(0, Math.min(projects.size(), limit));
        }
        return projectsBefore;
    }

    @Override
    public List<Project> findAllAfter(String cursorProjectId, int limit) {
        List<Project> projectsAfter = null;
        if (limit > 0) {
            var projects = this.getAllProjectsQuery(FIND_ALL_AFTER, cursorProjectId, limit + 1);
            projectsAfter = projects.subList(0, Math.min(projects.size(), limit));
        }
        return projectsAfter;
    }

    private List<Project> getAllProjectsQuery(String sqlQuery, String cursorProjectId, int limit) {
        return this.jdbcClient
                .sql(sqlQuery)
                .param(CURSOR_PROJECT_ID, cursorProjectId)
                .param(LIMIT, limit)
                .query(Project.class)
                .list();
    }
}
