package com.github.skrcode.springxmlbeantoannotation;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;

import java.util.ArrayList;
import java.util.List;


public class AddAnnotationAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {

        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            System.out.println("Editor not available.");
            return;
        }
        SelectionModel selectionModel = editor.getSelectionModel();
        String selectedText = selectionModel.getSelectedText();
        if (selectedText == null || selectedText.trim().isEmpty()) {
            System.out.println("No text selected.");
            return;
        }

        // Split selected text into individual XML tags
        String[] xmlFragments = selectedText.trim().split("\n\\s*\n"); // Split by blank lines
        XmlElementFactory factory = XmlElementFactory.getInstance(e.getProject());

        for (String xmlFragment: xmlFragments) {
            try {
                // Parse each fragment as an XmlTag
                XmlTag tag = factory.createTagFromText(xmlFragment);
                processTag(tag, 0, e.getProject(), e);

                WriteCommandAction.runWriteCommandAction(e.getProject(), () -> {
Document document = editor.getDocument();
                SelectionModel sModel = editor.getSelectionModel();
                int start = sModel.getSelectionStart();
                int end = sModel.getSelectionEnd();

                // Get the currently selected text
                String sText = sModel.getSelectedText();
                if (sText != null) {
                    // Build the new text with "Done !" comments before and after
                    String replacedText = "<!-- Annotated Start ! -->\n" + selectedText + "\n<!-- Annotated Stop ! -->";


                    // Replace the selected text with the new commented text
                    document.replaceString(start, end, replacedText);
                }

                // Clear the selection
                sModel.removeSelection();
                });


            } catch (Exception ex) {
                System.out.println("Failed to parse fragment as XML: " + xmlFragment);
                System.out.println("Error: " + ex.getMessage());
            }
        }
    }

    private void processTag(XmlTag tag, int depth, Project project, AnActionEvent e) {
        String indent = "  ".repeat(depth);
        if ("bean".equals(tag.getName())) {
            XmlAttribute idAttribute = tag.getAttribute("id");
            XmlAttribute classAttribute = tag.getAttribute("class");
            //if(!classAttribute.getValue().startsWith("org.springframework.samples.petclinic"))
            //throw new RuntimeException("Exception");

            List <PsiClassType> constructorArgs = new ArrayList < > ();
            PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(classAttribute.getValue(), classAttribute.getResolveScope());
            PsiElementFactory psiElementFactory = JavaPsiFacade.getElementFactory(project);

            WriteCommandAction.runWriteCommandAction(project, () -> {
            for (XmlTag subTag: tag.getSubTags()) { // iterate over children
                XmlAttribute ref = subTag.getAttribute("ref");
                XmlAttribute index = subTag.getAttribute("index");
                String name;
                if (subTag.getAttribute("name") == null || subTag.getAttribute("name").getValue() == null)
                    name = ref.getValue();
                else
                    name = subTag.getAttribute("name").getValue();
                XmlAttribute value = subTag.getAttribute("value");
                if ("property".equals(subTag.getName()) || "constructor-arg".equals(subTag.getName())) {
                    if (index != null) {
                        PsiMethod[] constructors = psiClass.getConstructors();
                        if (constructors.length == 0) return;
                        for (PsiMethod constructor: constructors) {
                            PsiModifierList modifierList = constructor.getModifierList();
                            if (modifierList.findAnnotation("org.springframework.beans.factory.annotation.Autowired") != null) continue;
                            PsiAnnotation autowiredAnnotation = psiElementFactory.createAnnotationFromText("@Autowired", constructor);
                            modifierList.addBefore(autowiredAnnotation, modifierList.getFirstChild());
                        }
                        PsiFile psiFile = psiClass.getContainingFile();
                        JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(psiClass.getProject());
                        styleManager.addImport(
                                (PsiJavaFile) psiFile,
                                psiElementFactory
                                        .createTypeByFQClassName("org.springframework.beans.factory.annotation.Autowired")
                                        .resolve()
                        );
                        continue;
                    }

                    if (ref != null) {
                        PsiField field = psiClass.findFieldByName(name, false);
                        if (field == null) continue;
                        PsiModifierList modifierList = field.getModifierList();
                        if (modifierList.findAnnotation("org.springframework.beans.factory.annotation.Autowired") != null) continue;
                        PsiAnnotation autowiredAnnotation = psiElementFactory.createAnnotationFromText("@Autowired", field);
                        modifierList.addBefore(autowiredAnnotation, modifierList.getFirstChild());
                        PsiFile psiFile = psiClass.getContainingFile();
                        JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(psiClass.getProject());
                        styleManager.addImport((PsiJavaFile) psiFile, psiElementFactory.createTypeByFQClassName("org.springframework.beans.factory.annotation.Autowired").resolve());
                    }
                    if (value != null) {
                        String val = value.getValueElement().getValue();
                        PsiField field = psiClass.findFieldByName(name, false);
                        if (field == null) continue;
                        PsiModifierList modifierList = field.getModifierList();
                        if (modifierList.findAnnotation("org.springframework.beans.factory.annotation.Value") != null) continue;
                        PsiAnnotation valueAnnotation = psiElementFactory.createAnnotationFromText("@Value(\"" + val + "\")", field);
                        modifierList.addBefore(valueAnnotation, modifierList.getFirstChild());
                        PsiFile psiFile = psiClass.getContainingFile();
                        JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(psiClass.getProject());
                        styleManager.addImport((PsiJavaFile) psiFile, psiElementFactory.createTypeByFQClassName("org.springframework.beans.factory.annotation.Value").resolve());
                    }
                }
            }

            PsiAnnotation annotation = psiElementFactory.createAnnotationFromText("@" + e.getPresentation().getText(), null);
            if (!psiClass.hasAnnotation("org.springframework.stereotype.Component") && !psiClass.hasAnnotation("org.springframework.stereotype.Repository") && !psiClass.hasAnnotation("org.springframework.stereotype.Service") && !psiClass.hasAnnotation("org.springframework.stereotype.Controller") && !psiClass.hasAnnotation("org.springframework.stereotype.RestController"))
                psiClass.getModifierList().addBefore(annotation, psiClass.getModifierList().getFirstChild());
            PsiFile psiFile = psiClass.getContainingFile();
            JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(psiClass.getProject());
            styleManager.addImport((PsiJavaFile) psiFile, psiElementFactory.createTypeByFQClassName("org.springframework.stereotype." + e.getPresentation().getText()).resolve());
            });
        } else {
            // Process other tags if needed
            System.out.println(indent + "Tag: " + tag.getName());
            // Recursively process child tags
            for (XmlTag childTag: tag.getSubTags()) {
                processTag(childTag, depth + 1, project, e);
            }
        }


    }

}