package com.daveme.intellij.organizephpimports;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.php.codeInsight.PhpCodeInsightUtil;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.elements.impl.PhpNamespaceImpl;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class OrganizeImportsProcessor extends WriteCommandAction.Simple {

    private static final Logger LOG = Logger.getInstance("#com.daveme.organizephpimports.OrganizeImportsProcessor");
    private static final String COMMAND_NAME = "Organize PHP Imports";

    private int modifyOffset;
    private int startingOffset;
    private Settings settings;

    private Project project;
    private PsiFile[] files;

    public OrganizeImportsProcessor(Project project, PsiFile... files) {
        super(project, COMMAND_NAME, files);
        this.project = project;
        this.files = files;
    }

    @Override
    protected void run() throws Throwable {
        settings = Settings.getInstance(project);
        for (PsiFile psiFile : files) {
            PhpFile phpFile = (PhpFile)psiFile;
            modifyOffset = 0;
            startingOffset = 0;

            MultiMap<String, PhpNamedElement> topLevelDefs = phpFile.getTopLevelDefs();
            for (Map.Entry<String, Collection<PhpNamedElement>> entry : topLevelDefs.entrySet()) {
                for (PhpNamedElement topLevelDef : entry.getValue()) {
                    LOG.debug("topLevelDef: "+topLevelDef);
                    if (topLevelDef instanceof PhpNamespace) {
                        organizeUseStatementsFromScope(phpFile, topLevelDef, project);
                    }
                }
            }
            PsiElement topLevelUseScope = phpFile.findElementAt(0);
            organizeUseStatementsFromScope(phpFile, topLevelUseScope, project);
        }
    }

    private void organizeUseStatementsFromScope(PhpFile file, PsiElement element, Project project) {
        if (element == null) {
            LOG.debug("element null");
            return;
        }
        PhpPsiElement scopeForUseOperator = PhpCodeInsightUtil.findScopeForUseOperator(element);
        if (scopeForUseOperator == null) {
            LOG.debug("scopeForUseOperator null");
            return;
        }

        LOG.debug("scopeForUseOperator: "+scopeForUseOperator);
        List imports = PhpCodeInsightUtil.collectImports(scopeForUseOperator);
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null) {
            LOG.debug("Null document");
            return;
        }
        LOG.debug("path:"+file.getVirtualFile().getCanonicalPath());
        LOG.debug("Imports length: "+imports.size());

        Integer offsetOfFirstUseStatement = removeUseStatements(imports, document);
        PsiElement namespaceParent = PsiTreeUtil.findFirstParent(element, PhpNamespace.INSTANCEOF);
        boolean indentExtraLevel = false;
        if (namespaceParent != null) {
            PhpNamespaceImpl parent = (PhpNamespaceImpl)namespaceParent;
            indentExtraLevel = parent.isBraced();
        }
        if (offsetOfFirstUseStatement != null) {
            List<PhpUseList> classList = splitUseStatements(imports, true, false, false);
            List<PhpUseList> constList = splitUseStatements(imports, false, true, false);
            List<PhpUseList> functionList = splitUseStatements(imports, false, false, true);

            StringBuilder useStatements = new StringBuilder();
            boolean generated;
            generated = generateUseStatements(classList, useStatements, null, indentExtraLevel, false);
            generated = generateUseStatements(constList, useStatements, "const", indentExtraLevel, generated);
            generateUseStatements(functionList, useStatements, "function", indentExtraLevel, generated);
            document.insertString(offsetOfFirstUseStatement - startingOffset, useStatements);
            modifyOffset -= useStatements.length();
            startingOffset = modifyOffset;
        }
        else {
            LOG.debug("starting offset is null");
        }
    }

    private List<PhpUseList> splitUseStatements(
        List imports,
        boolean extractClasses,
        boolean extractConst,
        boolean extractFunctions
    ) {
        ArrayList<PhpUseList> result = new ArrayList<>();
        for (Object useListObject : imports) {
            PhpUseList useList = (PhpUseList) useListObject;
            if (extractConst && useList.isOfConst()) {
                result.add(useList);
            }
            else if (extractFunctions && useList.isOfFunction()) {
                result.add(useList);
            }
            else if (extractClasses && (!useList.isOfFunction() && !useList.isOfConst())) {
                result.add(useList);
            }
        }

        return result;
    }

    @Nullable
    private Integer removeUseStatements(List imports, Document document) {
        Integer startingOffset = null;
        for (Object useListObject : imports) {
            PhpUseList useList = (PhpUseList)useListObject;
            TextRange textRange = useList.getTextRange();
            if (startingOffset == null) {
                startingOffset = textRange.getStartOffset();
            }
            // get the newline character after this use statement if there is one:
            PsiElement subsequentElement = useList.getNextSibling();
            modifyOffset = removeRange(modifyOffset, textRange, document);
            if (subsequentElement instanceof PsiWhiteSpace) {
                PsiElement nextElement = subsequentElement.getNextSibling();
                if (nextElement instanceof PhpUseList) {
                    modifyOffset = removeRange(modifyOffset, subsequentElement.getTextRange(), document);
                }
                else {
                    modifyOffset = removeUpToNextNewLine(modifyOffset, subsequentElement.getTextRange(), document);
                }
            }
        }
        return startingOffset;
    }

    private boolean generateUseStatements(
        List imports,
        StringBuilder useStatements,
        String extra,
        boolean indentExtraLevel,
        boolean generated
    ) {
        ArrayList<PhpUse> uses = new ArrayList<>();
        if (imports.size() == 0) {
            return false;
        }
        if (generated && indentExtraLevel) {
            useStatements.append("\t");
        }
        useStatements.append("use ");
        if (extra != null) {
            useStatements.append(extra);
            useStatements.append(" ");
        }

        for (Object useListObject : imports) {
            PhpUseList useList = (PhpUseList)useListObject;
            PhpUse[] declarations = useList.getDeclarations();
            if (declarations == null) {
                continue;
            }
            Collections.addAll(uses, declarations);
        }
        if (settings.sortUseStatements) {
            Collections.sort(uses, (useOne, useTwo) -> useOne.getFQN().compareTo(useTwo.getFQN()));
        }
        int totalUses = 0;
        for (PhpUse use : uses) {
            if (totalUses > 0) {
                useStatements.append(",\n\t");
                if (indentExtraLevel) {
                    useStatements.append("\t");
                }
            }
            String fqn = use.getFQN();
            if (!settings.addAnExtraBackslash) {
                fqn = fqn.substring(1);
            }
            useStatements.append(fqn);
            String aliasName = use.getAliasName();
            if (aliasName != null) {
                useStatements.append(" as ");
                useStatements.append(aliasName);
            }
            totalUses++;
        }
        useStatements.append(";\n");
        return true;
    }

    private int removeRange(int modifyOffset, TextRange textRange, Document document) {
        document.deleteString(textRange.getStartOffset() - modifyOffset,
                textRange.getEndOffset() - modifyOffset);
        return modifyOffset + textRange.getEndOffset() - textRange.getStartOffset();
    }

    private int removeUpToNextNewLine(int modifyOffset, TextRange textRange, Document document) {
        TextRange modifiedRange = new TextRange(textRange.getStartOffset() - modifyOffset,
                textRange.getEndOffset() - modifyOffset);
        String text = document.getText(modifiedRange);
        char[] chars = text.toCharArray();
        int textLength = 0;
        for (char aChar : chars) {
            textLength += 1;
            if (aChar == '\n') {
                break;
            }
        }
        TextRange newRange = new TextRange(textRange.getStartOffset(), textRange.getStartOffset() + textLength);
        return removeRange(modifyOffset, newRange, document);
    }

}
