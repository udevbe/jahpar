package org.freedesktop.jahpar;


import com.google.auto.common.BasicAnnotationProcessor;
import com.google.common.collect.SetMultimap;
import org.anarres.cpp.CppReader;
import org.anarres.cpp.LexerException;
import org.anarres.cpp.Macro;
import org.anarres.cpp.Preprocessor;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.freedesktop.jahpar.api.Headers;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;
import javax.tools.Diagnostic;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HeaderParsingProcessingStep implements BasicAnnotationProcessor.ProcessingStep {

    private final ProcessingEnvironment processingEnv;

    private static class HeadersValueVisitor extends SimpleAnnotationValueVisitor8<List<? extends AnnotationValue>, Void> {
        @Override
        protected List<? extends AnnotationValue> defaultAction(final Object o,
                                                                final Void aVoid) {
            throw new AssertionError("Expected array value, instead got: " + o.toString());
        }

        @Override
        public List<? extends AnnotationValue> visitArray(final List<? extends AnnotationValue> vals,
                                                          final Void aVoid) {
            return vals;
        }
    }

    private static class HeaderAnnotationVisitor extends SimpleAnnotationValueVisitor8<AnnotationMirror, Void> {
        @Override
        protected AnnotationMirror defaultAction(final Object o,
                                                 final Void aVoid) {
            throw new AssertionError("Expected annotation value, instead got: " + o.toString());
        }

        @Override
        public AnnotationMirror visitAnnotation(final AnnotationMirror a,
                                                final Void aVoid) {
            return a;
        }
    }

    HeaderParsingProcessingStep(final ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    @Override
    public Set<? extends Class<? extends Annotation>> annotations() {
        return Collections.singleton(Headers.class);
    }

    @Override
    public Set<Element> process(final SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
        elementsByAnnotation.get(Headers.class)//get package element
                            .forEach((Element element) -> {
                                element.getAnnotationMirrors()
                                       .forEach(protocolsAnnotationMirror -> {//get package level annotations (@Headers)
                                           protocolsAnnotationMirror.getElementValues()
                                                                    .forEach((executableElement, protocolsAnnotationValue) -> {
                                                                        if (executableElement.getSimpleName()
                                                                                             .contentEquals("value")) {//get @Headers.value()
                                                                            protocolsAnnotationValue.accept(new HeadersValueVisitor(),
                                                                                                            null)
                                                                                                    .forEach(annotationValue -> {
                                                                                                        final AnnotationMirror protocolAnnotationMirror = annotationValue.accept(new HeaderAnnotationVisitor(),
                                                                                                                                                                                 null);//get @Header
                                                                                                        protocolAnnotationMirror.getElementValues();

                                                                                                        String header  = null;
                                                                                                        String lib     = null;
                                                                                                        int    version = 0;

                                                                                                        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : protocolAnnotationMirror.getElementValues()
                                                                                                                                                                                                               .entrySet()) {
                                                                                                            if (entry.getKey()
                                                                                                                     .getSimpleName()
                                                                                                                     .contentEquals("value")) {//get @Header.value()
                                                                                                                header = entry.getValue()
                                                                                                                              .getValue()
                                                                                                                              .toString();
                                                                                                            }
                                                                                                            else if (entry.getKey()
                                                                                                                          .getSimpleName()
                                                                                                                          .contentEquals("lib")) {//get @Header.lib()
                                                                                                                lib = entry.getValue()
                                                                                                                           .getValue()
                                                                                                                           .toString();
                                                                                                            }
                                                                                                            else if (entry.getKey()
                                                                                                                          .getSimpleName()
                                                                                                                          .contentEquals("version")) {//get @Header.version()
                                                                                                                version = Integer.parseInt(entry.getValue()
                                                                                                                                                .getValue()
                                                                                                                                                .toString());
                                                                                                            }
                                                                                                        }

                                                                                                        try {
                                                                                                            parse(header,
                                                                                                                  lib,
                                                                                                                  version);
                                                                                                        }
                                                                                                        catch (IOException e) {
                                                                                                            this.processingEnv.getMessager()
                                                                                                                              .printMessage(Diagnostic.Kind.ERROR,
                                                                                                                                            String.format("Failed to parse header. header=%s, lib=%s, version=%d",
                                                                                                                                                          header,
                                                                                                                                                          lib,
                                                                                                                                                          version),
                                                                                                                                            element);
                                                                                                            e.printStackTrace();
                                                                                                        }
                                                                                                    });
                                                                        }
                                                                    });
                                       });
                            });
        return Collections.emptySet();
    }

    private void parse(final String header,
                       final String lib,
                       final int version) throws IOException {
        try (Preprocessor preprocessor = new Preprocessor(new File(header))) {
            //TODO run preprocessor and generate to-be-processed header file.
            org.freedesktop.jahpar.CParser parser = new org.freedesktop.jahpar.CParser(
                    new CommonTokenStream(
                            new org.freedesktop.jahpar.CLexer(
                                    new ANTLRInputStream(new CppReader(preprocessor)))));

            // Use ANTLR to do whatever you want...
            parser.setBuildParseTree(true);

            //TODO gather function and struct (and union) definitions
            org.freedesktop.jahpar.CListener listener = new CWalker();
            ParseTreeWalker.DEFAULT.walk(listener,
                                         parser.compilationUnit());

            //TODO how to handle typedef function pointers?
            final Map<String, Macro> macros = preprocessor.getMacros();
            macros.values()
                  .forEach(macro -> {
                      if (!macro.isFunctionLike()) {
                          final String macroName = macro.getName();
                          final String macroText = macro.getText();
                          //TODO write out constant
                      }
                  });

        }
    }
}
