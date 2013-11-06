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

import static java.lang.String.format;

import java.util.List;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.recommenders.codesearch.rcp.index.Fields;
import org.eclipse.recommenders.codesearch.rcp.index.searcher.SearchResult;
import org.eclipse.recommenders.internal.apidocs.rcp.ApidocsViewUtils;
import org.eclipse.recommenders.internal.codesearch.rcp.CodesearchIndexPlugin;
import org.eclipse.recommenders.rcp.JavaElementResolver;
import org.eclipse.recommenders.rcp.utils.Logs;
import org.eclipse.recommenders.rcp.utils.Selections;
import org.eclipse.recommenders.utils.names.Names;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.internal.misc.StatusUtil;
import org.eclipse.ui.texteditor.ITextEditor;

import com.google.common.base.Optional;

@SuppressWarnings("restriction")
public final class Renderer implements Runnable {

    private final JavaElementResolver jdtResolver;

    private final SearchResult searchResults;
    private final Composite parent;
    private final String typeName;
    private final String searchDuration;
    private final List<String> searchterms;
    private final String searchType;

    public Renderer(final SearchResult searchResult, final Composite parent, final String searchType, final String typeName,
            final String searchDuration, final JavaElementResolver jdtResolver, final List<String> searchterms) {
        searchResults = searchResult;
        this.searchType = searchType;
        this.parent = parent;
        this.typeName = typeName;
        this.searchDuration = searchDuration;
        this.jdtResolver = jdtResolver;
        this.searchterms = searchterms;
    }

    @Override
    public void run()
    {
        final Composite container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout());
        container.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
        ApidocsViewUtils.setInfoBackgroundColor(container);
        final Label l = new Label(container, SWT.NONE);
        
        final String msg = format("Found %s examples for %s search of '%s'. Search took %s.", searchResults.docs.totalHits, searchType,
                Names.vm2srcSimpleTypeName(typeName), searchDuration);
        l.setText(msg);
       
        final TableViewer v = new TableViewer(container, SWT.VIRTUAL);
        ColumnViewerToolTipSupport.enableFor(v, ToolTip.RECREATE);
        
        v.setLabelProvider(new LabelProvider(jdtResolver, searchterms, searchType, searchResults));
        v.setContentProvider(new ContentProvider(searchResults,  Names.vm2srcSimpleTypeName(typeName), searchType, jdtResolver));
        // v.setUseHashlookup(true);
        v.setInput(searchResults);
        // v.getTable().setLinesVisible(true);
        v.setItemCount(searchResults.scoreDocs().length);
        v.getControl().setLayoutData(GridDataFactory.fillDefaults().hint(300, 200).grab(true, false).create());
        
        v.addDoubleClickListener(new IDoubleClickListener() {        
        
            @Override
            public void doubleClick(final DoubleClickEvent event)
            {
                final Optional<Selection> opt = Selections.getFirstSelected(event.getSelection());
                final ITextEditor editor;
                
                if (opt.isPresent())
                {
                    final Selection s = opt.get();
                    if (s.isError()) {
                        ErrorDialog.openError(event.getViewer().getControl().getShell(), "Index issue",
                                "could not open indexed file.",
                                StatusUtil.newStatus("org.eclipse.recommenders", s.exception));
                        return;
                    }
                    
                    final String handle = s.doc.get(Fields.JAVA_ELEMENT_HANDLE);
                    final IJavaElement create = JavaCore.create(handle);
                    
                    try {
                        editor = (ITextEditor) JavaUI.openInEditor(create);
                        JavaUI.revealInEditor(editor, create);
                        //editor.getDocumentProvider().
                        
                    } catch (final Exception e) {
                        Logs.logError(e, CodesearchIndexPlugin.getDefault(),
                                "Failed to open method declaration in editor");
                    }
                    
                }
            }
        });

    }
}
