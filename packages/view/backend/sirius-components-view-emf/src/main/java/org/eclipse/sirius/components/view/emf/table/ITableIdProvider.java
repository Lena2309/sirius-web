/*******************************************************************************
 * Copyright (c) 2024, 2025 CEA LIST.
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
package org.eclipse.sirius.components.view.emf.table;

import org.eclipse.sirius.components.view.emf.IRepresentationDescriptionIdProvider;
import org.eclipse.sirius.components.view.table.CellDescription;
import org.eclipse.sirius.components.view.table.ColumnDescription;
import org.eclipse.sirius.components.view.table.RowDescription;
import org.eclipse.sirius.components.view.table.TableDescription;

/**
 * Interface to provide id for TableDescription.
 *
 * @author frouene
 */
public interface ITableIdProvider extends IRepresentationDescriptionIdProvider<TableDescription> {

    String TABLE_DESCRIPTION_KIND = PREFIX + "?kind=tableDescription";

    String CELL_DESCRIPTION_KIND = "siriusComponents://cellDescription";
    String COLUMN_DESCRIPTION_KIND = "siriusComponents://columnDescription";
    String ROW_DESCRIPTION_KIND = "siriusComponents://rowDescription";


    @Override
    String getId(TableDescription tableDescription);

    String getId(RowDescription rowDescription);

    String getId(ColumnDescription columnDescription);

    String getId(CellDescription cellDescription);

    /**
     * Implementation which does nothing, used for mocks in unit tests.
     *
     * @author frouene
     */
    class NoOp implements ITableIdProvider {

        @Override
        public String getId(TableDescription tableDescription) {
            return "";
        }

        @Override
        public String getId(RowDescription rowDescription) {
            return "";
        }

        @Override
        public String getId(ColumnDescription columnDescription) {
            return "";
        }

        @Override
        public String getId(CellDescription cellDescription) {
            return "";
        }

    }
}
