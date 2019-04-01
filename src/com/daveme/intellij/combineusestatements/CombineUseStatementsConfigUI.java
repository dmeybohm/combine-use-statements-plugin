package com.daveme.intellij.combineusestatements;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionListener;

public class CombineUseStatementsConfigUI implements SearchableConfigurable {
    private Project project;
    private boolean modified;

    private JCheckBox addAnExtraBackslashCheckBox;
    private JPanel myPanel;
    private JCheckBox sortUseStatementsCheckBox;
    private JCheckBox sortByStatementLengthCheckBox;

    CombineUseStatementsConfigUI(@NotNull Project project) {
        this.project = project;
    }

    private void loadSettings(Settings settings) {
        addAnExtraBackslashCheckBox.setSelected(settings.addAnExtraBackslash);
        sortUseStatementsCheckBox.setSelected(settings.sortUseStatements);
        sortByStatementLengthCheckBox.setSelected(settings.sortByStatementLength);
    }

    private void saveSettings(Settings settings) {
        settings.addAnExtraBackslash = addAnExtraBackslashCheckBox.isSelected();
        settings.sortUseStatements = sortUseStatementsCheckBox.isSelected();
        settings.sortByStatementLength = sortByStatementLengthCheckBox.isSelected();
    }

    @NotNull
    @Override
    public String getId() {
        return "combineusestatements.CombineUseStatementsConfigUI";
    }

    @Nullable
    @Override
    public Runnable enableSearch(String s) {
        return null;
    }

    @Nls
    @Override
    public String getDisplayName() {
        return null;
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return null;
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        Settings settings = Settings.getInstance(project);
        loadSettings(settings);

        ActionListener modifiedListener = ae -> modified = true;
        addAnExtraBackslashCheckBox.addActionListener(modifiedListener);
        sortUseStatementsCheckBox.addActionListener(modifiedListener);

        return myPanel;
    }

    @Override
    public boolean isModified() {
        return modified;
    }

    @Override
    public void apply() {
        Settings settings = Settings.getInstance(project);
        saveSettings(settings);
        modified = false;
    }

    @Override
    public void reset() {

    }

    @Override
    public void disposeUIResources() {

    }
}

