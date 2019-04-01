package com.daveme.intellij.combineusestatements;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.*;

import java.util.*;

public class CombineUseStatementsProcessor extends WriteCommandAction.Simple {

    private static final Logger LOG = Logger.getInstance("#com.daveme.combineusestatements.CombineUseStatementsProcessor");
    private static final String COMMAND_NAME = "Combine Use Statements";

    private Project project;
    private PsiFile[] files;

    CombineUseStatementsProcessor(Project project, PsiFile... files) {
        super(project, COMMAND_NAME, files);
        this.project = project;
        this.files = files;
    }

    @Override
    protected void run() {
        Settings settings = Settings.getInstance(project);

        for (PsiFile psiFile : files) {
            PhpFile phpFile = (PhpFile)psiFile;

            UseStatementCombiner useStatementOrganizer = new UseStatementCombiner(settings, phpFile, project);
            MultiMap<String, PhpNamedElement> topLevelDefs = phpFile.getTopLevelDefs();
            for (Map.Entry<String, Collection<PhpNamedElement>> entry : topLevelDefs.entrySet()) {
                for (PhpNamedElement topLevelDef : entry.getValue()) {
                    if (topLevelDef instanceof PhpNamespace) {
                        useStatementOrganizer.combine(topLevelDef);
                    }
                }
            }
            PsiElement topLevelUseScope = phpFile.findElementAt(0);
            useStatementOrganizer.combine(topLevelUseScope);
        }
    }

}
