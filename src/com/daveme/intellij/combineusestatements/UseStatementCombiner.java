package com.daveme.intellij.combineusestatements;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.codeInsight.PhpCodeInsightUtil;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.elements.impl.PhpNamespaceImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class UseStatementCombiner {

    private static final Logger LOG = Logger.getInstance("#com.daveme.combineusestatements.UseStatementCombiner");

    private Settings settings;
    private PhpFile phpFile;
    private Project project;

    private int modifyOffset;
    private int startingOffset;

    UseStatementCombiner(Settings settings, PhpFile phpFile, Project project) {
        this.settings = settings;
        this.phpFile = phpFile;
        this.project = project;
    }

    void combine(PsiElement element) {
        if (element == null) {
            LOG.debug("element null");
            return;
        }
        PhpPsiElement scopeForUseOperator = findScopeForUseOperator(element);
        if (scopeForUseOperator == null) {
            LOG.debug("scopeForUseOperator null");
            return;
        }

        LOG.debug("scopeForUseOperator: "+scopeForUseOperator);
        List<PhpUseList> imports = collectImports(scopeForUseOperator);
        Document document = getDocument(phpFile);
        if (document == null) {
            LOG.debug("Null document");
            return;
        }
        LOG.debug("path:"+phpFile.getVirtualFile().getCanonicalPath());
        LOG.debug("Imports length: "+imports.size());

        Integer offsetOfFirstUseStatement = removeUseStatements(imports, document);
        PsiElement namespaceParent = getFirstParent(element);
        boolean indentExtraLevel = determineIndentLevel(namespaceParent);
        if (offsetOfFirstUseStatement != null) {
            modifyUseStatements(imports, document, offsetOfFirstUseStatement, indentExtraLevel);
        }
        else {
            LOG.debug("starting offset is null");
        }
    }

    private boolean determineIndentLevel(PsiElement namespaceParent) {
        boolean indentExtraLevel = false;
        if (namespaceParent != null) {
            PhpNamespaceImpl parent = (PhpNamespaceImpl)namespaceParent;
            indentExtraLevel = parent.isBraced();
        }
        return indentExtraLevel;
    }

    @Nullable
    private PsiElement getFirstParent(PsiElement element) {
        return PsiTreeUtil.findFirstParent(element, PhpNamespace.INSTANCEOF);
    }

    @Nullable
    private Document getDocument(PsiFile psiFile) {
        return PsiDocumentManager.getInstance(project).getDocument(psiFile);
    }

    @Nullable
    private PhpPsiElement findScopeForUseOperator(PsiElement element) {
        return PhpCodeInsightUtil.findScopeForUseOperator(element);
    }

    @NotNull
    private List<PhpUseList> collectImports(PhpPsiElement scopeForUseOperator) {
        return PhpCodeInsightUtil.collectImports(scopeForUseOperator);
    }

    private void modifyUseStatements(
            List<PhpUseList> imports,
            Document document,
            Integer offsetOfFirstUseStatement,
            boolean indentExtraLevel
    ) {
        UseStatementCollection collection = new UseStatementCollection(imports);

        StringBuilder useStatements = new StringBuilder();
        boolean generated = organizeUseStatements(collection.classes, useStatements, null, indentExtraLevel, false);
        generated = organizeUseStatements(collection.constants, useStatements, "const", indentExtraLevel, generated);
        organizeUseStatements(collection.functions, useStatements, "function", indentExtraLevel, generated);

        document.insertString(offsetOfFirstUseStatement - startingOffset, useStatements);
        modifyOffset -= useStatements.length();
        startingOffset = modifyOffset;
    }

    private boolean organizeUseStatements(
            List<PhpUseList> imports,
            StringBuilder useStatements,
            String extra,
            boolean indentExtraLevel,
            boolean generated
    ) {
        ArrayList<PhpUse> uses = new ArrayList<>();
        if (imports.size() == 0) {
            return false;
        }
        ArrayList<PhpUseList> newImports = new ArrayList<>(imports);
        if (settings.sortByStatementLength) {
            newImports.sort(Comparator.comparing(a -> -1 * a.getDeclarations().length));
        }
        prepareBeginningOfUse(useStatements, extra, indentExtraLevel, generated);

        collectUseStatements(newImports, uses);
        if (settings.sortUseStatements) {
            uses.sort(Comparator.comparing(PhpNamedElement::getFQN));
        }
        generateUses(useStatements, indentExtraLevel, uses);
        useStatements.append(";\n");
        return true;
    }

    private void prepareBeginningOfUse(StringBuilder useStatements, String extra, boolean indentExtraLevel, boolean generated) {
        if (generated && indentExtraLevel) {
            useStatements.append("\t");
        }
        useStatements.append("use ");
        if (extra != null) {
            useStatements.append(extra);
            useStatements.append(" ");
        }
    }

    private void generateUses(StringBuilder useStatements, boolean indentExtraLevel, ArrayList<PhpUse> uses) {
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
    }

    private void collectUseStatements(List<PhpUseList> imports, ArrayList<PhpUse> uses) {
        for (PhpUseList useList : imports) {
            PhpUse[] declarations = useList.getDeclarations();
            Collections.addAll(uses, declarations);
        }
    }

    private int removeRange(int modifyOffset, TextRange textRange, Document document) {
        document.deleteString(textRange.getStartOffset() - modifyOffset,
                textRange.getEndOffset() - modifyOffset);
        return modifyOffset + textRange.getEndOffset() - textRange.getStartOffset();
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
