package com.daveme.intellij.organizephpimports;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class OrganizeImportsConfig implements SearchableConfigurable {

    private OrganizeImportsConfigUI organizeImportsConfigUI;
    private Project project;
    private boolean modified;

    OrganizeImportsConfig(@NotNull Project project) {
        this.project = project;
    }

    @NotNull
    @Override
    public String getId() {
        return "organizephpimports.OrganizeImportsConfig";
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
        if (organizeImportsConfigUI == null) {
            organizeImportsConfigUI = new OrganizeImportsConfigUI();
        }

        Settings settings = Settings.getInstance(project);

        organizeImportsConfigUI.getAddAnExtraBackslashCheckBox().setSelected(settings.addAnExtraBackslashCheckBox);
        organizeImportsConfigUI.getRemoveUnusedUseStatementsCheckBox().setSelected(settings.removeUnusedUseStatementsCheckBox);
        organizeImportsConfigUI.getSortUseStatementsCheckBox().setSelected(settings.sortUseStatementsCheckBox);
        modified = true;
        return organizeImportsConfigUI.getMyPanel();
    }

    @Override
    public boolean isModified() {
        return modified;
    }

    @Override
    public void apply() throws ConfigurationException {
        Settings settings = Settings.getInstance(project);

        settings.addAnExtraBackslashCheckBox = organizeImportsConfigUI.getAddAnExtraBackslashCheckBox().isSelected();
        settings.removeUnusedUseStatementsCheckBox = organizeImportsConfigUI.getRemoveUnusedUseStatementsCheckBox().isSelected();
        settings.sortUseStatementsCheckBox = organizeImportsConfigUI.getSortUseStatementsCheckBox().isSelected();
        modified = false;
    }

    @Override
    public void reset() {

    }

    @Override
    public void disposeUIResources() {
        organizeImportsConfigUI = null;
    }
}
