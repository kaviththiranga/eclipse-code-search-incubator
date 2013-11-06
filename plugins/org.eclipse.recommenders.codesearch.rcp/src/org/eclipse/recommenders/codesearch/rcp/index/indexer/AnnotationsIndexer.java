/**
 * Copyright (c) 2013 Kavith Thiranga Lokuhewage.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *    Kavith Thiranga Lokuhewage - initial implementation.
 */
package org.eclipse.recommenders.codesearch.rcp.index.indexer;

import org.apache.lucene.document.Document;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.recommenders.codesearch.rcp.index.Fields;
import org.eclipse.recommenders.codesearch.rcp.index.indexer.interfaces.IClassIndexer;
import org.eclipse.recommenders.codesearch.rcp.index.indexer.interfaces.IMethodIndexer;

import com.google.common.base.Optional;

public class AnnotationsIndexer implements IClassIndexer, IMethodIndexer {

    @Override
    public void indexType(final Document document, final TypeDeclaration type) {
        final ITypeBinding clazz = type.resolveBinding();
        if (clazz == null) {
            return;
        }

        addAnnotations(document, clazz.getAnnotations());
    }

    // TODO extend for field

    @Override
    public void indexMethod(final Document document, final MethodDeclaration method) {
        final IMethodBinding methodBinding = method.resolveBinding();
        if (methodBinding == null) {
            return;
        }

        addAnnotations(document, methodBinding.getAnnotations());
    }

    private void addAnnotations(final Document document, final IAnnotationBinding[] annotations) {
        for (final IAnnotationBinding annotation : annotations) {

            final Optional<String> opt = BindingHelper.getIdentifier(annotation.getAnnotationType());
            if (!opt.isPresent()) {
                continue;
            }
            // Annotation type i.e @Deprecated
            CodeIndexer.addFieldToDocument(document, Fields.ANNOTATIONS, opt.get());

            for (final IMemberValuePairBinding valuePairBinding : annotation.getAllMemberValuePairs()) {

                if (valuePairBinding.getValue() instanceof Object[]) {
                    for (final Object valuePairValue : (Object[]) valuePairBinding.getValue()) {
                        // Combination of annotation and value i.e
                        // @SuppressWarnings({"unchecked", "rawtypes"})
                        final String value = opt.get() + ":" + valuePairValue;
                        CodeIndexer.addFieldToDocument(document, Fields.ANNOTATIONS, value);
                    }
                }
            }
        }
    }
}
