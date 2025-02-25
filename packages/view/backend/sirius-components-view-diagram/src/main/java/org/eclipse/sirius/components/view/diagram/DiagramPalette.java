/*******************************************************************************
 * Copyright (c) 2023, 2025 Obeo.
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
package org.eclipse.sirius.components.view.diagram;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;

/**
 * <!-- begin-user-doc --> A representation of the model object '<em><b>Palette</b></em>'. <!-- end-user-doc -->
 *
 * <p>
 * The following features are supported:
 * </p>
 * <ul>
 * <li>{@link org.eclipse.sirius.components.view.diagram.DiagramPalette#getDropTool <em>Drop Tool</em>}</li>
 * <li>{@link org.eclipse.sirius.components.view.diagram.DiagramPalette#getDropNodeTool <em>Drop Node Tool</em>}</li>
 * <li>{@link org.eclipse.sirius.components.view.diagram.DiagramPalette#getNodeTools <em>Node Tools</em>}</li>
 * <li>{@link org.eclipse.sirius.components.view.diagram.DiagramPalette#getQuickAccessTools <em>Quick Access
 * Tools</em>}</li>
 * <li>{@link org.eclipse.sirius.components.view.diagram.DiagramPalette#getToolSections <em>Tool Sections</em>}</li>
 * </ul>
 *
 * @see org.eclipse.sirius.components.view.diagram.DiagramPackage#getDiagramPalette()
 * @model
 * @generated
 */
public interface DiagramPalette extends EObject {
    /**
     * Returns the value of the '<em><b>Drop Tool</b></em>' containment reference. <!-- begin-user-doc --> <!--
     * end-user-doc -->
     *
     * @return the value of the '<em>Drop Tool</em>' containment reference.
     * @see #setDropTool(DropTool)
     * @see org.eclipse.sirius.components.view.diagram.DiagramPackage#getDiagramPalette_DropTool()
     * @model containment="true"
     * @generated
     */
    DropTool getDropTool();

    /**
     * Sets the value of the '{@link org.eclipse.sirius.components.view.diagram.DiagramPalette#getDropTool <em>Drop
     * Tool</em>}' containment reference. <!-- begin-user-doc --> <!-- end-user-doc -->
     *
     * @param value
     *            the new value of the '<em>Drop Tool</em>' containment reference.
     * @see #getDropTool()
     * @generated
     */
    void setDropTool(DropTool value);

    /**
     * Returns the value of the '<em><b>Drop Node Tool</b></em>' containment reference. <!-- begin-user-doc --> <!--
     * end-user-doc -->
     *
     * @return the value of the '<em>Drop Node Tool</em>' containment reference.
     * @see #setDropNodeTool(DropNodeTool)
     * @see org.eclipse.sirius.components.view.diagram.DiagramPackage#getDiagramPalette_DropNodeTool()
     * @model containment="true"
     * @generated
     */
    DropNodeTool getDropNodeTool();

    /**
     * Sets the value of the '{@link org.eclipse.sirius.components.view.diagram.DiagramPalette#getDropNodeTool <em>Drop
     * Node Tool</em>}' containment reference. <!-- begin-user-doc --> <!-- end-user-doc -->
     *
     * @param value
     *            the new value of the '<em>Drop Node Tool</em>' containment reference.
     * @see #getDropNodeTool()
     * @generated
     */
    void setDropNodeTool(DropNodeTool value);

    /**
     * Returns the value of the '<em><b>Node Tools</b></em>' containment reference list. The list contents are of type
     * {@link org.eclipse.sirius.components.view.diagram.NodeTool}. <!-- begin-user-doc --> <!-- end-user-doc -->
     *
     * @return the value of the '<em>Node Tools</em>' containment reference list.
     * @see org.eclipse.sirius.components.view.diagram.DiagramPackage#getDiagramPalette_NodeTools()
     * @model containment="true" keys="name"
     * @generated
     */
    EList<NodeTool> getNodeTools();

    /**
     * Returns the value of the '<em><b>Quick Access Tools</b></em>' containment reference list. The list contents are
     * of type {@link org.eclipse.sirius.components.view.diagram.NodeTool}. <!-- begin-user-doc --> <!-- end-user-doc
     * -->
     *
     * @return the value of the '<em>Quick Access Tools</em>' containment reference list.
     * @see org.eclipse.sirius.components.view.diagram.DiagramPackage#getDiagramPalette_QuickAccessTools()
     * @model containment="true" keys="name"
     * @generated
     */
    EList<NodeTool> getQuickAccessTools();

    /**
     * Returns the value of the '<em><b>Tool Sections</b></em>' containment reference list. The list contents are of
     * type {@link org.eclipse.sirius.components.view.diagram.DiagramToolSection}. <!-- begin-user-doc --> <!--
     * end-user-doc -->
     *
     * @return the value of the '<em>Tool Sections</em>' containment reference list.
     * @see org.eclipse.sirius.components.view.diagram.DiagramPackage#getDiagramPalette_ToolSections()
     * @model containment="true" keys="name"
     * @generated
     */
    EList<DiagramToolSection> getToolSections();

} // DiagramPalette
