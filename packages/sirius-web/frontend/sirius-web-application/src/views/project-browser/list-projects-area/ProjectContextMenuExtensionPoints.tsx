/*******************************************************************************
 * Copyright (c) 2024 Obeo.
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
import { ComponentExtensionPoint } from '@eclipse-sirius/sirius-components-core';
import { ProjectContextMenuContainerProps, ProjectContextMenuEntryProps } from './ProjectActionButton.types';

const FallbackProjectContextMenuContainer = ({ children }: ProjectContextMenuContainerProps) => {
  return <div>{children}</div>;
};

export const projectContextMenuContainerExtensionPoint: ComponentExtensionPoint<ProjectContextMenuContainerProps> = {
  identifier: 'projectContextMenu#container',
  FallbackComponent: FallbackProjectContextMenuContainer,
};

export const projectContextMenuEntryExtensionPoint: ComponentExtensionPoint<ProjectContextMenuEntryProps> = {
  identifier: 'projectContextMenu#entry',
  FallbackComponent: () => null,
};
