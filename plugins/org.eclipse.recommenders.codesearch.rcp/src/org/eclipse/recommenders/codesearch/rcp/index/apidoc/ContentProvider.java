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
package org.eclipse.recommenders.codesearch.rcp.index.apidoc;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.document.Document;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.ui.SharedASTProvider;
import org.eclipse.jface.viewers.ILazyContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.recommenders.codesearch.rcp.index.Fields;
import org.eclipse.recommenders.codesearch.rcp.index.searcher.SearchResult;
import org.eclipse.recommenders.internal.codesearch.rcp.CodesearchIndexPlugin;
import org.eclipse.recommenders.rcp.JavaElementResolver;
import org.eclipse.recommenders.rcp.utils.ASTNodeUtils;
import org.eclipse.recommenders.rcp.utils.Logs;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Table;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

final class ContentProvider implements ILazyContentProvider {

    private final ExecutorService s = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime
            .getRuntime().availableProcessors(), 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(20),

    new ThreadFactoryBuilder().setPriority(Thread.MIN_PRIORITY).build());
    public static MethodDeclaration EMPTY;
    static {
        final AST ast = AST.newAST(AST.JLS4);
        EMPTY = ast.newMethodDeclaration();
        EMPTY.setName(ast.newSimpleName("not_found"));
        EMPTY.setBody(ast.newBlock());
    }

    private TableViewer viewer;
    private final SearchResult searchResults;
    private final String searchType;
    final String searchVar;

    ContentProvider(final SearchResult searchResults, final String typeName, final String searchType, final JavaElementResolver jdtResolver) {
        this.searchResults = searchResults;
        this.searchType = searchType;
        this.searchVar = typeName;
    }

    @Override
    public void dispose() {
        s.shutdown();
    }

    @Override
    public void updateElement(final int index) {
        try {
            s.submit(new Runnable() {
                private IMethod jdtMethod;
                private MethodDeclaration astMethod;
                private IJavaElement element;

                @Override
                public void run() {
                    try {
                        final Document doc = searchResults.scoreDoc(index);
                        if (!findHandle(doc)) {
                            final IllegalStateException e = new IllegalStateException("Could not find handle "
                                    + doc.get(Fields.JAVA_ELEMENT_HANDLE));
                            updateIndex(new Selection(e), index);
                            return;
                        }
                        if(!hasEnclosingMethod())
                        {
                            
                        }
                        if (!findJdtMethod()) {
                            updateIndex(new Selection(EMPTY, "", doc), index);
                            return;
                        }
                        if (!findAstMethod()) {
                            updateIndex(new Selection(EMPTY, "", doc), index);
                            return;
                        }
                        if(searchType.equals(LocalExamplesProvider.VAR_USAGE_SEARCH))
                        {
                            updateIndex(new Selection(astMethod, doc.get(Fields.VARIABLE_NAME), doc), index);
                        }
                        else
                        {String s = searchVar;
                            updateIndex(new Selection(astMethod, searchVar, doc), index);
                        }
                        
                    } catch (final Exception e) {
                        updateIndex(new Selection(e), index);
                    }
                }

                private boolean findHandle(final Document doc) {
                    final String handle = doc.get(Fields.JAVA_ELEMENT_HANDLE);
                    element = JavaCore.create(handle);
                    return element != null;
                }

                private boolean findJdtMethod() {
                    jdtMethod = (IMethod) element.getAncestor(IJavaElement.METHOD);
                    return jdtMethod != null;
                }

                private boolean findAstMethod() {
                    try {
                        final ITypeRoot cu = jdtMethod.getTypeRoot();
                        if (cu == null) {
                            return false;
                        }
                        final CompilationUnit ast = SharedASTProvider.getAST(cu, SharedASTProvider.WAIT_YES, null);
                        if (ast == null) {
                            return false;
                        }

                        // caused NPEs:
                        // ASTNodeSearchUtil.getMethodDeclarationNode(jdtMethod,
                        // ast);
                        astMethod = ASTNodeUtils.find(ast, jdtMethod).orNull();
                        String ss = "";
                    } catch (final Exception e) {
                        Logs.logError(e, CodesearchIndexPlugin.getDefault(), "failed to find declaring method %s",
                                jdtMethod);
                    }
                    return astMethod != null;
                }
                private boolean hasEnclosingMethod(){
                    return !!((searchType.equals(LocalExamplesProvider.CLASS_FIELD_SEARCH)) 
                            || (searchType.equals(LocalExamplesProvider.EXTENDED_TYPE_SEARCH))
                            || (searchType.equals(LocalExamplesProvider.IMPLEENTED_TYPE_SEARCH))
                            //this is a Class annotation
                            || ((searchType.equals(LocalExamplesProvider.USED_ANNOTATION_SEARCH) && astMethod == ContentProvider.EMPTY))
                            
                          );
                  }
            });
            
        } catch (final RejectedExecutionException e) {
            updateIndex(new Selection(new RuntimeException(
                    "Too many rendering requests at once. Select this item again to refresh.")), index);
            // the user was just scrolling to fast...
            // to prevent ui freezes we ignore too many requests...
        }
    }
    


    private void updateIndex(final Selection s, final int index) {
        Display.getDefault().asyncExec(new Runnable() {

            @Override
            public void run() {
                final Table table = viewer.getTable();
                if (table.isDisposed()) {
                    return;
                }
                viewer.replace(s, index);
            }
        });
    }

    @Override
    public void inputChanged(final Viewer viewer, final Object oldInput, final Object newInput) {
        this.viewer = (TableViewer) viewer;
    }
}
