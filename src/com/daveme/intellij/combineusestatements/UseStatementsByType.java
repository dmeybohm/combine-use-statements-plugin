package com.daveme.intellij.combineusestatements;

import com.google.common.collect.ImmutableList;
import com.jetbrains.php.lang.psi.elements.PhpUseList;

import java.util.List;
import java.util.stream.Collectors;

class UseStatementsByType {

    final List<PhpUseList> constants;
    final List<PhpUseList> functions;
    final List<PhpUseList> classes;

    UseStatementsByType(List<PhpUseList> imports) {
       this.constants = ImmutableList.<PhpUseList>builder()
                .addAll(imports.stream()
                    .filter(PhpUseList::isOfConst)
                    .collect(Collectors.toList()))
                .build();

       this.functions = ImmutableList.<PhpUseList>builder()
                .addAll(imports.stream()
                    .filter(PhpUseList::isOfFunction)
                    .collect(Collectors.toList()))
                .build();

        this.classes = ImmutableList.<PhpUseList>builder()
                .addAll(imports.stream()
                    .filter(uses -> !uses.isOfConst() && !uses.isOfFunction())
                    .collect(Collectors.toList()))
                .build();
    }
}
