package com.daveme.intellij.combineusestatements;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
        name = "OrganizePHPImportsSettings",
        storages = {
                @Storage(file = "OrganizePHPImports.xml")
        }
)
public class Settings implements PersistentStateComponent<Settings> {

    boolean addAnExtraBackslash;
    boolean removeUnusedUseStatements;
    boolean sortUseStatements;
    boolean sortByStatementLength;

    @Nullable
    @Override
    public Settings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull Settings settings) {
        XmlSerializerUtil.copyBean(settings, this);
    }

    static Settings getInstance(Project project) {
        return ServiceManager.getService(project, Settings.class);
    }
}
