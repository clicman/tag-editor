package ru.sbtqa.tag.cucumber.java.steps;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JVMElementFactories;
import com.intellij.psi.JVMElementFactory;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.util.CreateClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import cucumber.runtime.snippets.CamelCaseConcatenator;
import cucumber.runtime.snippets.FunctionNameGenerator;
import cucumber.runtime.snippets.SnippetGenerator;
import gherkin.formatter.model.Step;
import io.cucumber.cucumberexpressions.CucumberExpressionGenerator;
import io.cucumber.cucumberexpressions.GeneratedExpression;
import io.cucumber.cucumberexpressions.ParameterTypeRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import ru.sbtqa.tag.cucumber.AbstractStepDefinitionCreator;
import ru.sbtqa.tag.cucumber.java.CucumberJavaUtil;
import ru.sbtqa.tag.cucumber.psi.GherkinStep;

import static ru.sbtqa.tag.cucumber.java.CucumberJavaUtil.getCucumberStepAnnotation;

public class JavaStepDefinitionCreator extends AbstractStepDefinitionCreator {

    private static final String STEP_DEFINITION_SUFFIX = "MyStepdefs";
    private static final String FILE_TEMPLATE_CUCUMBER_JAVA_STEP_DEFINITION_JAVA = "Cucumber Java Step Definition.java";
    private static final String DEFAULT_STEP_KEYWORD = "Given";

    private final static Logger LOG = Logger.getInstance("#ru.sbtqa.tag.cucumber.java.steps.JavaStepDefinitionCreator");

    static String escapeStepDefinition(@NotNull String stepDefinition, @NotNull PsiElement context) {
        return stepDefinition.replace("PendingException", CucumberJavaUtil.getCucumberPendingExceptionFqn(context))
                .replaceAll("\\\\\\\\", "\\\\")
                .replaceAll("\\\\d", "\\\\\\\\d")
                .replaceAll(" {4}", "\n    ");
    }

    private static PsiMethod buildStepDefinitionByStep(@NotNull final GherkinStep step, Language language) {
        String annotationPackage = new AnnotationPackageProvider().getAnnotationPackageFor(step);
        String methodAnnotation = String.format("@%s.", annotationPackage);

        final Step cucumberStep = new Step(new ArrayList<>(), step.getKeyword().getText(), step.getStepName(), 0, null, null);
        final SnippetGenerator generator = new SnippetGenerator(new JavaSnippet());

        String snippet = generator.getSnippet(cucumberStep, new FunctionNameGenerator(new CamelCaseConcatenator()));

        if (CucumberJavaUtil.isCucumberExpressionsAvailable(step)) {
            snippet = replaceRegexpWithCucumberExpression(snippet, step.getStepName());
        }

        snippet = escapeStepDefinition(snippet, step).replaceFirst("@", methodAnnotation);
        // Change * to And cuz asterisk is not valid annotation name
        snippet = snippet.replaceFirst("\\*", "And");
        JVMElementFactory factory = JVMElementFactories.requireFactory(language, step.getProject());
        PsiMethod methodFromCucumberLibraryTemplate = factory.createMethodFromText(snippet, step);

        try {
            return createStepDefinitionFromSnippet(methodFromCucumberLibraryTemplate, step, factory);
        } catch (Exception e) {
            return methodFromCucumberLibraryTemplate;
        }
    }

    private static String replaceRegexpWithCucumberExpression(@NotNull String snippet, @NotNull String step) {
        try {
            ParameterTypeRegistry registry = new ParameterTypeRegistry(Locale.getDefault());
            CucumberExpressionGenerator generator = new CucumberExpressionGenerator(registry);
            GeneratedExpression result = generator.generateExpression(step);
            if (result != null) {
                String cucumberExpression = new JavaSnippet().escapePattern(result.getSource());
                String[] lines = snippet.split("\n");

                int start = lines[0].indexOf('(') + 1;
                lines[0] = lines[0].substring(0, start + 1) + cucumberExpression + "\")";
                return StringUtil.join(lines, "");
            }
        } catch (Exception ignored) {
            LOG.warn("Failed to replace regex with Cucumber Expression for step: " + step);
        }
        return snippet;
    }

    private static PsiMethod createStepDefinitionFromSnippet(@NotNull PsiMethod methodFromSnippet, @NotNull GherkinStep step,
                                                             @NotNull JVMElementFactory factory) {
        PsiAnnotation cucumberStepAnnotation = getCucumberStepAnnotation(methodFromSnippet);
        String regexp = CucumberJavaUtil.getPatternFromStepDefinition(cucumberStepAnnotation);
        String stepAnnotationName = cucumberStepAnnotation.getQualifiedName();
        if (stepAnnotationName == null) {
            stepAnnotationName = DEFAULT_STEP_KEYWORD;
        }

        FileTemplateDescriptor fileTemplateDescriptor = new FileTemplateDescriptor(FILE_TEMPLATE_CUCUMBER_JAVA_STEP_DEFINITION_JAVA);
        FileTemplate fileTemplate = FileTemplateManager.getInstance(step.getProject()).getCodeTemplate(fileTemplateDescriptor.getFileName());
        String text = fileTemplate.getText();
        text = text.replace("${STEP_KEYWORD}", stepAnnotationName).replace("${STEP_REGEXP}", "\"" + regexp + "\"")
                .replace("${METHOD_NAME}", methodFromSnippet.getName())
                .replace("${PARAMETERS}", methodFromSnippet.getParameterList().getText()).replace("${BODY}\n", "");

        text = escapeStepDefinition(text, step);

        return factory.createMethodFromText(text, step);
    }

    @NotNull
    @Override
    public PsiFile createStepDefinitionContainer(@NotNull PsiDirectory dir, @NotNull String name) {
        PsiClass newClass = CreateClassUtil.createClassNamed(name, CreateClassUtil.DEFAULT_CLASS_TEMPLATE, dir);
        assert newClass != null;
        return newClass.getContainingFile();
    }

    @Override
    public boolean createStepDefinition(@NotNull GherkinStep step, @NotNull PsiFile file) {
        if (!(file instanceof PsiClassOwner)) return false;

        final Project project = file.getProject();
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        assert editor != null;

        closeActiveTemplateBuilders(file);

        final PsiClass clazz = PsiTreeUtil.getChildOfType(file, PsiClass.class);
        if (clazz != null) {
            PsiDocumentManager.getInstance(project).commitAllDocuments();
            // snippet text
            final PsiMethod element = buildStepDefinitionByStep(step, file.getLanguage());
            PsiMethod addedElement = (PsiMethod) clazz.add(element);
            addedElement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(addedElement);
            JavaCodeStyleManager.getInstance(project).shortenClassReferences(addedElement);
            editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            assert editor != null;

            final PsiParameterList blockVars = addedElement.getParameterList();
            final PsiCodeBlock body = addedElement.getBody();
            final PsiAnnotation annotation = addedElement.getModifierList().getAnnotations()[0];
            final PsiElement regexpElement = annotation.getParameterList().getAttributes()[0];

            runTemplateBuilderOnAddedStep(editor, addedElement, regexpElement, blockVars, body);
        }

        return true;
    }

    void runTemplateBuilderOnAddedStep(@NotNull Editor editor,
                                       @NotNull PsiElement addedElement,
                                       PsiElement regexpElement,
                                       PsiParameterList blockVars,
                                       PsiCodeBlock body) {
        Project project = regexpElement.getProject();
        final TemplateBuilderImpl builder = (TemplateBuilderImpl) TemplateBuilderFactory.getInstance().createTemplateBuilder(addedElement);

        final TextRange range = new TextRange(1, regexpElement.getTextLength() - 1);
        builder.replaceElement(regexpElement, range, regexpElement.getText().substring(range.getStartOffset(), range.getEndOffset()));

        for (PsiParameter var : blockVars.getParameters()) {
            final PsiElement nameIdentifier = var.getNameIdentifier();
            if (nameIdentifier != null) {
                builder.replaceElement(nameIdentifier, nameIdentifier.getText());
            }
        }

        if (body.getStatements().length > 0) {
            final PsiElement firstStatement = body.getStatements()[0];
            final TextRange pendingRange = new TextRange(0, firstStatement.getTextLength() - 1);
            builder.replaceElement(firstStatement, pendingRange,
                    firstStatement.getText().substring(pendingRange.getStartOffset(), pendingRange.getEndOffset()));
        }

        final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
        documentManager.doPostponedOperationsAndUnblockDocument(editor.getDocument());

        Template template = builder.buildInlineTemplate();

        editor.getCaretModel().moveToOffset(addedElement.getTextRange().getStartOffset());
        TemplateEditingAdapter adapter = new TemplateEditingAdapter() {
            @Override
            public void templateFinished(@NotNull Template template, boolean brokenOff) {
                ApplicationManager.getApplication().runWriteAction(() -> {
                    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
                    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
                    if (psiFile == null) {
                        return;
                    }
                    int offset = editor.getCaretModel().getOffset() - 1;
                    PsiCodeBlock codeBlock = null;
                    PsiLambdaExpression lambda = PsiTreeUtil.findElementOfClassAtOffset(psiFile, offset, PsiLambdaExpression.class, false);
                    if (lambda != null) {
                        PsiElement body = lambda.getBody();
                        codeBlock = body instanceof PsiCodeBlock ? (PsiCodeBlock) body : null;
                    }
                    if (codeBlock == null) {
                        PsiMethod method = PsiTreeUtil.findElementOfClassAtOffset(psiFile, offset, PsiMethod.class, false);
                        if (method != null) {
                            codeBlock = method.getBody();
                        }
                    }

                    if (codeBlock != null) {
                        CreateFromUsageUtils.setupEditor(codeBlock, editor);
                    }
                });
            }
        };

        TemplateManager.getInstance(project).startTemplate(editor, template, adapter);
    }

    @Override
    public boolean validateNewStepDefinitionFileName(@NotNull final Project project, @NotNull final String name) {
        if (name.length() == 0) return false;
        if (!Character.isJavaIdentifierStart(name.charAt(0))) return false;
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) return false;
        }
        return true;
    }

    @NotNull
    @Override
    public PsiDirectory getDefaultStepDefinitionFolder(@NotNull final GherkinStep step) {
        PsiFile featureFile = step.getContainingFile();
        if (featureFile != null) {
            PsiDirectory psiDirectory = featureFile.getContainingDirectory();
            final Project project = step.getProject();
            if (psiDirectory != null) {
                ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
                VirtualFile directory = psiDirectory.getVirtualFile();
                if (projectFileIndex.isInContent(directory)) {
                    VirtualFile sourceRoot = projectFileIndex.getSourceRootForFile(directory);
                    final Module module = projectFileIndex.getModuleForFile(featureFile.getVirtualFile());
                    if (module != null) {
                        final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(module).getSourceRoots();
                        if (sourceRoot != null && sourceRoot.getName().equals("resources")) {
                            final VirtualFile resourceParent = sourceRoot.getParent();
                            for (VirtualFile vFile : sourceRoots) {
                                if (vFile.getPath().startsWith(resourceParent.getPath()) && vFile.getName().equals("java")) {
                                    sourceRoot = vFile;
                                    break;
                                }
                            }
                        } else {
                            if (sourceRoots.length > 0) {
                                sourceRoot = sourceRoots[sourceRoots.length - 1];
                            }
                        }
                    }
                    String packageName = "";
                    if (sourceRoot != null) {
                        packageName = CucumberJavaUtil.getPackageOfStepDef(step);
                    }

                    final String packagePath = packageName.replace('.', '/');
                    final String path = sourceRoot != null ? sourceRoot.getPath() : directory.getPath();
                    // ToDo: I shouldn't create directories, only create VirtualFile object.
                    final Ref<PsiDirectory> resultRef = new Ref<>();
                    try {
                        WriteAction.runAndWait(() -> {
                            final VirtualFile packageFile = VfsUtil.createDirectoryIfMissing(path + '/' + packagePath);
                            if (packageFile != null) {
                                resultRef.set(PsiDirectoryFactory.getInstance(project).createDirectory(packageFile));
                            }
                        });
                    } catch (IOException ignored) {

                    }
                    return resultRef.get();
                }
            }
        }

        assert featureFile != null;
        return Objects.requireNonNull(featureFile.getParent());
    }

    @NotNull
    @Override
    public String getStepDefinitionFilePath(@NotNull final PsiFile file) {
        final VirtualFile vFile = file.getVirtualFile();
        if (file instanceof PsiClassOwner && vFile != null) {
            String packageName = ((PsiClassOwner) file).getPackageName();
            if (StringUtil.isEmptyOrSpaces(packageName)) {
                return vFile.getNameWithoutExtension();
            } else {
                return vFile.getNameWithoutExtension() + " (" + packageName + ")";
            }
        }
        return file.getName();
    }

    @NotNull
    @Override
    public String getDefaultStepFileName(@NotNull final GherkinStep step) {
        return STEP_DEFINITION_SUFFIX;
    }
}

