package com.daveme.intellij.organizephpimports;

import com.intellij.lang.LanguageImportStatements;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.php.codeInsight.PhpCodeInsightUtil;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.elements.impl.PhpNamespaceImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class OrganizeImports extends AnAction {
    private static String COMMAND_NAME = "Organize PHP Imports";
    private static final Logger LOG = Logger.getInstance("#com.davemen.organizephpimports.actions.OrganizeImports");
    private int modifyOffset;

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        final VirtualFile[] virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        final Project project = e.getData(CommonDataKeys.PROJECT);
        final PsiFile[] psiFiles = convertToPsiFiles(virtualFiles, project);

        boolean visible = containsAtLeastOnePhpFile(psiFiles);
        boolean enabled = visible && hasImportStatements(psiFiles, project);
        e.getPresentation().setVisible(visible);
        e.getPresentation().setEnabled(enabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getData(CommonDataKeys.PROJECT);
        final VirtualFile[] virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);

        PsiFile[] psiFiles = convertToPsiFiles(virtualFiles, project);
        organizeImports(project, psiFiles);
    }

    private static boolean hasImportStatements(final PsiFile[] files, final Project project) {
        // @todo account for multiple projects?
        for (PsiFile file : files) {
            if (!LanguageImportStatements.INSTANCE.forFile(file).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void organizeImports(final Project project, final PsiFile[] files) {
        new WriteCommandAction.Simple(project, COMMAND_NAME, files) {
            // @todo use this instead for thread safety
            protected int modifyOffset;

            @Override
            protected void run() throws Throwable {
                for (PsiFile psiFile : files) {
                    PhpFile phpFile = (PhpFile)psiFile;

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
        }.execute();
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
        Integer startingOffset = removeUseStatements(imports, document);
        PsiElement namespaceParent = PsiTreeUtil.findFirstParent(element, PhpNamespace.INSTANCEOF);
        boolean indentExtraLevel = false;
        if (namespaceParent != null) {
            PhpNamespaceImpl parent = (PhpNamespaceImpl)namespaceParent;
            indentExtraLevel = parent.isBraced();
        }
        if (startingOffset != null) {
            List<PhpUseList> classList = splitUseStatements(imports, true, false, false);
            List<PhpUseList> constList = splitUseStatements(imports, false, true, false);
            List<PhpUseList> functionList = splitUseStatements(imports, false, false, true);

            StringBuilder useStatements = new StringBuilder();
            boolean generated;
            generated = generateUseStatements(classList, useStatements, null, indentExtraLevel, false);
            generated = generateUseStatements(constList, useStatements, "const", indentExtraLevel, generated);
            generateUseStatements(functionList, useStatements, "function", indentExtraLevel, generated);
            document.insertString(startingOffset, useStatements);
        }
        else {
            LOG.debug("starting offset is null");
        }
    }

    public static boolean containsAtLeastOnePhpFile(final PsiFile[] files) {
        if (files == null) return false;
        if (files.length < 1) return false;
        for (PsiFile file : files) {
            if (file.getVirtualFile().isDirectory()) continue;
            if (!(file instanceof PhpFile)) continue;
            return true;
        }
        return false;
    }

    private static PsiFile[] convertToPsiFiles(final VirtualFile[] files, Project project) {
        final PsiManager manager = PsiManager.getInstance(project);
        final ArrayList<PsiFile> result = new ArrayList<PsiFile>();
        for (VirtualFile virtualFile : files) {
            final PsiFile psiFile = manager.findFile(virtualFile);
            if (psiFile instanceof PhpFile) result.add(psiFile);
        }
        return PsiUtilCore.toPsiFileArray(result);
    }

    private List<PhpUseList> splitUseStatements(List imports, boolean extractClasses, boolean extractConst, boolean extractFunctions) {
        ArrayList<PhpUseList> result = new ArrayList<PhpUseList>();
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

        int totalUses = 0;
        for (Object useListObject : imports) {
            PhpUseList useList = (PhpUseList)useListObject;
            PhpUse[] declarations = useList.getDeclarations();
            if (declarations == null) {
                continue;
            }
            for (PhpUse use : declarations) {
                if (totalUses > 0) {
                    useStatements.append(",\n\t");
                    if (indentExtraLevel) {
                        useStatements.append("\t");
                    }
                }
                useStatements.append(use.getFQN());
                String aliasName = use.getAliasName();
                if (aliasName != null) {
                    useStatements.append(" as ");
                    useStatements.append(aliasName);
                }
                totalUses++;
            }
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
