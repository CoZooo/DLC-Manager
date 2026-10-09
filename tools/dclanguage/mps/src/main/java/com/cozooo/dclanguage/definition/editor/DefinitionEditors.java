package com.cozooo.dclanguage.definition.editor;

import static com.cozooo.dclanguage.definition.DefinitionConcepts.*;
import com.cozooo.dclanguage.definition.DefinitionCompiler;
import jetbrains.mps.nodeEditor.*;
import jetbrains.mps.nodeEditor.cells.*;
import jetbrains.mps.nodeEditor.cellLayout.CellLayout_Vertical;
import jetbrains.mps.lang.editor.cellProviders.RefNodeListHandler;
import jetbrains.mps.nodeEditor.cellMenu.SPropertySubstituteInfo;
import jetbrains.mps.openapi.editor.EditorContext;
import jetbrains.mps.openapi.editor.cells.EditorCell;
import jetbrains.mps.openapi.editor.descriptor.ConceptEditor;
import jetbrains.mps.editor.runtime.style.StyleAttributes;
import org.jetbrains.mps.openapi.language.*;
import org.jetbrains.mps.openapi.model.SNode;
import java.awt.Color;
import java.util.*;

public final class DefinitionEditors extends EditorAspectDescriptorBase {
    @Override public Collection<ConceptEditor> getDeclaredEditors(SAbstractConcept concept) {
        return concept.getLanguage().equals(LANGUAGE) ? List.of(new DefinitionEditor()) : List.of();
    }
    private static final class DefinitionEditor extends DefaultNodeEditor {
        @Override public EditorCell createEditorCell(EditorContext context, SNode node) {
            Concept definition = CONCEPTS.get(node.getConcept().getName());
            var result = EditorCell_Collection.createVertical(context, node);
            result.setBig(true);
            var title = EditorCell_Collection.createHorizontal(context, node);
            title.addEditorCell(label(context, node, definition.name() + "  "));
            title.addEditorCell(propertyCell(context, node, NAME));result.addEditorCell(title);
            for (Property property : definition.properties()) {
                var row = EditorCell_Collection.createHorizontal(context, node);
                row.addEditorCell(label(context, node, "  " + property.name() + " = "));
                row.addEditorCell(propertyCell(context, node, property(definition.name(), property.name())));result.addEditorCell(row);
            }
            for (Child role : definition.children()) {
                result.addEditorCell(label(context, node, role.name() + ":"));
                result.addEditorCell(new RefNodeListHandler(context) {
                    @Override public SNode getNode() { return node; }
                    @Override public SContainmentLink getSLink() { return child(definition.name(), role.name()); }
                    @Override public SAbstractConcept getChildSConcept() { return concept(role.target()); }
                }.createCells(new CellLayout_Vertical()));
            }
            if (definition.root()) for (String issue : DefinitionCompiler.validate(node)) {
                var error = label(context, node, issue);error.getStyle().set(StyleAttributes.TEXT_COLOR,new Color(190,65,65));result.addEditorCell(error);
            }
            else if (!definition.children().isEmpty()) {
                result.setFoldable(true);result.setFoldedCell(label(context,node,definition.name()+" "+node.getProperty(NAME)+" { … }"));
            }
            return result;
        }
        private EditorCell_Property propertyCell(EditorContext context, SNode node, SProperty property) {
            var cell = EditorCell_Property.create(context,new SPropertyAccessor(node,property,false,false),node);
            cell.setCellId("definition-"+property.getName());cell.setDefaultText("<"+property.getName()+">");
            cell.setSubstituteInfo(new SPropertySubstituteInfo(cell,property));return cell;
        }
        private EditorCell_Constant label(EditorContext context,SNode node,String text) {
            var cell=new EditorCell_Constant(context,node,text);cell.getStyle().set(StyleAttributes.SELECTABLE,false);return cell;
        }
    }
}
