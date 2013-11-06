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


import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.recommenders.codesearch.rcp.index.apidoc.LocalExamplesProvider;
import org.eclipse.recommenders.codesearch.rcp.index.indexer.CodeIndexer;
import org.eclipse.recommenders.injection.InjectionService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class CodesearchIndexPlugin extends AbstractUIPlugin {

    private static CodesearchIndexPlugin INSTANCE;

    public static CodesearchIndexPlugin getDefault() {
        return INSTANCE;
    }

    @Override
    public void start(final BundleContext context) throws Exception {
        super.start(context);
        INSTANCE = this;
    }

    @Override
    public void stop(final BundleContext context) throws Exception {
        super.stop(context);
        final CodeIndexer instance = InjectionService.getInstance().getInjector()
                .getInstance(CodeIndexer.class);
        instance.close();
    }

    @Override
    protected void initializeDefaultPreferences(IPreferenceStore store) {
        
       store.setDefault(PreferencePage.P_KEEP_IN_SYNC, false);
       store.setDefault(PreferencePage.P_HIGHLIGHT_SUMMARY, true);
       store.setDefault(PreferencePage.P_MAX_HITS, 1000);
       store.setDefault(PreferencePage.P_HIGHLIGHT_COLOR, 
               GetStringFromColor(Display.getDefault().getSystemColor(SWT.COLOR_YELLOW)));
       store.setDefault(PreferencePage.P_CUSTOM_LOC, 
               CodesearchIndexModule.findOrCreateIndexFolder().getPath());
       store.setDefault(LocalExamplesProvider.CHECKED_EXCEPTION_SEARCH, true);
       store.setDefault(LocalExamplesProvider.CLASS_FIELD_SEARCH, true);
       store.setDefault(LocalExamplesProvider.EXTENDED_TYPE_SEARCH, true);
       store.setDefault(LocalExamplesProvider.IMPLEENTED_TYPE_SEARCH, true);
       store.setDefault(LocalExamplesProvider.METHOD_INVOCATION_SEARCH, true);
       store.setDefault(LocalExamplesProvider.METHOD_PARAMETER_SEARCH, true);
       store.setDefault(LocalExamplesProvider.RETURN_TYPE_SEARCH, true);
       store.setDefault(LocalExamplesProvider.USED_ANNOTATION_SEARCH, true);
       store.setDefault(LocalExamplesProvider.VAR_USAGE_SEARCH, true);
       
    }
    
    private String GetStringFromColor(Color theColor) {
        String colorString = theColor.toString();

        colorString = colorString.substring(1 + colorString.indexOf("{"),
                colorString.lastIndexOf("}"));
        colorString = colorString.replaceAll(" ", "");
        return colorString;
    }
    
}
