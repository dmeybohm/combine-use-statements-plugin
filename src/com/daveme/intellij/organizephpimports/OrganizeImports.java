package com.daveme.intellij.organizephpimports;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.php.codeInsight.PhpCodeInsightUtil;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.PhpPsiElement;
import com.jetbrains.php.lang.psi.elements.PhpUse;
import com.jetbrains.php.lang.psi.elements.PhpUseList;

import java.util.Iterator;
import java.util.List;

public class OrganizeImports extends AnAction {

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        Object psiFile = e.getData(CommonDataKeys.PSI_FILE);
        boolean enabled = psiFile instanceof PhpFile;
        e.getPresentation().setVisible(enabled);
        e.getPresentation().setEnabled(enabled);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Object psiFile = e.getData(CommonDataKeys.PSI_FILE);
        final PhpFile file = (PhpFile)psiFile;
        final Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null || psiFile == null) {
            return;
        }
        new WriteCommandAction.Simple(file.getProject(), file) {
            @Override
            protected void run() throws Throwable {
                int offset = editor.getCaretModel().getOffset();
                PsiElement element = file.findElementAt(offset);
                if (element == null) {
                    return;
                }
                PhpPsiElement scopeForUseOperator = PhpCodeInsightUtil.findScopeForUseOperator(element);
                if (scopeForUseOperator == null) {
                    return;
                }
                List imports = PhpCodeInsightUtil.collectImports(scopeForUseOperator);
                Iterator iterator = imports.iterator();

                int modifyOffset = 0;
                Integer startingOffset = null;
                while (iterator.hasNext()) {
                    PhpUseList useList = (PhpUseList)iterator.next();
                    TextRange textRange = useList.getTextRange();
                    if (startingOffset == null) {
                        startingOffset = textRange.getStartOffset();
                    }
                    // get the newline character after this use statement if there is one:
                    PsiElement subsequentElement = useList.getNextSibling();
                    modifyOffset = removeElement(modifyOffset, textRange, editor);
                    if (subsequentElement instanceof PsiWhiteSpace) {
                        modifyOffset = removeElement(modifyOffset, subsequentElement.getTextRange(), editor);
                    }
                }

                if (startingOffset != null) {
                    // replace the use statements:
                    Iterator secondIterator = imports.iterator();
                    StringBuilder useStatements = new StringBuilder();
                    useStatements.append("use ");
                    int totalUses = 0;
                    while (secondIterator.hasNext()) {
                        PhpUseList useList = (PhpUseList)secondIterator.next();
                        PhpUse[] declarations = useList.getDeclarations();

                        for (PhpUse use : declarations) {
                            if (totalUses > 0) {
                                useStatements.append(",\n\t");
                            }
                            useStatements.append("\\").append(use.getName());
                            totalUses++;
                        }
                    }
                    useStatements.append(";\n\n");
                    editor.getDocument().insertString(startingOffset, useStatements.toString());
                }

            }
        }.execute();
    }

    private int removeElement(int modifyOffset, TextRange textRange, Editor editor) {
        editor.getDocument().deleteString(textRange.getStartOffset() - modifyOffset,
                    textRange.getEndOffset() - modifyOffset);
        return modifyOffset + textRange.getEndOffset() - textRange.getStartOffset();
    }

}
