/*******************************************************************************
 * Copyright (c) 2022, 2024 Obeo.
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
package org.eclipse.sirius.components.forms;

import java.text.MessageFormat;
import java.util.Objects;

import org.eclipse.sirius.components.annotations.Immutable;

/**
 * The style of a Checkbox.
 *
 * @author arichard
 */
@Immutable
public final class CheckboxStyle {

    private String color;

    private WidgetGridLayout widgetGridLayout;

    private CheckboxStyle() {
        // Prevent instantiation
    }

    public String getColor() {
        return this.color;
    }

    public WidgetGridLayout getWidgetGridLayout() {
        return this.widgetGridLayout;
    }

    public static Builder newCheckboxStyle() {
        return new Builder();
    }

    @Override
    public String toString() {
        String pattern = "{0} '{'color: {1}'}'";
        return MessageFormat.format(pattern, this.getClass().getSimpleName(), this.color);
    }

    /**
     * Builder used to create the Checkbox style.
     *
     * @author arichard
     */
    @SuppressWarnings("checkstyle:HiddenField")
    public static final class Builder {

        private String color;

        private WidgetGridLayout widgetGridLayout;

        private Builder() {
        }

        public Builder color(String color) {
            this.color = Objects.requireNonNull(color);
            return this;
        }

        public Builder widgetGridLayout(WidgetGridLayout widgetGridLayout) {
            this.widgetGridLayout = Objects.requireNonNull(widgetGridLayout);
            return this;
        }

        public CheckboxStyle build() {
            CheckboxStyle checkboxStyle = new CheckboxStyle();
            checkboxStyle.color = this.color; // Optional on purpose
            checkboxStyle.widgetGridLayout = this.widgetGridLayout;
            return checkboxStyle;
        }

    }
}
