package com.daveme.intellij.combineusestatements;

import com.intellij.lang.LanguageImportStatements;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtilCore;
import com.jetbrains.php.lang.psi.PhpFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class CombineUseStatementsAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        final VirtualFile[] virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        boolean visible = false, enabled = false;

        if (virtualFiles != null) {
            final Project project = e.getData(CommonDataKeys.PROJECT);
            final PsiFile[] psiFiles = convertToPsiFiles(virtualFiles, project);
            visible = containsAtLeastOnePhpFile(psiFiles);
            enabled = visible && hasImportStatements(psiFiles);
        }
        e.getPresentation().setVisible(visible);
        e.getPresentation().setEnabled(enabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getData(CommonDataKeys.PROJECT);
        final VirtualFile[] virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        final PsiFile[] psiFiles = convertToPsiFiles(virtualFiles, project);

        new CombineUseStatementsProcessor(project, psiFiles).execute();
    }

    private static boolean hasImportStatements(final PsiFile[] files) {
        for (PsiFile file : files) {
            if (!LanguageImportStatements.INSTANCE.forFile(file).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAtLeastOnePhpFile(final PsiFile[] files) {
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
        final ArrayList<PsiFile> result = new ArrayList<>();
        for (VirtualFile virtualFile : files) {
            final PsiFile psiFile = manager.findFile(virtualFile);
            if (psiFile instanceof PhpFile) result.add(psiFile);
        }
        return PsiUtilCore.toPsiFileArray(result);
    }

}
