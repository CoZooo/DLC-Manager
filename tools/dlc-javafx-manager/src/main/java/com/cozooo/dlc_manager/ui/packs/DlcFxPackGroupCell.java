package com.cozooo.dlc_manager.ui.packs;
import com.cozooo.dlc_manager.ui.common.DlcFxRichTextRenderer;
import com.cozooo.dlc_manager.ui.common.DlcFxStyle;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackRowState;
import com.cozooo.dlc_manager.ui.packs.DlcFxPackRowView;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuButton;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

public final class DlcFxPackGroupCell<G, E> extends ListCell<G> {
    public interface Adapter<G, E> {
        E displayEntry(G group);
        DlcFxPackRowState rowState(G group, E entry, int rowIndex, int rowCount);
        Node versionLine(G group);
        String title(E entry);
        String author(E entry);
        String description(E entry);
        Image icon(E entry);
        String iconFallback(E entry);
        String actionText(G group);
        default void onPrimaryPress(G group, E entry, int rowIndex) {
        }
        void openHomepage(E entry);
        void moveUp(G group);
        void moveDown(G group);
        void toggleInstall(G group);
        void deleteArchive(G group);
        void openIssue(E entry);
        void openSponsor(E entry);
    }

    private final Adapter<G, E> adapter;

    public DlcFxPackGroupCell(Adapter<G, E> adapter) {
        this.adapter = adapter;
        addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY || isEmpty() || isInsideButton(event.getTarget())) {
                return;
            }
            G group = getItem();
            E entry = group == null ? null : adapter.displayEntry(group);
            adapter.onPrimaryPress(group, entry, getIndex());
            if (event.getClickCount() >= 2 && entry != null) {
                adapter.openHomepage(entry);
            }
            event.consume();
        });
    }

    @Override
    protected void updateItem(G group, boolean empty) {
        super.updateItem(group, empty);
        E entry = group == null ? null : adapter.displayEntry(group);
        if (empty || group == null || entry == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        Node title = DlcFxRichTextRenderer.formatted(adapter.title(entry));
        DlcFxStyle.add(title, "row-title");
        Node author = DlcFxRichTextRenderer.formatted(adapter.author(entry));
        DlcFxStyle.add(author, "muted");
        Node description = DlcFxRichTextRenderer.markdown(adapter.description(entry));
        DlcFxStyle.add(description, "muted");

        setText(null);
        setGraphic(DlcFxPackRowView.create(new DlcFxPackRowView.Spec(
                adapter.rowState(group, entry, getIndex(), getListView().getItems().size()),
                title,
                author,
                adapter.versionLine(group),
                description,
                DlcFxPackRowView.packIcon(adapter.icon(entry), adapter.iconFallback(entry)),
                adapter.actionText(group),
                () -> adapter.moveUp(group),
                () -> adapter.moveDown(group),
                () -> adapter.toggleInstall(group),
                () -> adapter.deleteArchive(group),
                () -> adapter.openIssue(entry),
                () -> adapter.openSponsor(entry)
        )));
    }

    private boolean isInsideButton(Object target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        for (Node current = node; current != null; current = current.getParent()) {
            if (current instanceof Button || current instanceof MenuButton) {
                return true;
            }
            if (current == this) {
                return false;
            }
        }
        return false;
    }
}
