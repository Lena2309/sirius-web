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
package org.eclipse.sirius.components.tables.descriptions;

import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.sirius.components.annotations.Immutable;
import org.eclipse.sirius.components.representations.VariableManager;

/**
 * The description of the line.
 *
 * @author arichard
 */
@Immutable
public final class LineDescription {

    /**
     * The variable name used to store a reference to a row.
     */
    public static final String SELECTED_ROW = "selectedRow";

    private String id;

    private Function<VariableManager, String> targetObjectIdProvider;

    private Function<VariableManager, String> targetObjectKindProvider;

    private Function<VariableManager, PaginatedData> semanticElementsProvider;

    private Function<VariableManager, String> headerLabelProvider;

    private Function<VariableManager, List<String>> headerIconURLsProvider;

    private Function<VariableManager, String> headerIndexLabelProvider;

    private Function<VariableManager, Integer> initialHeightProvider;

    private Predicate<VariableManager> isResizablePredicate;

    private Function<VariableManager, Integer> depthLevelProvider;

    private LineDescription() {
        // Prevent instantiation
    }

    public static Builder newLineDescription(String id) {
        return new Builder(id);
    }

    public String getId() {
        return this.id;
    }

    public Function<VariableManager, String> getTargetObjectIdProvider() {
        return this.targetObjectIdProvider;
    }

    public Function<VariableManager, String> getTargetObjectKindProvider() {
        return this.targetObjectKindProvider;
    }

    public Function<VariableManager, PaginatedData> getSemanticElementsProvider() {
        return this.semanticElementsProvider;
    }

    public Function<VariableManager, String> getHeaderLabelProvider() {
        return this.headerLabelProvider;
    }

    public Function<VariableManager, List<String>> getHeaderIconURLsProvider() {
        return this.headerIconURLsProvider;
    }

    public Function<VariableManager, String> getHeaderIndexLabelProvider() {
        return this.headerIndexLabelProvider;
    }

    public Function<VariableManager, Integer> getInitialHeightProvider() {
        return this.initialHeightProvider;
    }

    public Predicate<VariableManager> getIsResizablePredicate() {
        return this.isResizablePredicate;
    }

    public Function<VariableManager, Integer> getDepthLevelProvider() {
        return this.depthLevelProvider;
    }

    @Override
    public String toString() {
        String pattern = "{0} '{'id: {1}'}'";
        return MessageFormat.format(pattern, this.getClass().getSimpleName(), this.id);
    }

    /**
     * The builder used to create the line description.
     *
     * @author arichard
     */
    @SuppressWarnings("checkstyle:HiddenField")
    public static final class Builder {

        private final String id;

        private Function<VariableManager, String> targetObjectIdProvider;

        private Function<VariableManager, String> targetObjectKindProvider;

        private Function<VariableManager, PaginatedData> semanticElementsProvider;

        private Function<VariableManager, String> headerLabelProvider;

        private Function<VariableManager, List<String>> headerIconURLsProvider;

        private Function<VariableManager, String> headerIndexLabelProvider;

        private Function<VariableManager, Integer> initialHeightProvider;

        private Predicate<VariableManager> isResizablePredicate;

        private Function<VariableManager, Integer> depthLevelProvider;

        public Builder(String id) {
            this.id = Objects.requireNonNull(id);
        }

        public Builder targetObjectIdProvider(Function<VariableManager, String> targetObjectIdProvider) {
            this.targetObjectIdProvider = Objects.requireNonNull(targetObjectIdProvider);
            return this;
        }

        public Builder targetObjectKindProvider(Function<VariableManager, String> targetObjectKindProvider) {
            this.targetObjectKindProvider = Objects.requireNonNull(targetObjectKindProvider);
            return this;
        }

        public Builder semanticElementsProvider(Function<VariableManager, PaginatedData> semanticElementsProvider) {
            this.semanticElementsProvider = Objects.requireNonNull(semanticElementsProvider);
            return this;
        }

        public Builder headerLabelProvider(Function<VariableManager, String> headerLabelProvider) {
            this.headerLabelProvider = Objects.requireNonNull(headerLabelProvider);
            return this;
        }

        public Builder headerIconURLsProvider(Function<VariableManager, List<String>> headerIconURLsProvider) {
            this.headerIconURLsProvider = Objects.requireNonNull(headerIconURLsProvider);
            return this;
        }

        public Builder headerIndexLabelProvider(Function<VariableManager, String> headerIndexLabelProvider) {
            this.headerIndexLabelProvider = Objects.requireNonNull(headerIndexLabelProvider);
            return this;
        }

        public Builder initialHeightProvider(Function<VariableManager, Integer> initialHeightProvider) {
            this.initialHeightProvider = Objects.requireNonNull(initialHeightProvider);
            return this;
        }

        public Builder isResizablePredicate(Predicate<VariableManager> isResizablePredicate) {
            this.isResizablePredicate = Objects.requireNonNull(isResizablePredicate);
            return this;
        }

        public Builder depthLevelProvider(Function<VariableManager, Integer> depthLevelProvider) {
            this.depthLevelProvider = Objects.requireNonNull(depthLevelProvider);
            return this;
        }

        public LineDescription build() {
            LineDescription lineDescription = new LineDescription();
            lineDescription.id = Objects.requireNonNull(this.id);
            lineDescription.targetObjectIdProvider = Objects.requireNonNull(this.targetObjectIdProvider);
            lineDescription.targetObjectKindProvider = Objects.requireNonNull(this.targetObjectKindProvider);
            lineDescription.semanticElementsProvider = Objects.requireNonNull(this.semanticElementsProvider);
            lineDescription.headerLabelProvider = Objects.requireNonNull(this.headerLabelProvider);
            lineDescription.headerIconURLsProvider = Objects.requireNonNull(this.headerIconURLsProvider);
            lineDescription.headerIndexLabelProvider = Objects.requireNonNull(this.headerIndexLabelProvider);
            lineDescription.initialHeightProvider = Objects.requireNonNull(this.initialHeightProvider);
            lineDescription.isResizablePredicate = Objects.requireNonNull(this.isResizablePredicate);
            lineDescription.depthLevelProvider = Objects.requireNonNull(this.depthLevelProvider);
            return lineDescription;
        }
    }
}
