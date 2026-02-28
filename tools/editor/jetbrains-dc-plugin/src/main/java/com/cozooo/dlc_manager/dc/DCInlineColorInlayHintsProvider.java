package com.cozooo.dlc_manager.dc;

import com.intellij.codeInsight.hints.FactoryInlayHintsCollector;
import com.intellij.codeInsight.hints.ImmediateConfigurable;
import com.intellij.codeInsight.hints.InlayHintsCollector;
import com.intellij.codeInsight.hints.InlayHintsProvider;
import com.intellij.codeInsight.hints.InlayHintsSink;
import com.intellij.codeInsight.hints.NoSettings;
import com.intellij.codeInsight.hints.SettingsKey;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.codeInsight.hints.presentation.MouseButton;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.ui.ColorChooser;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DCInlineColorInlayHintsProvider implements InlayHintsProvider<NoSettings> {
    private static final SettingsKey<NoSettings> KEY = new SettingsKey<>("dc.inline.color.hints");
    private static final Pattern COLOR_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");

    @Override
    public @NotNull SettingsKey<NoSettings> getKey() {
        return KEY;
    }

    @Override
    public @NotNull String getName() {
        return "DC inline color";
    }

    @Override
    public @NotNull NoSettings createSettings() {
        return new NoSettings();
    }

    @Override
    public @NotNull ImmediateConfigurable createConfigurable(@NotNull NoSettings settings) {
        return new ImmediateConfigurable() {
            @Override
            public @NotNull JComponent createComponent(@NotNull com.intellij.codeInsight.hints.ChangeListener listener) {
                return new JPanel(new VerticalFlowLayout());
            }
        };
    }

    @Override
    public @Nullable String getPreviewText() {
        return "";
    }

    @Override
    public @Nullable InlayHintsCollector getCollectorFor(
        @NotNull PsiFile file,
        @NotNull Editor editor,
        @NotNull NoSettings settings,
        @NotNull InlayHintsSink sink
    ) {
        if (!file.getLanguage().isKindOf(DCLanguage.INSTANCE)) {
            return null;
        }
        return new FactoryInlayHintsCollector(editor) {
            @Override
            public boolean collect(@NotNull PsiElement element, @NotNull Editor editor, @NotNull InlayHintsSink sink) {
                if (element.getFirstChild() != null || element.getNode() == null) {
                    return true;
                }
                IElementType type = element.getNode().getElementType();
                if (type != DCTokenTypes.STRING && type != DCTokenTypes.IDENTIFIER) {
                    return true;
                }
                String text = element.getText();
                if (text == null || text.isEmpty()) {
                    return true;
                }
                Matcher matcher = COLOR_PATTERN.matcher(text);
                while (matcher.find()) {
                    Color color = parseColor(matcher.group(1));
                    if (color == null) {
                        continue;
                    }
                    int startOffset = element.getTextRange().getStartOffset() + matcher.start();
                    int endOffset = startOffset + matcher.group(0).length();
                    InlayPresentation base = getFactory().icon(new ColorChipIcon(color));
                    InlayPresentation clickable = getFactory().onClick(base, MouseButton.Left, (event, point) -> {
                        openColorPickerAndReplace(editor, file, startOffset, endOffset, color);
                        return Unit.INSTANCE;
                    });
                    InlayPresentation presentation = getFactory().inset(
                        getFactory().withTooltip("Pick color", getFactory().withCursorOnHover(clickable, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))),
                        0,
                        4,
                        2,
                        0
                    );
                    sink.addInlineElement(startOffset, true, presentation, false);
                }
                return true;
            }
        };
    }

    private void openColorPickerAndReplace(
        @NotNull Editor editor,
        @NotNull PsiFile file,
        int startOffset,
        int endOffset,
        @NotNull Color initialColor
    ) {
        if (file.getProject().isDisposed()) {
            return;
        }
        Color chosen = ColorChooser.chooseColor(
            editor.getComponent(),
            "Pick Color",
            initialColor,
            false
        );
        if (chosen == null) {
            return;
        }
        String token = toHexToken(chosen);
        WriteCommandAction.runWriteCommandAction(file.getProject(), () -> {
            int length = editor.getDocument().getTextLength();
            int safeStart = Math.max(0, Math.min(startOffset, length));
            int safeEnd = Math.max(safeStart, Math.min(endOffset, length));
            editor.getDocument().replaceString(safeStart, safeEnd, token);
        });
    }

    private Color parseColor(String hex) {
        try {
            return new Color(Integer.parseInt(hex, 16));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String toHexToken(Color color) {
        return String.format("&#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static final class ColorChipIcon implements Icon {
        private final Color color;
        private static final int ICON_SIZE = 16;
        private static final int CHIP_SIZE = 12;

        private ColorChipIcon(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int pad = (ICON_SIZE - CHIP_SIZE) / 2;
                int cx = x + pad;
                int cy = y + pad;
                g2.setColor(new Color(0, 0, 0, 90));
                g2.drawRoundRect(cx, cy, CHIP_SIZE - 1, CHIP_SIZE - 1, 4, 4);
                g2.setColor(color);
                g2.fillRoundRect(cx + 1, cy + 1, CHIP_SIZE - 2, CHIP_SIZE - 2, 3, 3);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return ICON_SIZE;
        }

        @Override
        public int getIconHeight() {
            return ICON_SIZE;
        }
    }
}
