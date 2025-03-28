/***********************************************************************************************
 * Copyright (c) 2025 Obeo. All Rights Reserved.
 * This software and the attached documentation are the exclusive ownership
 * of its authors and was conceded to the profit of Obeo S.A.S.
 * This software and the attached documentation are protected under the rights
 * of intellectual ownership, including the section "Titre II  Droits des auteurs (Articles L121-1 L123-12)"
 * By installing this software, you acknowledge being aware of these rights and
 * accept them, and as a consequence you must:
 * - be in possession of a valid license of use conceded by Obeo only.
 * - agree that you have read, understood, and will comply with the license terms and conditions.
 * - agree not to do anything that could conflict with intellectual ownership owned by Obeo or its beneficiaries
 * or the authors of this software.
 *
 * Should you not agree with these terms, you must stop to use this software and give it back to its legitimate owner.
 ***********************************************************************************************/
package org.eclipse.sirius.web.ai.tool.edition;

import org.eclipse.sirius.components.collaborative.forms.dto.EditCheckboxInput;
import org.eclipse.sirius.web.ai.tool.service.AiDiagramService;
import org.eclipse.sirius.web.ai.codec.UUIDCodec;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.collaborative.forms.api.FormCreationParameters;
import org.eclipse.sirius.components.collaborative.forms.api.IFormPostProcessor;
import org.eclipse.sirius.components.collaborative.forms.api.IPropertiesDefaultDescriptionProvider;
import org.eclipse.sirius.components.collaborative.forms.api.IPropertiesDescriptionService;
import org.eclipse.sirius.components.collaborative.forms.configuration.FormEventProcessorFactoryConfiguration;
import org.eclipse.sirius.components.collaborative.forms.dto.EditMultiSelectInput;
import org.eclipse.sirius.components.collaborative.forms.dto.EditRadioInput;
import org.eclipse.sirius.components.collaborative.forms.dto.EditTextfieldInput;
import org.eclipse.sirius.components.collaborative.forms.variables.FormVariableProvider;
import org.eclipse.sirius.components.core.api.*;
import org.eclipse.sirius.components.forms.*;
import org.eclipse.sirius.components.forms.components.FormComponent;
import org.eclipse.sirius.components.forms.components.FormComponentProps;
import org.eclipse.sirius.components.forms.description.FormDescription;
import org.eclipse.sirius.components.forms.description.PageDescription;
import org.eclipse.sirius.components.forms.renderer.FormRenderer;
import org.eclipse.sirius.components.forms.renderer.IWidgetDescriptor;
import org.eclipse.sirius.components.representations.Element;
import org.eclipse.sirius.components.representations.GetOrCreateRandomIdProvider;
import org.eclipse.sirius.components.representations.VariableManager;
import org.eclipse.sirius.web.application.views.details.services.DetailsViewFormDescriptionAggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class EditionToolService {
    private static final Logger logger = LoggerFactory.getLogger(EditionToolService.class);

    private final IFormPostProcessor formPostProcessor;

    private final List<IWidgetDescriptor> widgetDescriptors;

    private final IObjectService objectService;

    private final IPropertiesDescriptionService propertiesDescriptionService;

    private final IPropertiesDefaultDescriptionProvider propertiesDefaultDescriptionProvider;

    private AiDiagramService aiDiagramService;

    private FormCreationParameters formCreationParameters = null;

    private VariableManager variableManager = null;

    public EditionToolService(FormEventProcessorFactoryConfiguration formConfiguration,
                              List<IWidgetDescriptor> widgetDescriptors,
                              IPropertiesDefaultDescriptionProvider propertiesDefaultDescriptionProvider,
                              IPropertiesDescriptionService propertiesDescriptionService) {
        this.widgetDescriptors = Objects.requireNonNull(widgetDescriptors);
        this.objectService = Objects.requireNonNull(formConfiguration.getObjectService());
        this.formPostProcessor = Objects.requireNonNull(formConfiguration.getFormPostProcessor());
        this.propertiesDefaultDescriptionProvider = Objects.requireNonNull(propertiesDefaultDescriptionProvider);
        this.propertiesDescriptionService = Objects.requireNonNull(propertiesDescriptionService);
    }

    public void setDiagramService(AiDiagramService aiDiagramService) {
        this.aiDiagramService = aiDiagramService;
    }


    // ---------------------------------------------------------------------------------------------------------------
    //                                                  INITIALIZERS
    // ---------------------------------------------------------------------------------------------------------------


    private void initializeFormCreationParameters(Object object) {
        List<PageDescription> pageDescriptions = this.propertiesDescriptionService.getPropertiesDescriptions();

        Optional<FormDescription> optionalFormDescription = Optional.empty();
        if (!pageDescriptions.isEmpty()) {
            optionalFormDescription = new DetailsViewFormDescriptionAggregator().aggregate(pageDescriptions, List.of(object), this.objectService);
        }
        FormDescription formDescription = optionalFormDescription.orElse(this.propertiesDefaultDescriptionProvider.getFormDescription());

        var optionalEditingContext = this.aiDiagramService.getEditingContext();
        assert optionalEditingContext.isPresent();
        this.formCreationParameters = FormCreationParameters.newFormCreationParameters(this.aiDiagramService.getDiagramId())
                .editingContext(optionalEditingContext.get())
                .formDescription(formDescription)
                .object(object)
                .selection(List.of())
                .build();
    }

    private void initializeVariableManager() {
        var formDescription = this.formCreationParameters.getFormDescription();
        var self = this.formCreationParameters.getObject();

        VariableManager initialVariableManager = new VariableManager();
        initialVariableManager.put(VariableManager.SELF, self);
        initialVariableManager.put(FormVariableProvider.SELECTION.name(), this.formCreationParameters.getSelection());
        initialVariableManager.put(GetOrCreateRandomIdProvider.PREVIOUS_REPRESENTATION_ID, this.formCreationParameters.getId());
        initialVariableManager.put(IEditingContext.EDITING_CONTEXT, this.formCreationParameters.getEditingContext());

        var initializer = formDescription.getVariableManagerInitializer();
        this.variableManager = initializer.apply(initialVariableManager);
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  SERVICE GETTER METHODS
    // ---------------------------------------------------------------------------------------------------------------

    Form getFormForObject(String elementId, boolean isObject) {
        UUID decompressedElementId;

        try {
            decompressedElementId = new UUIDCodec().decompress(elementId);
        } catch (Exception e) {
            throw new UnsupportedOperationException("id is not in the correct format.");
        }

        var optionalEditingContext = this.aiDiagramService.getEditingContext();
        assert optionalEditingContext.isPresent();
        Optional<Object> optionalObject;
        if (isObject) {
            var node = this.aiDiagramService.findNode(decompressedElementId.toString());

            Objects.requireNonNull(node);
            optionalObject = this.objectService.getObject(optionalEditingContext.get(), node.getTargetObjectId());
        } else {
            var edge = this.aiDiagramService.findEdge(decompressedElementId.toString());

            Objects.requireNonNull(edge);
            optionalObject = this.objectService.getObject(optionalEditingContext.get(), edge.getTargetObjectId());
        }

        assert optionalObject.isPresent();
        if (this.formCreationParameters == null) {
            this.initializeFormCreationParameters(optionalObject.get());
        }
        if (this.variableManager == null) {
            this.initializeVariableManager();
        } else {
            this.variableManager.put(VariableManager.SELF, optionalObject.get());
        }

        FormComponentProps formComponentProps = new FormComponentProps(this.variableManager, this.formCreationParameters.getFormDescription(), this.widgetDescriptors);
        Element element = new Element(FormComponent.class, formComponentProps);
        Form form = new FormRenderer(this.widgetDescriptors).render(element);

        form = this.formPostProcessor.postProcess(form, this.variableManager);

        return form;
    }

    HashMap<String, Map<String, Object>> getProperties(Form form) {
        var properties = new HashMap<String,Map<String, Object>>();
        var checkboxProperty = new HashMap<String, Object>();
        var singleValueProperty = new HashMap<String, Object>();
        var multipleValueProperty = new HashMap<String, Object>();

        // TODO: add widget type support
        for (var page : form.getPages()) {
            for (var group : page.getGroups()) {
                for (var widget : group.getWidgets()) {
                    if (widget instanceof Checkbox checkbox) {
                        checkboxProperty.put(checkbox.getLabel(), checkbox.isValue());

                    } else if (widget instanceof Radio radio) {
                        var options = new ArrayList<String>();
                        for (var option : radio.getOptions()) {
                            options.add(option.getLabel());
                        }

                        singleValueProperty.put(radio.getLabel(), options);

                    } else if (widget instanceof Textfield textfield) {
                        singleValueProperty.put(textfield.getLabel(), textfield.getValue());

                    } else if (widget instanceof Textarea textarea) {
                        singleValueProperty.put(textarea.getLabel(), textarea.getValue());

                    } else if (widget instanceof MultiSelect multiSelect) {
                        var options = new ArrayList<String>();
                        for (var option : multiSelect.getOptions()) {
                            options.add(option.getLabel());
                        }

                        multipleValueProperty.put(multiSelect.getLabel(), options);
                    }
                }
            }
        }

        properties.put("Checkbox Properties", checkboxProperty);
        properties.put("Single Valued Properties", singleValueProperty);
        properties.put("Multiple Valued Properties", multipleValueProperty);

        return properties;
    }

    Optional<AbstractWidget> getWidget(String objectId, String propertyLabel, boolean isObject) {
        var form = this.getFormForObject(objectId, isObject);

        for (var page : form.getPages()) {
            for (var group : page.getGroups()) {
                for (var widget : group.getWidgets()) {
                    if (widget.getLabel().equals(propertyLabel)) {
                        return Optional.of(widget);
                    }
                }
            }
        }

        return Optional.empty();
    }


    // ---------------------------------------------------------------------------------------------------------------
    //                                                PROPERTIES MODIFIERS
    // ---------------------------------------------------------------------------------------------------------------

    String changeCheckboxProperty(boolean checked, AbstractWidget widget, StringBuilder representationId, EditingContextEventProcessorRegistry editingContextEventProcessorRegistry) {
        var formInput = new EditCheckboxInput(
                UUID.randomUUID(),
                this.aiDiagramService.getEditingContextId(),
                representationId.toString(),
                widget.getId(),
                checked
        );

        return sendPropertyChange(formInput, editingContextEventProcessorRegistry);
    }

    String changePropertySingleValue(String newPropertyValue, AbstractWidget widget, StringBuilder representationId, EditingContextEventProcessorRegistry editingContextEventProcessorRegistry) {
        IInput formInput = null;

        if (widget instanceof Radio radio) {
            var optionId = radio.getOptions().stream()
                    .filter(option -> option.getLabel().equals(newPropertyValue))
                    .findFirst()
                    .get()
                    .getId();

            formInput = new EditRadioInput(
                    UUID.randomUUID(),
                    this.aiDiagramService.getEditingContextId(),
                    representationId.toString(),
                    radio.getId(),
                    optionId
            );
        }

        if ((widget instanceof Textfield) || (widget instanceof Textarea)) {
            formInput = new EditTextfieldInput(
                    UUID.randomUUID(),
                    this.aiDiagramService.getEditingContextId(),
                    representationId.toString(),
                    widget.getId(),
                    newPropertyValue
            );
        }

        return sendPropertyChange(formInput, editingContextEventProcessorRegistry);
    }

    String changePropertyMultipleValue(List<String> newPropertyValues, AbstractWidget widget, StringBuilder representationId, EditingContextEventProcessorRegistry editingContextEventProcessorRegistry) {
        IInput formInput = null;

        if (widget instanceof MultiSelect multiSelect) {
            var optionsId = new ArrayList<String>();
            for (var option : multiSelect.getOptions()) {
                if (newPropertyValues.contains(option.getLabel())) {
                    optionsId.add(option.getId());
                }
            }

            formInput = new EditMultiSelectInput(
                    UUID.randomUUID(),
                    this.aiDiagramService.getEditingContextId(),
                    representationId.toString(),
                    multiSelect.getId(),
                    optionsId
            );
        }

        return sendPropertyChange(formInput, editingContextEventProcessorRegistry);
    }

    private String sendPropertyChange(IInput formInput, EditingContextEventProcessorRegistry editingContextEventProcessorRegistry) {
        var monoPayload = new AtomicReference<Mono<IPayload>>();
        editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(this.aiDiagramService.getEditingContextId())
                .ifPresent(processor -> monoPayload.set(processor.handle(formInput)));

        AtomicReference<String> result = new AtomicReference<>();
        monoPayload.get().subscribe( payload -> {
            if (payload instanceof ErrorPayload) {
                result.set("Failure, try something else. Mind the new value type, maybe only numbers or letters are allowed");
            } else {
                result.set("Success");
            }
        });

        return result.get();
    }
}
