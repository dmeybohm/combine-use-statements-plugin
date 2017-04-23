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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
                Integer startingOffset = removeUseStatements(imports, editor);

                if (startingOffset != null) {
                    StringBuilder useStatements = generateUseStatements(imports);
                    editor.getDocument().insertString(startingOffset, useStatements);
                }

            }
        }.execute();
    }

    @Nullable
    private Integer removeUseStatements(List imports, Editor editor) {
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
            modifyOffset = removeElement(modifyOffset, textRange, editor);
            if (subsequentElement instanceof PsiWhiteSpace) {
                PsiElement nextElement = subsequentElement.getNextSibling();
                if (nextElement instanceof PhpUseList) {
                    modifyOffset = removeElement(modifyOffset, subsequentElement.getTextRange(), editor);
                } else {
                    TextRange oldRange = subsequentElement.getTextRange();
                    TextRange newRange = new TextRange(oldRange.getStartOffset(), oldRange.getStartOffset() + 1);
                    modifyOffset = removeElement(modifyOffset, newRange, editor);
                }
            }
        }
        return startingOffset;
    }

    @NotNull
    private StringBuilder generateUseStatements(List imports) {
        // replace the use statements:
        StringBuilder useStatements = new StringBuilder();
        useStatements.append("use ");
        int totalUses = 0;
        for (Object useListObject : imports) {
            PhpUseList useList = (PhpUseList)useListObject;

            for (PhpUse use : useList.getDeclarations()) {
                if (totalUses > 0) {
                    useStatements.append(",\n\t");
                }
                useStatements.append(use.getFQN());
                totalUses++;
            }
        }
        useStatements.append(";\n");
        return useStatements;
    }

    private int removeElement(int modifyOffset, TextRange textRange, Editor editor) {
        editor.getDocument().deleteString(textRange.getStartOffset() - modifyOffset,
                    textRange.getEndOffset() - modifyOffset);
        return modifyOffset + textRange.getEndOffset() - textRange.getStartOffset();
    }

}
