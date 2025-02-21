package org.eclipse.sirius.web.ai.tool.edition;

import org.eclipse.sirius.web.ai.service.AiToolService;
import org.eclipse.sirius.web.ai.util.UUIDConverter;
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
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class EditionToolService {

    private final IFormPostProcessor formPostProcessor;

    private final List<IWidgetDescriptor> widgetDescriptors;

    private final IObjectService objectService;

    private final IPropertiesDescriptionService propertiesDescriptionService;

    private final IPropertiesDefaultDescriptionProvider propertiesDefaultDescriptionProvider;

    private AiToolService aiToolService;

    private FormCreationParameters formCreationParameters = null;

    private VariableManager variableManager = null;

    public EditionToolService(FormEventProcessorFactoryConfiguration formConfiguration,
                              List<IWidgetDescriptor> widgetDescriptors,
                              IPropertiesDefaultDescriptionProvider propertiesDefaultDescriptionProvider,
                              IPropertiesDescriptionService propertiesDescriptionService) {
        this.widgetDescriptors = widgetDescriptors;
        this.objectService = formConfiguration.getObjectService();
        this.formPostProcessor = formConfiguration.getFormPostProcessor();
        this.propertiesDefaultDescriptionProvider = propertiesDefaultDescriptionProvider;
        this.propertiesDescriptionService = propertiesDescriptionService;
    }

    public void setAiToolService(AiToolService aiToolService) {
        this.aiToolService = aiToolService;
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

        this.formCreationParameters = FormCreationParameters.newFormCreationParameters(this.aiToolService.getRepresentationId())
                .editingContext(this.aiToolService.getEditingContext())
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
        this.aiToolService.refreshDiagram();
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  SERVICE GETTER METHODS
    // ---------------------------------------------------------------------------------------------------------------

    Form getFormForObject(String elementId, boolean isObject) {
        this.aiToolService.refreshDiagram();

        Optional<Object> optionalObject;
        if (isObject) {
            var node = this.aiToolService.findNode(UUIDConverter.decompress(elementId).toString());

            assert node != null;
            optionalObject = this.objectService.getObject(this.aiToolService.getEditingContext(), node.getTargetObjectId());
            if (optionalObject.isEmpty()) {
                optionalObject = this.objectService.getObject(this.aiToolService.getEditingContext(), node.getId());
            }
        } else {
            var edge = this.aiToolService.findEdge(UUIDConverter.decompress(elementId).toString());

            assert edge != null;
            optionalObject = this.objectService.getObject(this.aiToolService.getEditingContext(), edge.getTargetObjectId());
            if (optionalObject.isEmpty()) {
                optionalObject = this.objectService.getObject(this.aiToolService.getEditingContext(), edge.getId());
            }
        }

        // TODO: Node trouvé mais pas l'object
        assert optionalObject.isPresent();
        if (this.formCreationParameters == null) {
            this.initializeFormCreationParameters(optionalObject.get());
        }
        if (this.variableManager == null) {
            this.initializeVariableManager();
        }

        FormComponentProps formComponentProps = new FormComponentProps(this.variableManager, this.formCreationParameters.getFormDescription(), this.widgetDescriptors);
        Element element = new Element(FormComponent.class, formComponentProps);
        Form form = new FormRenderer(this.widgetDescriptors).render(element);

        form = this.formPostProcessor.postProcess(form, this.variableManager);

        return form;
    }

    HashMap<String, Map<String, Object>> getProperties(Form form) {
        var properties = new HashMap<String,Map<String, Object>>();
        var singleValueProperty = new HashMap<String, Object>();
        var multipleValueProperty = new HashMap<String, Object>();

        // TODO: add widget type support
        for (var widget : form.getPages().get(0).getGroups().get(0).getWidgets()) {
            if (widget instanceof Radio radio) {

                var options = new ArrayList<String>();
                for (var option : radio.getOptions()) {
                    options.add(option.getLabel());
                }

                singleValueProperty.put(radio.getLabel(), options);

            } else if (widget instanceof Textfield textfield) {
                singleValueProperty.put(textfield.getLabel(), textfield.getValue());

            } else if (widget instanceof MultiSelect multiSelect) {

                var options = new ArrayList<String>();
                for (var option : multiSelect.getOptions()) {
                    options.add(option.getLabel());
                }

                multipleValueProperty.put(multiSelect.getLabel(), options);
            }
        }

        properties.put("Single Value Property", singleValueProperty);
        properties.put("Multiple Value Property", multipleValueProperty);

        return properties;
    }

    AbstractWidget getWidget(String objectId, String propertyLabel, boolean isObject) {
        var form = this.getFormForObject(objectId, isObject);

        var optionalWidget = form.getPages().get(0).getGroups().get(0).getWidgets().stream()
                .filter(abstractWidget -> Objects.equals(abstractWidget.getLabel(), propertyLabel))
                .findFirst();

        assert optionalWidget.isPresent();
        return optionalWidget.get();
    }


    // ---------------------------------------------------------------------------------------------------------------
    //                                                PROPERTIES MODIFIERS
    // ---------------------------------------------------------------------------------------------------------------

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
                    this.aiToolService.getEditingContextId(),
                    representationId.toString(),
                    radio.getId(),
                    optionId
            );
        }
        if (widget instanceof Textfield textfield) {
            formInput = new EditTextfieldInput(
                    UUID.randomUUID(),
                    this.aiToolService.getEditingContextId(),
                    representationId.toString(),
                    textfield.getId(),
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
                    this.aiToolService.getEditingContextId(),
                    representationId.toString(),
                    multiSelect.getId(),
                    optionsId
            );
        }

        return sendPropertyChange(formInput, editingContextEventProcessorRegistry);
    }

    private String sendPropertyChange(IInput formInput, EditingContextEventProcessorRegistry editingContextEventProcessorRegistry) {
        var monoPayload = new AtomicReference<Mono<IPayload>>();
        editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(this.aiToolService.getEditingContextId())
                .ifPresent(processor -> monoPayload.set(processor.handle(formInput)));

        AtomicReference<String> result = new AtomicReference<>("");
        monoPayload.get().subscribe( payload -> {
            if (payload instanceof ErrorPayload) {
                result.set("Failure");
            } else {
                result.set("Success");
            }
        });

        return result.get();
    }
}
