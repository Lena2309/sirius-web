/*******************************************************************************
 * Copyright (c) 2023, 2024 Obeo.
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
package org.eclipse.sirius.components.collaborative.gantt.dto.input;

import org.eclipse.sirius.components.gantt.TemporalType;

/**
 * The input of the "Edit task" mutation.
 *
 * @author lfasani
 */
public record EditGanttTaskDetailInput(String name, String description, String startTime, String endTime, TemporalType temporalType, int progress) {
}
