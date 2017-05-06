package com.daveme.intellij.organizephpimports;

import javax.swing.*;

public class OrganizeImportsConfigUI {
    private JCheckBox addAnExtraBackslashCheckBox;
    private JPanel myPanel;
    private JCheckBox removeUnusedUseStatementsCheckBox;
    private JCheckBox sortUseStatementsCheckBox;

    public JPanel getMyPanel() {
        return myPanel;
    }

    public JCheckBox getAddAnExtraBackslashCheckBox() {
        return addAnExtraBackslashCheckBox;
    }

    public JCheckBox getRemoveUnusedUseStatementsCheckBox() {
        return removeUnusedUseStatementsCheckBox;
    }

    public JCheckBox getSortUseStatementsCheckBox() {
        return sortUseStatementsCheckBox;
    }
}
