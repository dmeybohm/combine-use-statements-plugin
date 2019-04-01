package com.daveme.intellij.organizephpimports;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class OrganizeImportsProcessor extends WriteCommandAction.Simple {

    private static final Logger LOG = Logger.getInstance("#com.daveme.organizephpimports.OrganizeImportsProcessor");
    private static final String COMMAND_NAME = "Organize PHP Imports";

    private Project project;
    private PsiFile[] files;

    OrganizeImportsProcessor(Project project, PsiFile... files) {
        super(project, COMMAND_NAME, files);
        this.project = project;
        this.files = files;
    }

    @Override
    protected void run() {
        Settings settings = Settings.getInstance(project);

        for (PsiFile psiFile : files) {
            PhpFile phpFile = (PhpFile)psiFile;

            UseStatementOrganizer useStatementOrganizer = new UseStatementOrganizer(settings, phpFile, project);
            MultiMap<String, PhpNamedElement> topLevelDefs = phpFile.getTopLevelDefs();
            for (Map.Entry<String, Collection<PhpNamedElement>> entry : topLevelDefs.entrySet()) {
                for (PhpNamedElement topLevelDef : entry.getValue()) {
                    LOG.debug("topLevelDef: "+topLevelDef);
                    if (topLevelDef instanceof PhpNamespace) {
                        useStatementOrganizer.organize(topLevelDef);
                    }
                }
            }
            PsiElement topLevelUseScope = phpFile.findElementAt(0);
            useStatementOrganizer.organize(topLevelUseScope);
        }
    }

}
