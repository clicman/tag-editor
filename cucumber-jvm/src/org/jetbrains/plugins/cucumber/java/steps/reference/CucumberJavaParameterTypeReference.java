// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.cucumber.java.steps.reference;


import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReferenceBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.cucumber.MapParameterTypeManager;
import org.jetbrains.plugins.cucumber.java.CucumberJavaUtil;

/**
 * Provides reference from Cucumber Expression to its definition. For example, from "iso-date" in step definition:
 * <pre><code>
 *
 * @Given("today is {iso-date}")
 * public void todayIs(Date date) throws Throwable {
 *   ....
 * }
 * </code></pre>
 *
 * to expression definition:
 * <pre><code>
 *   typeRegistry.defineParameterType(new ParameterType<>(
 *           "iso-date",
 *           "\\d{4}-\\d{2}-\\d{2}",
 *           Date.class,
 *           (String s) -> new SimpleDateFormat("yyyy-mm-dd").parse(s)
 *   ));
 * </code></pre>
 */
public class CucumberJavaParameterTypeReference extends PsiReferenceBase<PsiElement> {
  public CucumberJavaParameterTypeReference(@NotNull PsiElement element, @NotNull TextRange range) {
    // Exclude { and }
    super(element, TextRange.create(range.getStartOffset() + 1, range.getEndOffset() - 1), false);
  }


  @Nullable
  @Override
  public PsiElement resolve() {
    String parameterTypeName = getParameterTypeName();
    final Module module = ModuleUtilCore.findModuleForPsiElement(myElement);
    if (module != null) {
      MapParameterTypeManager manager = CucumberJavaUtil.getAllParameterTypes(module);

      PsiElement declaration = manager.getParameterTypeDeclaration(parameterTypeName);
      if (declaration != null) {
        return PomService.convertToPsi(new CucumberJavaParameterPomTarget(declaration, parameterTypeName));
      }
    }
    return null;
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return getParameterTypeName();
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    if (!(element instanceof PsiNamedElement) || !(element instanceof PomTargetPsiElement)) {
      return false;
    }
    PomTarget pomTarget = ((PomTargetPsiElement)element).getTarget();
    if (!(pomTarget instanceof CucumberJavaParameterPomTarget)) {
      return false;
    }
    String parameterTypeName = getParameterTypeName();
    if (!StringUtil.equals(((PsiNamedElement)element).getName(), parameterTypeName)) {
      return false;
    }
    PsiElement resolved = resolve();
    return resolved != null && resolved.equals(element);
  }

  @NotNull
  private String getParameterTypeName() {
    return getRangeInElement().substring(myElement.getText());
  }
}
