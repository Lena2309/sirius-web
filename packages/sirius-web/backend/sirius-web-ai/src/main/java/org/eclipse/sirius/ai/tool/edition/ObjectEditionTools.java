package org.eclipse.sirius.ai.tool.edition;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.eclipse.sirius.ai.dto.AiRequestInput;
import org.eclipse.sirius.ai.tool.AiTools;
import org.eclipse.sirius.ai.util.UUIDConverter;
import org.eclipse.sirius.components.collaborative.api.IRepresentationSearchService;
import org.eclipse.sirius.components.collaborative.diagrams.dto.*;
import org.eclipse.sirius.components.collaborative.editingcontext.EditingContextEventProcessorRegistry;
import org.eclipse.sirius.components.collaborative.forms.api.FormCreationParameters;
import org.eclipse.sirius.components.collaborative.forms.api.IFormPostProcessor;
import org.eclipse.sirius.components.collaborative.forms.configuration.FormEventProcessorFactoryConfiguration;
import org.eclipse.sirius.components.collaborative.forms.dto.EditMultiSelectInput;
import org.eclipse.sirius.components.collaborative.forms.dto.EditRadioInput;
import org.eclipse.sirius.components.collaborative.forms.dto.EditTextfieldInput;
import org.eclipse.sirius.components.collaborative.forms.variables.FormVariableProvider;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IEditingContextSearchService;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.core.api.IObjectService;
import org.eclipse.sirius.components.diagrams.OutsideLabel;
import org.eclipse.sirius.components.forms.*;
import org.eclipse.sirius.components.forms.components.FormComponent;
import org.eclipse.sirius.components.forms.components.FormComponentProps;
import org.eclipse.sirius.components.forms.description.FormDescription;
import org.eclipse.sirius.components.forms.renderer.FormRenderer;
import org.eclipse.sirius.components.forms.renderer.IWidgetDescriptor;
import org.eclipse.sirius.components.representations.Element;
import org.eclipse.sirius.components.representations.GetOrCreateRandomIdProvider;
import org.eclipse.sirius.components.representations.VariableManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.List;

@Service
public class ObjectEditionTools extends AiTools {
    private static final Logger log = LoggerFactory.getLogger(ObjectEditionTools.class);

    private final IFormPostProcessor formPostProcessor;

    private final List<IWidgetDescriptor> widgetDescriptors;

    private final IObjectService objectService;

    private FormCreationParameters formCreationParameters = null;

    private VariableManager variableManager = null;

    public ObjectEditionTools(IRepresentationSearchService representationSearchService,
                              IEditingContextSearchService editingContextSearchService,
                              @Lazy EditingContextEventProcessorRegistry editingContextEventProcessorRegistry,
                              FormEventProcessorFactoryConfiguration formConfiguration,
                              List<IWidgetDescriptor> widgetDescriptors) {
        super(representationSearchService, editingContextSearchService, editingContextEventProcessorRegistry, formConfiguration.getFormEventHandlers());

        this.widgetDescriptors = Objects.requireNonNull(widgetDescriptors);
        this.objectService = Objects.requireNonNull(formConfiguration.getObjectService());

        this.formPostProcessor = Objects.requireNonNull(formConfiguration.getFormPostProcessor());
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  INITIALIZERS
    // ---------------------------------------------------------------------------------------------------------------


    private void initializeFormCreationParameters(Object object) {
        // TODO : null variable
        FormDescription formDescription = null;
        this.formCreationParameters = FormCreationParameters.newFormCreationParameters(this.input instanceof AiRequestInput ? ((AiRequestInput) this.input).representationId() : null)
                .editingContext(this.editingContext)
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

    private Form getFormForObject(String objectId) {
        Optional<Object> optionalObject = this.objectService.getObject(editingContext, UUIDConverter.decompress(objectId).toString());
        var objectNode = this.diagram.getNodes().stream()
                .filter(node -> node.getId().equals(UUIDConverter.decompress(objectId).toString()))
                .findFirst();

        assert objectNode.isPresent();
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

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  GET OBJECT PROPERTIES
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Retrieve a Map of an object properties structured as {property label, [property value options]} OR {property label, property current value}")
   public Map<String, Object> getProperties(String objectId) {
        var form = this.getFormForObject(objectId);

        var properties = new HashMap<String, Object>();

        // TODO: add widget type support
        for (var widget : form.getPages().get(0).getGroups().get(0).getWidgets()) {
            if (widget instanceof Radio radio) {

                var options = new ArrayList<String>();
                for (var option : radio.getOptions()) {
                    options.add(option.getLabel());
                }

                properties.put(radio.getLabel(), options);

            } else if (widget instanceof Textfield textfield) {
                properties.put(textfield.getLabel(), textfield.getValue());

            } else if (widget instanceof MultiSelect multiSelect) {

                var options = new ArrayList<String>();
                for (var option : multiSelect.getOptions()) {
                    options.add(option.getLabel());
                }

                properties.put(multiSelect.getLabel(), options);
            }

        }
        return properties;
   }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  EDIT OBJECT LABEL
    // ---------------------------------------------------------------------------------------------------------------

    @Tool("Edit the label of a diagram's object.")
    public String editObjectLabel(@P("The object's label Id to edit.") String objectId, String newLabel) {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            if (this.diagram == null) {
                this.refreshDiagram();
            }

            var labelId = diagram.getNodes().stream()
                    .filter(node -> Objects.equals(node.getId(), UUIDConverter.decompress(objectId).toString()))
                    .map(node -> {
                        List<OutsideLabel> outsideLabels = node.getOutsideLabels();
                        if(!outsideLabels.isEmpty()) {
                            return outsideLabels.get(0).id();
                        }
                        return node.getInsideLabel().getId();
                    })
                    .findFirst()
                    .orElse(null);

            assert labelId != null;
            var diagramInput = new EditLabelInput(
                    UUID.randomUUID(),
                    aiRequestInput.editingContextId(),
                    aiRequestInput.representationId(),
                    labelId,
                    newLabel
            );

            this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                    .ifPresent(processor -> processor.handle(diagramInput));

            return "Success";
        }
        return "Failure";
    }

    @Tool("Edit the label of an object's child.")
    public String editChildLabel(String parentId, String childId, String newLabel) {
        if (this.input instanceof AiRequestInput aiRequestInput) {
            if (this.diagram == null) {
                this.refreshDiagram();
            }

            var labelId = diagram.getNodes().stream()
                    .filter(node -> Objects.equals(node.getId(), UUIDConverter.decompress(parentId).toString()))
                    .map(node -> {
                        String childLabelId = node.getChildNodes().stream()
                                .filter(childNode -> Objects.equals(childNode.getId(), UUIDConverter.decompress(childId).toString()))
                                .map(childNode -> childNode.getOutsideLabels().get(0).id())
                                .findFirst()
                                .orElse(null);

                        assert childLabelId != null;
                        return childLabelId;
                    }).findFirst()
                    .orElse(null);

            var diagramInput = new EditLabelInput(
                    UUID.randomUUID(),
                    aiRequestInput.editingContextId(),
                    aiRequestInput.representationId(),
                    labelId,
                    newLabel
            );

            this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(diagramInput.editingContextId())
                    .ifPresent(processor -> processor.handle(diagramInput));

            return "Success";
        }
        return "Failure";
    }

    // ---------------------------------------------------------------------------------------------------------------
    //                                                  EDIT OBJECT PROPERTIES
    // ---------------------------------------------------------------------------------------------------------------

    public String editObjectProperty(String objectId, String propertyLabel, String newPropertyValue) {
        var widget = getWidget(objectId, propertyLabel);
        IInput formInput = null;

        var representationId = new StringBuilder("details://?objectIds=[");

        if (this.input instanceof AiRequestInput aiRequestInput) {
            representationId.append(aiRequestInput.representationId()).append("]");
        }

        if (widget instanceof Radio radio) {
            formInput = new EditRadioInput(
                    UUID.randomUUID(),
                    this.getEditingContextId(),
                    representationId.toString(),
                    radio.getId(),
                    newPropertyValue
            );
        }
        if (widget instanceof Textfield textfield) {
            formInput = new EditTextfieldInput(
                    UUID.randomUUID(),
                    this.getEditingContextId(),
                    representationId.toString(),
                    textfield.getId(),
                    newPropertyValue
            );
        }

        IInput finalFormInput = formInput;
        this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(this.getEditingContextId())
                .ifPresent(processor -> processor.handle(finalFormInput));

        return "Success";
    }

    public String editObjectPropertyWithMultiValues(String objectId, String propertyLabel, List<String> newPropertyValues) {
        var widget = getWidget(objectId, propertyLabel);
        IInput formInput = null;

        if (widget instanceof MultiSelect multiSelect) {
            if (this.input instanceof AiRequestInput aiRequestInput) {
                formInput = new EditMultiSelectInput(
                        UUID.randomUUID(),
                        aiRequestInput.editingContextId(),
                        aiRequestInput.representationId(),
                        widget.getId(),
                        newPropertyValues
                );
            }
        }

        IInput finalFormInput = formInput;
        this.editingContextEventProcessorRegistry.getOrCreateEditingContextEventProcessor(this.getEditingContextId())
                .ifPresent(processor -> processor.handle(finalFormInput));

        return "Success";
    }

    private AbstractWidget getWidget(String objectId, String propertyLabel) {
        var form = this.getFormForObject(objectId);

        var optionalWidget = form.getPages().get(0).getGroups().get(0).getWidgets().stream()
                .filter(abstractWidget -> Objects.equals(abstractWidget.getLabel(), propertyLabel))
                .findFirst();

        assert optionalWidget.isPresent();
        return optionalWidget.get();
    }
}
