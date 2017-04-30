package com.daveme.intellij.organizephpimports;

import com.intellij.lang.LanguageImportStatements;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.jetbrains.php.codeInsight.PhpCodeInsightUtil;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.PhpNamespace;
import com.jetbrains.php.lang.psi.elements.PhpPsiElement;
import com.jetbrains.php.lang.psi.elements.PhpUse;
import com.jetbrains.php.lang.psi.elements.PhpUseList;
import com.jetbrains.php.lang.psi.elements.impl.PhpNamespaceImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class OrganizeImports extends AnAction {
    private static String COMMAND_NAME = "Organize PHP Imports";
    private static final Logger LOG = Logger.getInstance("#com.davemen.organizephpimports.actions.OrganizeImports");
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        Object psiFile = e.getData(CommonDataKeys.PSI_FILE);
        boolean enabled = psiFile instanceof PhpFile;
        e.getPresentation().setVisible(enabled);
        e.getPresentation().setEnabled(enabled);
    }

    private static boolean isAvailable(final PsiFile file) {
        return !LanguageImportStatements.INSTANCE.forFile(file).isEmpty();
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Editor editor = e.getData(CommonDataKeys.EDITOR);
        final Project project = e.getData(CommonDataKeys.PROJECT);
        final VirtualFile[] virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);

        LOG.debug("virtualFiles: "+virtualFiles);
        PsiFile[] psiFiles = convertToPsiFiles(virtualFiles, project);
        LOG.debug("files: "+psiFiles);
        organizeImports(project, editor, psiFiles);
    }

    private void organizeImports(final Project project, final Editor editor, final PsiFile[] files) {
        new WriteCommandAction.Simple(project, COMMAND_NAME, files) {
            @Override
            protected void run() throws Throwable {
                PhpFile file = (PhpFile)files[0];
                int offset = 0;
                PsiElement element;
                LOG.debug("Editor: "+editor);
                if (editor != null) {
                    offset = editor.getCaretModel().getOffset();
                    element = file.findElementAt(offset);
                } else {
                    element = file.findElementAt(10);
                }
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
                } else {
                    LOG.debug("starting offset is null");
                }
            }
        }.execute();
    }

    public static boolean containsAtLeastOneFile(final VirtualFile[] files) {
        if (files == null) return false;
        if (files.length < 1) return false;
        for (VirtualFile virtualFile : files) {
            if (virtualFile.isDirectory()) return false;
        }
        return true;
    }

    private static PsiFile[] convertToPsiFiles(final VirtualFile[] files,Project project) {
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
        int modifyOffset = 0;
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
