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
package org.eclipse.recommenders.internal.codesearch.rcp;

import static org.eclipse.recommenders.utils.Checks.cast;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.ui.SharedASTProvider;
import org.eclipse.recommenders.codesearch.rcp.index.indexer.CodeIndexer;
import org.eclipse.recommenders.codesearch.rcp.index.indexer.ResourcePathIndexer;
import org.eclipse.recommenders.codesearch.rcp.index.indexer.TimestampIndexer;
import org.eclipse.recommenders.rcp.JavaModelEvents.CompilationUnitAdded;
import org.eclipse.recommenders.rcp.JavaModelEvents.CompilationUnitRemoved;
import org.eclipse.recommenders.rcp.JavaModelEvents.CompilationUnitSaved;
import org.eclipse.recommenders.rcp.JavaModelEvents.JavaProjectOpened;
import org.eclipse.recommenders.rcp.utils.Logs;

import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

@SuppressWarnings("restriction")
public class IndexUpdateService {

    private final class IndexerJob extends Job {
        private final CodeIndexer indexer;
        private final IProject[] projects;
        private final Workspace root;

        private final ExecutorService e = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
                .setNameFormat("Recommenders::codesearch-indexer-%d").setPriority(Thread.MIN_PRIORITY).build());

        private IndexerJob(final String name, final IProject[] projects, final CodeIndexer indexer,
                final Workspace workspace) {
            super(name);
            this.indexer = indexer;
            this.projects = projects;
            root = workspace;
            setRule(MUTEX);
            schedule(30000);
        }

        @Override
        protected IStatus run(final IProgressMonitor monitor) {
            if (root.getBuildManager().isAutobuildBuildPending()) {
                schedule(5000);
                return Status.CANCEL_STATUS;
            }

            final CountDownLatch wait = new CountDownLatch(1);
            e.submit(new Runnable() {

                @Override
                public void run() {
                    try {
                        TimestampIndexer.updateCurrentTimestamp();

                        monitor.beginTask("Indexing", projects.length + 1);
                        monitor.subTask("");
                        monitor.worked(1);
                        for (final IProject p : projects) {
                            if (JavaProject.hasJavaNature(p)) {
                                if (monitor.isCanceled()) {
                                    return;
                                }
                                final ProjectIndexerRunnable r = new ProjectIndexerRunnable(JavaCore.create(p), indexer);
                                final SubProgressMonitor sub = new SubProgressMonitor(monitor, 1);
                                try {
                                    r.run(sub);
                                } catch (final Exception e) {
                                    Logs.logError(e, CodesearchIndexPlugin.getDefault(),
                                            "Exception while indexing project '%s'", p);
                                }
                            }
                        }
                    } finally {
                        wait.countDown();
                    }
                }
            });
            e.shutdown();
            try {
                wait.await();
            } catch (final InterruptedException e1) {
            }
            monitor.done();
            return Status.OK_STATUS;
        }
    }

    private static boolean backgroundIndexerActive = true;

    public static void setBackgroundIndexerActive(final boolean isActive) {
        backgroundIndexerActive = isActive;
    }

    public static final ISchedulingRule MUTEX = new ISchedulingRule() {
        @Override
        public boolean isConflicting(final ISchedulingRule rule) {
            return rule == this;
        }

        @Override
        public boolean contains(final ISchedulingRule rule) {
            return rule == this;
        }

    };

    private final CodeIndexer indexer;
    private final IWorkspaceRoot workspace;
    @Inject
    public IndexUpdateService(final CodeIndexer indexer, final IWorkspaceRoot workspace) {
        this.indexer = indexer;
        this.workspace = workspace;
        if (PreferencePage.isActive() && backgroundIndexerActive) {

            new IndexerJob("Code-search: Re-indexing workspace.", workspace.getProjects(), indexer,
                    (Workspace) workspace.getWorkspace());
        }
    }

    @Subscribe
    public void onEvent(final CompilationUnitAdded event) {
        if (PreferencePage.isActive() && backgroundIndexerActive) {
            tryIndexing(event.compilationUnit);
        }
    }

    @Subscribe
    public void onEvent(final JavaProjectOpened event) {
        if (PreferencePage.isActive() && backgroundIndexerActive) {
            final IJavaProject project = event.project;
            final IProject[] projects = { project.getProject() };
            final Workspace workspace = cast(projects[0].getWorkspace());
            final String title = "Indexing " + project.getElementName();
            new IndexerJob(title, projects, indexer, workspace);
        }
    }

    @Subscribe
    public void onEvent(final CompilationUnitSaved event) {
        if (PreferencePage.isActive() && backgroundIndexerActive) {
            tryIndexing(event.compilationUnit);
        }
    }

    private void tryIndexing(final ICompilationUnit cu) {
        try {
            TimestampIndexer.updateCurrentTimestamp();

            final File file = ResourcePathIndexer.getFile(cu);
            final CompilationUnit ast = SharedASTProvider.getAST(cu, SharedASTProvider.WAIT_YES, null);
            indexer.index(ast);
        } catch (final Exception e) {
            Logs.logError(e, CodesearchIndexPlugin.getDefault(), "Failed to index '%s'", cu.getResource().getFullPath());
        }
    }

    @Subscribe
    public void onEvent(final CompilationUnitRemoved event) {
        if (!PreferencePage.isActive() || !backgroundIndexerActive) {
            return;
        }

        try {
            if (event.compilationUnit != null) {
                final CompilationUnit ast = SharedASTProvider.getAST(event.compilationUnit, SharedASTProvider.WAIT_YES,
                        null);
                if (ast != null) {
                    indexer.delete(ast);
                }
            }
        } catch (final IOException e) {
            Logs.logError(e, CodesearchIndexPlugin.getDefault(), "error during indexing");
        }
    }
    
    public void reindexWorkspace(){
        if (!backgroundIndexerActive) {

           IndexerJob job= new IndexerJob("Code-search: Re-indexing workspace.", workspace.getProjects(), indexer,
                    (Workspace) workspace.getWorkspace());
           job.schedule(1000);
        }
    }

}
