package com.daveme.intellij.organizephpimports;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nullable;

@State(
        name = "OrganizePHPImportsSettings",
        storages = {
                @Storage(file = "OrganizePHPImports.xml")
        }
)
public class Settings implements PersistentStateComponent<Settings> {

    public boolean addAnExtraBackslash;
    public boolean removeUnusedUseStatements;
    public boolean sortUseStatements;

    @Nullable
    @Override
    public Settings getState() {
        return this;
    }

    @Override
    public void loadState(Settings settings) {
        XmlSerializerUtil.copyBean(settings, this);
    }

    public static Settings getInstance(Project project) {
        return ServiceManager.getService(project, Settings.class);
    }
}
