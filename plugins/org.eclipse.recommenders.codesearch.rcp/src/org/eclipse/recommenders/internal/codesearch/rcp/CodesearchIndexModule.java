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

import static org.eclipse.recommenders.utils.Checks.ensureIsTrue;
import static org.eclipse.recommenders.utils.Throws.throwUnhandledException;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.inject.Inject;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.PerFieldAnalyzerWrapper;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.recommenders.codesearch.rcp.index.CodeSearch;
import org.eclipse.recommenders.codesearch.rcp.index.Fields;
import org.eclipse.recommenders.codesearch.rcp.index.indexer.CodeIndexer;
import org.eclipse.recommenders.codesearch.rcp.index.indexer.analyzer.JavaSourceCodeAnalyzer;
import org.eclipse.recommenders.codesearch.rcp.index.indexer.analyzer.LowerCaseKeywordAnalyzer;

import com.google.common.collect.Maps;
import com.google.common.eventbus.EventBus;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class CodesearchIndexModule extends AbstractModule {

    @Override
    protected void configure() {

        configureDirectory();
        bind(Services.class).asEagerSingleton();
    }

    private void configureDirectory() {
        try {
            File folder = findOrCreateIndexFolder();           
            deleteOldLocks(folder);
            final FSDirectory directory = FSDirectory.open(folder);
            bind(Directory.class).annotatedWith(CodeSearch.class).toInstance(directory);
            // this is needed by GenericQueryProposalProvider ATM
            bind(Directory.class).toInstance(directory);
        } catch (final IOException e) {
            // this is critical!
            throwUnhandledException(e);
        }
    }

    private void deleteOldLocks(final File folder) {
        final File file = new File(folder, "write.lock");
        if (file.exists()) {
            ensureIsTrue(file.delete(), "failed to remove old write lock file");
        }
    }

    public static File findOrCreateIndexFolder() {
        final File basedir = CodesearchIndexPlugin.getDefault().getStateLocation().toFile();
        File indexdir = new File(basedir, "index_v7");
        indexdir.mkdirs();
        if(CodesearchIndexPlugin.getDefault().getPreferenceStore().getBoolean(PreferencePage.P_USE_CUSTOM_LOC)){
            File customFolder = new File(
                    CodesearchIndexPlugin.getDefault().getPreferenceStore().getString(PreferencePage.P_CUSTOM_LOC)
                    );
            if(customFolder.exists() && customFolder.canWrite()){
                indexdir = customFolder;
            }
        }
        return indexdir;
    }

    @Provides
    @Singleton
    public Version version() {
        return Version.LUCENE_35;
    }

    @Provides
    @Singleton
    public IndexWriter indexWriter(final Version version, final Analyzer analyzer, final Directory directory)
            throws IOException {
        final IndexWriterConfig config = new IndexWriterConfig(version, analyzer);
        IndexWriter.unlock(directory);
        return new IndexWriter(directory, config);
    }

    @Provides
    @Singleton
    public IndexReader indexReader(final IndexWriter writer) throws IOException {
        return IndexReader.open(writer, true);
    }

    @Provides
    @Singleton
    public IndexSearcher indexSearcher(final IndexReader reader) throws IOException {
        return new IndexSearcher(reader);
    }

    @Provides
    @Singleton
    public Analyzer analyzer() {
        final Map<String, Analyzer> analyzerPerField = Maps.newHashMap();
        analyzerPerField.put(Fields.FULL_TEXT, new JavaSourceCodeAnalyzer());
        return new PerFieldAnalyzerWrapper(new LowerCaseKeywordAnalyzer(version()), analyzerPerField);
    }

    @Provides
    public QueryParser queryParser(final Version version, final Analyzer analyzer) {
        final QueryParser parser = new QueryParser(version, Fields.FULL_TEXT, analyzer);
        parser.setLowercaseExpandedTerms(true);
        parser.setAllowLeadingWildcard(true);
        return parser;
    }

    @Provides
    @Singleton
    public IndexUpdateService indexUpdateService(final EventBus workspaceBus, final CodeIndexer indexer,
            final IWorkspaceRoot workspace) {
        final IndexUpdateService service = new IndexUpdateService(indexer, workspace);
        workspaceBus.register(service);
        return service;
    }

    private static class Services {

        @Inject
        public Services(final IndexUpdateService service, final CodeIndexer indexer, final IWorkspaceRoot workspace) {
        }
    }
}
