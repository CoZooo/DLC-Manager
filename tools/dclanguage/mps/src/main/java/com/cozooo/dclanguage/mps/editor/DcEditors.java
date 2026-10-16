package com.cozooo.dclanguage.mps.editor;

import static com.cozooo.dclanguage.mps.DcConcepts.*;
import com.cozooo.dclanguage.mps.DcModelCodec;
import com.cozooo.dclanguage.DcDocuments;
import com.google.gson.JsonPrimitive;
import static com.cozooo.dclanguage.generated.DcGeneratedSyntax.*;
import jetbrains.mps.nodeEditor.*;
import jetbrains.mps.nodeEditor.cells.*;
import jetbrains.mps.nodeEditor.cellLayout.CellLayout_Vertical;
import jetbrains.mps.lang.editor.cellProviders.*;
import jetbrains.mps.openapi.editor.EditorContext;
import jetbrains.mps.openapi.editor.cells.EditorCell;
import jetbrains.mps.openapi.editor.descriptor.ConceptEditor;
import jetbrains.mps.editor.runtime.style.StyleAttributes;
import org.jetbrains.mps.openapi.language.*;
import org.jetbrains.mps.openapi.model.SNode;
import java.awt.Color;
import java.util.*;

public final class DcEditors extends EditorAspectDescriptorBase {
    @Override public Collection<ConceptEditor> getDeclaredEditors(SAbstractConcept concept) {
        return concept.getLanguage().equals(LANGUAGE) && !concept.equals(VALUE) ? List.of(new DcEditor()) : List.of();
    }
    private static final class DcEditor extends DefaultNodeEditor {
        @Override public EditorCell createEditorCell(EditorContext context, SNode node) {
            EditorCell_Collection result;
            if (node.getConcept().equals(DOCUMENT)) {
                result = EditorCell_Collection.createVertical(context, node);
                result.addEditorCell(label(context, node, "DC  "));
                result.addEditorCell(property(context, node, NAME, "document"));
                for (String error : DcModelCodec.validate(node)) {
                    EditorCell_Constant message = label(context, node, error);
                    message.getStyle().set(StyleAttributes.TEXT_COLOR, new Color(190, 65, 65));
                    result.addEditorCell(message);
                }
                result.addEditorCell(children(context, node, ROOT_MEMBERS, MEMBER));
            } else if (node.getConcept().equals(MEMBER)) {
                result = EditorCell_Collection.createHorizontal(context, node);
                result.addEditorCell(property(context, node, NAME, "field"));
                result.addEditorCell(label(context, node, " " + TOKEN_ASSIGNMENT + " "));
                result.addEditorCell(new SingleRoleCellProvider(MEMBER_VALUE, context) {
                    @Override public SNode getNode() { return node; }
                }.createCell());
            } else if (node.getConcept().equals(SCALAR)) {
                return property(context, node, TEXT, DcDocuments.writeValue(new JsonPrimitive("value")));
            } else {
                boolean object = node.getConcept().equals(OBJECT);
                result = EditorCell_Collection.createVertical(context, node);
                result.addEditorCell(label(context, node, object ? TOKEN_OBJECT_OPEN : TOKEN_ARRAY_OPEN));
                result.addEditorCell(children(context, node, object ? OBJECT_MEMBERS : ARRAY_VALUES, object ? MEMBER : VALUE));
                result.addEditorCell(label(context, node, object ? TOKEN_OBJECT_CLOSE : TOKEN_ARRAY_CLOSE));
                result.setFoldable(true);
                result.setFoldedCell(label(context, node, object ? TOKEN_OBJECT_OPEN + " … " + TOKEN_OBJECT_CLOSE : TOKEN_ARRAY_OPEN + " … " + TOKEN_ARRAY_CLOSE));
            }
            result.setBig(true);
            return result;
        }
        private EditorCell children(EditorContext context, SNode node, SContainmentLink link, SConcept target) {
            return new RefNodeListHandler(context) {
                @Override public SNode getNode() { return node; }
                @Override public SContainmentLink getSLink() { return link; }
                @Override public SAbstractConcept getChildSConcept() { return target; }
            }.createCells(new CellLayout_Vertical());
        }
        private EditorCell_Property property(EditorContext context, SNode node, SProperty property, String empty) {
            var cell = EditorCell_Property.create(context, new SPropertyAccessor(node, property, false, false), node);
            cell.setCellId("dc-" + property.getName());
            cell.setDefaultText(empty);
            return cell;
        }
        private EditorCell_Constant label(EditorContext context, SNode node, String value) {
            var cell = new EditorCell_Constant(context, node, value);
            cell.getStyle().set(StyleAttributes.SELECTABLE, false);
            return cell;
        }
    }
}
